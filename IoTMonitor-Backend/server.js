
require("dotenv").config();

const express = require("express");
const { MongoClient } = require("mongodb");

const app = express();

const PORT = Number(process.env.PORT || 3000);

const MONGODB_URI =
    process.env.MONGODB_URI || "mongodb://127.0.0.1:27017";

const DB_NAME =
    process.env.DB_NAME || "iotmonitor";

const API_KEY = process.env.INGEST_API_KEY;

if (!API_KEY || API_KEY.length < 24) {
    console.error("Set a strong INGEST_API_KEY in .env");
    process.exit(1);
}

app.use(express.json({ limit: "16kb" }));

const client = new MongoClient(MONGODB_URI);

let readings;

// Convert database documents to API responses
function formatReading(document) {

    if (!document) {
        return null;
    }

    return {
        temperature: document.temperature,
        humidity: document.humidity,
        airQuality: document.airQuality,
        timestamp: document.timestamp
    };
}

// Check database connectivity
app.get("/api/health", async (req, res) => {

    try {
        await client.db("admin").command({ ping: 1 });

        res.json({
            status: "online",
            database: "connected"
        });

    } catch (error) {
        res.status(503).json({
            status: "offline",
            database: "disconnected"
        });
    }
});

// Retrieve newest sensor reading
app.get("/api/sensors/latest", async (req, res) => {

    try {
        const latest = await readings.findOne(
            {},
            { sort: { timestamp: -1 } }
        );

        if (!latest) {
            return res.status(404).json({
                error: "No sensor readings available"
            });
        }

        res.json(formatReading(latest));

    } catch (error) {
        console.error(error);

        res.status(500).json({
            error: "Failed to retrieve sensor data"
        });
    }
});

// Retrieve historical sensor readings
app.get("/api/sensors/history", async (req, res) => {

    try {
        const requestedLimit = Number(req.query.limit || 50);

        if (!Number.isInteger(requestedLimit)
                || requestedLimit < 1
                || requestedLimit > 500) {

            return res.status(400).json({
                error: "Limit must be between 1 and 500"
            });
        }

        const history = await readings
            .find({})
            .sort({ timestamp: -1 })
            .limit(requestedLimit)
            .toArray();

        res.json(history.map(formatReading));

    } catch (error) {
        console.error(error);

        res.status(500).json({
            error: "Failed to retrieve history"
        });
    }
});

// Receive a new reading from ESP32
app.post("/api/sensors", async (req, res) => {

    // Require API key for submitting readings
    if (req.headers["x-api-key"] !== API_KEY) {

        return res.status(401).json({
            error: "Unauthorized"
        });
    }

    const {
        temperature,
        humidity,
        airQuality
    } = req.body || {};

    // Validate sensor measurements
    if (
        typeof temperature !== "number" ||
        !Number.isFinite(temperature) ||
        temperature < -50 ||
        temperature > 125 ||

        typeof humidity !== "number" ||
        !Number.isFinite(humidity) ||
        humidity < 0 ||
        humidity > 100 ||

        !Number.isInteger(airQuality) ||
        airQuality < 0 ||
        airQuality > 100
    ) {

        return res.status(400).json({
            error: "Invalid sensor readings"
        });
    }

    const measurement = {
        temperature,
        humidity,
        airQuality,
        timestamp: new Date()
    };

    try {
        await readings.insertOne(measurement);

        res.status(201).json({
            message: "Reading stored successfully",
            data: formatReading(measurement)
        });

    } catch (error) {
        console.error(error);

        res.status(500).json({
            error: "Failed to store sensor reading"
        });
    }
});

// Check when the device last reported
app.get("/api/device/status", async (req, res) => {

    try {
        const latest = await readings.findOne(
            {},
            { sort: { timestamp: -1 } }
        );

        if (!latest) {
            return res.json({
                device: "ESP32",
                online: false,
                lastSeen: null
            });
        }

        const lastSeen = new Date(latest.timestamp);

        const age =
            Date.now() - lastSeen.getTime();

        // Consider the device online if it has
        // reported during the past 30 seconds.
        const online = age >= 0 && age < 30000;

        res.json({
            device: "ESP32",
            online,
            lastSeen
        });

    } catch (error) {
        console.error(error);

        res.status(500).json({
            error: "Failed to retrieve device status"
        });
    }
});

// Start database connection and HTTP server
async function startServer() {

    try {
        await client.connect();

        const database = client.db(DB_NAME);

        readings = database.collection("readings");

        await readings.createIndex({
            timestamp: -1
        });

        console.log("Connected to MongoDB");

        app.listen(PORT, "0.0.0.0", () => {

            console.log(
                `IoT Monitor API running on port ${PORT}`
            );
        });

    } catch (error) {
        console.error(
            "Server startup failed:",
            error.message
        );

        await client.close();
        process.exit(1);
    }
}

startServer();
