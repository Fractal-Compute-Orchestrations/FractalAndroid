package AppBackend.ResourceManagement;

import android.content.Context;
import android.os.Build;
import android.util.Log;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import AppBackend.Network.networkConfig_ini;

public class DataDownloader_naf {

    private static final String TAG = "FRACTAL_DOWNLOADER";

    // ── NEW: Allows the caller to signal pause and cancel into the download thread ──
    public interface PauseController {
        boolean isPaused();
        boolean isCancelled();
    }

    public interface DownloadListener {
        void onDownloadFinished();
        void onError(String error);
        void onProgressUpdate(int percentage);
        // NEW: fired while the download is paused so the UI can show a paused state
        void onStatusMessage(String message);
    }

    // ── Original signature kept for backward compatibility ──────────────────────────
    public static void downloadFiles(Context context,
                                     String imagesFileName, String labelsFileName, String modelFileName,
                                     DownloadListener listener) {
        downloadFiles(context, imagesFileName, labelsFileName, modelFileName, listener, null);
    }

    // ── New signature accepts an optional PauseController ───────────────────────────
    public static void downloadFiles(Context context,
                                     String imagesFileName, String labelsFileName, String modelFileName,
                                     DownloadListener listener,
                                     PauseController pauseController) {
        new Thread(() -> {
            try {
                networkConfig_ini networkConfig = new networkConfig_ini();
                String baseUrl = networkConfig.getBaseUrl() + "/download/";

                String imagesUrl = baseUrl + "images?filename=" + imagesFileName;
                String labelsUrl = baseUrl + "labels?filename=" + labelsFileName;
                String modelUrl  = baseUrl + "model?filename="  + modelFileName;

                Log.d(TAG, "Starting dynamic sync from: " + baseUrl);

                // 1. Get total file sizes with ultra-fast HEAD requests
                long totalBytes = getFileSize(imagesUrl) + getFileSize(labelsUrl) + getFileSize(modelUrl);
                long[] downloadedBytes = {0};

                // 2. Download files sequentially with pause/cancel awareness
                downloadFileWithProgress(context, imagesUrl, imagesFileName, downloadedBytes, totalBytes, listener, pauseController);
                downloadFileWithProgress(context, labelsUrl, labelsFileName, downloadedBytes, totalBytes, listener, pauseController);
                downloadFileWithProgress(context, modelUrl,  modelFileName,  downloadedBytes, totalBytes, listener, pauseController);

                Log.i(TAG, "All files downloaded successfully.");
                listener.onDownloadFinished();

            } catch (CancelledDownloadException e) {
                // Clean cancellation — not an error, but we still need to unblock the latch
                Log.i(TAG, "Download cancelled by user.");
                listener.onError("CANCELLED");
            } catch (Exception e) {
                Log.e(TAG, "Sync failed: " + e.getMessage());
                listener.onError(e.getMessage());
            }
        }).start();
    }

    // ── Sentinel exception so we can distinguish cancel from real errors ─────────────
    private static class CancelledDownloadException extends Exception {
        CancelledDownloadException() { super("Gulping cancelled by user"); }
    }

    private static long getFileSize(String urlStr) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(5000);
            long size = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                    ? connection.getContentLengthLong()
                    : connection.getContentLength();
            connection.disconnect();
            return Math.max(size, 0);
        } catch (Exception e) {
            return 0;
        }
    }

    private static void downloadFileWithProgress(Context context, String urlStr, String fileName,
                                                 long[] downloadedBytes, long totalBytes,
                                                 DownloadListener listener,
                                                 PauseController pauseController)
            throws Exception {

        URL url = new URL(urlStr);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(15000);
        connection.connect();

        if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
            throw new Exception("Server Error (" + fileName + "): " + connection.getResponseCode());
        }

        File file = new File(context.getFilesDir(), fileName);
        try (InputStream input = new BufferedInputStream(connection.getInputStream());
             FileOutputStream output = new FileOutputStream(file)) {

            byte[] data = new byte[8192];
            int count;
            int lastPercent = 0;

            while ((count = input.read(data)) != -1) {

                // ── CANCEL CHECK: exit immediately before writing the next chunk ──
                if (pauseController != null && pauseController.isCancelled()) {
                    throw new CancelledDownloadException();
                }

                // ── PAUSE TRAP: spin here until the user resumes or cancels ──────
                if (pauseController != null && pauseController.isPaused()) {
                    Log.i(TAG, "Download paused at " + lastPercent + "%");
                    while (pauseController.isPaused()) {
                        if (pauseController.isCancelled()) {
                            throw new CancelledDownloadException();
                        }
                        // Notify the UI we are in a paused state
                        listener.onStatusMessage("Gulping Paused: " + lastPercent + "%");

                        Thread.sleep(300);
                    }
                    Log.i(TAG, "Download resumed at " + lastPercent + "%");
                }

                output.write(data, 0, count);
                downloadedBytes[0] += count;

                if (totalBytes > 0) {
                    int percent = (int) ((downloadedBytes[0] * 100) / totalBytes);
                    if (percent > lastPercent) {
                        lastPercent = percent;
                        listener.onProgressUpdate(percent);
                    }
                }
            }
            output.flush();
        } finally {
            connection.disconnect();
        }
    }
}