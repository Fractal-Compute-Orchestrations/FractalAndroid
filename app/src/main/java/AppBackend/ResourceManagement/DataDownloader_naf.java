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

    public interface DownloadListener {
        void onDownloadFinished();
        void onError(String error);
        void onProgressUpdate(int percentage); // NEW: Unified progress tracker
    }

    public static void downloadFiles(Context context,
                                     String imagesFileName, String labelsFileName, String modelFileName,
                                     DownloadListener listener) {
        new Thread(() -> {
            try {
                networkConfig_ini networkConfig = new networkConfig_ini();
                String baseUrl = networkConfig.getBaseUrl() + "/download/";

                String imagesUrl = baseUrl + "images?filename=" + imagesFileName;
                String labelsUrl = baseUrl + "labels?filename=" + labelsFileName;
                String modelUrl = baseUrl + "model?filename=" + modelFileName;

                Log.d(TAG, "Starting dynamic sync from: " + baseUrl);

                // 1. Get total file sizes first using ultra-fast HEAD requests
                long totalBytes = getFileSize(imagesUrl) + getFileSize(labelsUrl) + getFileSize(modelUrl);
                long[] downloadedBytes = {0}; // Array so we can modify it inside the helper method

                // 2. Download files sequentially while tracking aggregate progress
                downloadFileWithProgress(context, imagesUrl, imagesFileName, downloadedBytes, totalBytes, listener);
                downloadFileWithProgress(context, labelsUrl, labelsFileName, downloadedBytes, totalBytes, listener);
                downloadFileWithProgress(context, modelUrl, modelFileName, downloadedBytes, totalBytes, listener);

                Log.i(TAG, "All files downloaded successfully with HTTPS routing.");
                listener.onDownloadFinished();

            } catch (Exception e) {
                Log.e(TAG, "Sync failed: " + e.getMessage());
                listener.onError(e.getMessage());
            }
        }).start();
    }

    // Sends a quick ping to the server to get the exact file size in bytes
    private static long getFileSize(String urlStr) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(5000);
            long size = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ? connection.getContentLengthLong() : connection.getContentLength();
            connection.disconnect();
            return Math.max(size, 0);
        } catch (Exception e) {
            return 0;
        }
    }

    private static void downloadFileWithProgress(Context context, String urlStr, String fileName,
                                                 long[] downloadedBytes, long totalBytes,
                                                 DownloadListener listener) throws Exception {
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
                output.write(data, 0, count);
                downloadedBytes[0] += count; // Add to master byte count

                if (totalBytes > 0) {
                    int percent = (int) ((downloadedBytes[0] * 100) / totalBytes);
                    // Only trigger the UI update if the percentage actually changed
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