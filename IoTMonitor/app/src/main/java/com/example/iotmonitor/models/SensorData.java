
package com.example.iotmonitor.models;

public class SensorData {

    private final double temperature;
    private final double humidity;
    private final int airQuality;

    // Constructor
    public SensorData(double temperature,
                      double humidity,
                      int airQuality) {

        this.temperature = temperature;
        this.humidity = humidity;
        this.airQuality = airQuality;
    }

    // Return temperature in Celsius
    public double getTemperature() {
        return temperature;
    }

    // Return relative humidity percentage
    public double getHumidity() {
        return humidity;
    }

    // Return simulated air quality score
    public int getAirQuality() {
        return airQuality;
    }
}
