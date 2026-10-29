# IoT Environmental Monitoring System

**Status:** In Development

An Android-based IoT monitoring application designed to collect, store, and visualize environmental measurements from an ESP32 microcontroller.

The project combines embedded programming, mobile application development, REST API communication, and database management.

## Technologies

- **Android Studio:** Java and XML
- **Microcontroller:** ESP32-WROOM-32D
- **Embedded framework:** ESP-IDF
- **Backend:** Node.js and Express
- **Database:** MongoDB
- **Communication:** Wi-Fi, HTTP/REST, JSON, I²C

## Hardware

- ESP32 development board
- DHT22 temperature and humidity sensor
- MikroElektronika CO2 Click (STC31)
- Breadboard and supporting components

## Architecture

The ESP32 collects environmental measurements and sends them to a Node.js REST API over Wi-Fi.

The backend stores measurements in MongoDB and provides endpoints for retrieving current and historical data.

The Android application communicates with the backend to display sensor measurements.

## Current Progress

- [x] Android dashboard interface
- [x] Simulated sensor readings
- [x] REST API communication
- [x] MongoDB integration
- [x] Android-to-server communication over Wi-Fi
- [ ] ESP32 sensor integration
- [ ] Automatic sensor data transmission
- [ ] Historical data visualization
- [ ] Final testing and documentation

## Development Environment

- Android Studio — Java/XML
- Visual Studio Code — ESP-IDF
- Node.js — Express REST API
- MongoDB — Data storage
- Linux — Development environment

## Project Goals

The objective is to demonstrate practical knowledge of Android programming, embedded systems, networking, and backend development through an integrated IoT application.

The system is being developed incrementally, with each component tested independently before full integration.