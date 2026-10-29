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

    // Node.js backend address
    private static final String API_URL =
            "http://192.168.1.245:3000/api/sensors/latest";

    // Background executor for network requests
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

    // Retrieve the latest sensor measurement
    public void getLatestSensorData(SensorCallback callback) {

        executor.execute(() -> {

            HttpURLConnection connection = null;

            try {

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

                // Check response status
                int responseCode =
                        connection.getResponseCode();

                if (responseCode != HttpURLConnection.HTTP_OK) {

                    throw new IOException(
                            "Server returned HTTP " + responseCode
                    );
                }

                // Read response body
                StringBuilder response = new StringBuilder();

                try (
                        InputStream input =
                                connection.getInputStream();

                        BufferedReader reader =
                                new BufferedReader(
                                        new InputStreamReader(
                                                input,
                                                StandardCharsets.UTF_8
                                        )
                                )
                ) {

                    String line;

                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                }

                // Parse JSON response
                JSONObject json =
                        new JSONObject(response.toString());

                // Required measurements
                double temperature =
                        json.getDouble("temperature");

                double humidity =
                        json.getDouble("humidity");

                // Optional air-quality measurement
                Integer airQuality = null;

                if (
                        json.has("airQuality") &&
                                !json.isNull("airQuality")
                ) {
                    airQuality = json.getInt("airQuality");
                }

                // Validate temperature
                if (
                        !Double.isFinite(temperature) ||
                                temperature < -50 ||
                                temperature > 125
                ) {
                    throw new IOException(
                            "Invalid temperature received"
                    );
                }

                // Validate humidity
                if (
                        !Double.isFinite(humidity) ||
                                humidity < 0 ||
                                humidity > 100
                ) {
                    throw new IOException(
                            "Invalid humidity received"
                    );
                }

                // Validate air quality when available
                if (airQuality != null) {

                    if (
                            airQuality < 0 ||
                                    airQuality > 100
                    ) {
                        throw new IOException(
                                "Invalid air-quality value received"
                        );
                    }
                }

                // Create the sensor data object
                SensorData sensorData = new SensorData(
                        temperature,
                        humidity,
                        airQuality
                );

                // Return successful result to UI thread
                mainHandler.post(() -> {
                    callback.onSuccess(sensorData);
                });

            } catch (Exception e) {

                String errorMessage = e.getMessage();

                if (errorMessage == null) {
                    errorMessage = "Unknown network error";
                }

                String finalError = errorMessage;

                mainHandler.post(() -> {
                    callback.onError(finalError);
                });

            } finally {

                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    // Shut down background operations
    public void shutdown() {
        executor.shutdownNow();
    }
}