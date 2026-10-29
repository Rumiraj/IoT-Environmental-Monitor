package com.example.iotmonitor.models;

public class SensorData {

    private final double temperature;
    private final double humidity;

    // Nullable until an air-quality sensor is integrated
    private final Integer airQuality;

    public SensorData(
            double temperature,
            double humidity,
            Integer airQuality
    ) {
        this.temperature = temperature;
        this.humidity = humidity;
        this.airQuality = airQuality;
    }

    // Temperature in Celsius
    public double getTemperature() {
        return temperature;
    }

    // Relative humidity percentage
    public double getHumidity() {
        return humidity;
    }

    // Air-quality score, or null if unavailable
    public Integer getAirQuality() {
        return airQuality;
    }

    // Check whether air-quality data exists
    public boolean hasAirQuality() {
        return airQuality != null;
    }
}