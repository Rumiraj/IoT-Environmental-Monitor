
package com.example.iotmonitor.network;

import android.os.Handler;
import android.os.Looper;

import com.example.iotmonitor.models.SensorData;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import java.net.HttpURLConnection;
import java.net.URL;

import java.nio.charset.StandardCharsets;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ApiClient {

    // REST API address
    // Replace this IP with your computer's local IP in Part 3
    private static final String API_URL =
            "http://192.168.1.100:3000/api/sensors/latest";

    // Background thread for network communication
    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    // Handler for updating the Android UI
    private final Handler mainHandler =
            new Handler(Looper.getMainLooper());

    // Callback interface
    public interface SensorCallback {

        void onSuccess(SensorData data);

        void onError(String error);
    }

    // Request the latest sensor readings
    public void getLatestSensorData(SensorCallback callback) {

        executor.execute(() -> {

            HttpURLConnection connection = null;

            try {

                // Create the HTTP connection
                URL url = new URL(API_URL);

                connection =
                        (HttpURLConnection) url.openConnection();

                // Configure HTTP request
                connection.setRequestMethod("GET");

                connection.setConnectTimeout(3000);
                connection.setReadTimeout(3000);

                connection.setRequestProperty(
                        "Accept",
                        "application/json"
                );

                // Check server response
                int responseCode =
                        connection.getResponseCode();

                if (responseCode != HttpURLConnection.HTTP_OK) {

                    throw new IOException(
                            "Server returned HTTP " + responseCode
                    );
                }

                // Read the JSON response
                StringBuilder response = new StringBuilder();

                try (InputStream input = connection.getInputStream();
                     BufferedReader reader = new BufferedReader(
                             new InputStreamReader(
                                     input,
                                     StandardCharsets.UTF_8
                             ))) {

                    String line;

                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                }

                // Convert the response to JSON
                JSONObject json =
                        new JSONObject(response.toString());

                // Extract sensor measurements
                double temperature =
                        json.getDouble("temperature");

                double humidity =
                        json.getDouble("humidity");

                int airQuality =
                        json.getInt("airQuality");

                // Validate the values
                if (!Double.isFinite(temperature)
                        || !Double.isFinite(humidity)
                        || humidity < 0
                        || humidity > 100
                        || airQuality < 0
                        || airQuality > 100) {

                    throw new IOException(
                            "Invalid sensor readings received"
                    );
                }

                // Create SensorData object
                SensorData sensorData = new SensorData(
                        temperature,
                        humidity,
                        airQuality
                );

                // Return data to the UI thread
                mainHandler.post(() -> {
                    callback.onSuccess(sensorData);
                });

            } catch (Exception e) {

                String errorMessage = e.getMessage();

                if (errorMessage == null) {
                    errorMessage = "Unknown network error";
                }

                String finalError = errorMessage;

                // Return error to the UI thread
                mainHandler.post(() -> {
                    callback.onError(finalError);
                });

            } finally {

                // Close the connection
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    // Shut down background network operations
    public void shutdown() {
        executor.shutdownNow();
    }
}
