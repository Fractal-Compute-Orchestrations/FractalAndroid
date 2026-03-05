package AppBackend.ResourceManagement;

import android.content.Context;
import android.util.Log;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class DataDownloader_naf {

    private static final String TAG = "FRACTAL_DOWNLOADER";

    public interface DownloadListener {
        void onDownloadFinished();
        void onError(String error);
    }

    // NEW: Added the dynamic file names to the method signature

    public static void downloadFiles(Context context, String laptopIp, String serverPort,
                                     String imagesFileName, String labelsFileName, String modelFileName,
                                     DownloadListener listener) {
        new Thread(() -> {
            try {
                String baseUrl = "http://" + laptopIp + ":" + serverPort + "/download/";

                // FIXED: Append the dynamic file names to the server URL so it serves the right segment
                String imagesUrl = baseUrl + "images?filename=" + imagesFileName;
                String labelsUrl = baseUrl + "labels?filename=" + labelsFileName;
                String modelUrl = baseUrl + "model?filename=" + modelFileName;

                Log.d(TAG, "Starting dynamic sync. Fetching: " + imagesFileName);

                // FIXED: Save the files locally using the exact dynamic names provided by the task
                downloadFile(context, imagesUrl, imagesFileName);
                downloadFile(context, labelsUrl, labelsFileName);
                downloadFile(context, modelUrl, modelFileName);

                Log.i(TAG, "All files downloaded successfully with dynamic routing.");
                listener.onDownloadFinished();

            } catch (Exception e) {
                Log.e(TAG, "Sync failed: " + e.getMessage());
                listener.onError(e.getMessage());
            }
        }).start();
    }

    private static File downloadFile(Context context, String urlStr, String fileName) throws Exception {
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
            while ((count = input.read(data)) != -1) {
                output.write(data, 0, count);
            }
            output.flush();
        } finally {
            connection.disconnect();
        }
        return file;
    }
}