
package com.example.iotmonitor;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.iotmonitor.models.SensorData;
import com.example.iotmonitor.network.ApiClient;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

public class MainActivity extends AppCompatActivity {

    // Dashboard UI elements
    private TextView textTemperature;
    private TextView textHumidity;
    private TextView textAirQuality;
    private TextView textConnection;
    private TextView textDevice;
    private TextView textLastUpdate;

    private Button buttonRefresh;

    // Simulation settings
    // Change to false when the REST API server is running
    private static final boolean USE_SIMULATION = true;

    // Update interval: 5 seconds
    private static final long UPDATE_INTERVAL = 5000;

    private final Random random = new Random();

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    // REST API client
    private ApiClient apiClient;

    private boolean activityVisible = false;
    private boolean requestInProgress = false;

    // Automatic sensor update task
    private final Runnable updateTask = new Runnable() {

        @Override
        public void run() {

            refreshSensorData();

            handler.postDelayed(
                    this,
                    UPDATE_INTERVAL
            );
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        // Handle system bars on modern Android devices
        View root = findViewById(R.id.dashboardRoot);

        ViewCompat.setOnApplyWindowInsetsListener(
                root,
                (view, insets) -> {

                    Insets bars = insets.getInsets(
                            WindowInsetsCompat.Type.systemBars()
                    );

                    view.setPadding(
                            bars.left,
                            bars.top,
                            bars.right,
                            bars.bottom
                    );

                    return insets;
                }
        );

        // Connect Java variables to XML components
        textTemperature =
                findViewById(R.id.textTemperature);

        textHumidity =
                findViewById(R.id.textHumidity);

        textAirQuality =
                findViewById(R.id.textAirQuality);

        textConnection =
                findViewById(R.id.textConnection);

        textDevice =
                findViewById(R.id.textDevice);

        textLastUpdate =
                findViewById(R.id.textLastUpdate);

        buttonRefresh =
                findViewById(R.id.buttonRefresh);

        // Initialize the REST API client
        apiClient = new ApiClient();

        // Set the initial connection status
        if (USE_SIMULATION) {

            textConnection.setText("SIMULATION MODE");
            textDevice.setText("ESP32 - Not connected");

        } else {

            textConnection.setText("CONNECTING...");
            textDevice.setText("Waiting for server");
        }

        // Manual refresh button
        buttonRefresh.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                refreshSensorData();
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();

        activityVisible = true;

        // Start automatic updates
        handler.removeCallbacks(updateTask);
        handler.post(updateTask);
    }

    @Override
    protected void onStop() {
        super.onStop();

        activityVisible = false;

        // Stop automatic updates
        handler.removeCallbacks(updateTask);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        handler.removeCallbacks(updateTask);

        // Release network resources
        apiClient.shutdown();
    }

    // Select simulation or live REST API data
    private void refreshSensorData() {

        if (!activityVisible) {
            return;
        }

        if (USE_SIMULATION) {

            SensorData data = generateSimulatedData();

            displaySensorData(data);

        } else {

            fetchSensorDataFromApi();
        }
    }

    // Generate simulated environmental sensor readings
    private SensorData generateSimulatedData() {

        // Temperature: 20.0 to 28.0 degrees Celsius
        double temperature =
                20.0 + random.nextDouble() * 8.0;

        // Relative humidity: 35.0 to 65.0 percent
        double humidity =
                35.0 + random.nextDouble() * 30.0;

        // Simulated air-quality score: 50 to 90
        int airQuality =
                50 + random.nextInt(41);

        return new SensorData(
                temperature,
                humidity,
                airQuality
        );
    }

    // Request sensor readings from the Node.js REST API
    private void fetchSensorDataFromApi() {

        // Prevent overlapping requests
        if (requestInProgress) {
            return;
        }

        requestInProgress = true;

        apiClient.getLatestSensorData(
                new ApiClient.SensorCallback() {

                    @Override
                    public void onSuccess(SensorData data) {

                        requestInProgress = false;

                        if (!activityVisible) {
                            return;
                        }

                        // API request succeeded
                        textConnection.setText("API CONNECTED");
                        textDevice.setText("Server responding");

                        // Display received sensor values
                        displaySensorData(data);
                    }

                    @Override
                    public void onError(String error) {

                        requestInProgress = false;

                        if (!activityVisible) {
                            return;
                        }

                        // API request failed
                        textConnection.setText("API OFFLINE");
                        textDevice.setText(error);

                        textLastUpdate.setText(
                                "Unable to retrieve new sensor data"
                        );
                    }
                }
        );
    }

    // Display sensor readings on the dashboard
    private void displaySensorData(SensorData data) {

        // Temperature
        textTemperature.setText(
                String.format(
                        Locale.getDefault(),
                        "%.1f °C",
                        data.getTemperature()
                )
        );

        // Relative humidity
        textHumidity.setText(
                String.format(
                        Locale.getDefault(),
                        "%.1f %%",
                        data.getHumidity()
                )
        );

        // Air quality
        textAirQuality.setText(
                String.format(
                        Locale.getDefault(),
                        "%d / 100",
                        data.getAirQuality()
                )
        );

        // Format current time
        SimpleDateFormat timeFormat =
                new SimpleDateFormat(
                        "HH:mm:ss",
                        Locale.getDefault()
                );

        String currentTime =
                timeFormat.format(new Date());

        // Display the last successful update time
        textLastUpdate.setText(
                "Last updated: " + currentTime
        );
    }
}
