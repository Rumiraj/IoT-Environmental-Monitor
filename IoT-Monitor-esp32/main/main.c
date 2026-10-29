#include <stdio.h>
#include <string.h>

#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "freertos/event_groups.h"

#include "esp_log.h"
#include "esp_err.h"
#include "esp_event.h"
#include "esp_netif.h"
#include "esp_wifi.h"
#include "esp_http_client.h"
#include "esp_system.h"
#include "nvs_flash.h"

#include "driver/gpio.h"
#include "dht.h"

// --------------------------------------------------
// Configuration
// --------------------------------------------------

#define DHT_GPIO GPIO_NUM_4

#define READ_INTERVAL_MS 5000

#define API_URL "http://192.168.1.245:3000/api/sensors"

#define WIFI_CONNECTED_BIT BIT0

static const char *TAG = "IoTMonitor";

static EventGroupHandle_t wifi_event_group;

// --------------------------------------------------
// Wi-Fi event handler
// --------------------------------------------------

static void wifi_event_handler(
    void *arg,
    esp_event_base_t event_base,
    int32_t event_id,
    void *event_data)
{
    if (event_base == WIFI_EVENT)
    {
        if (event_id == WIFI_EVENT_STA_START)
        {
            ESP_LOGI(TAG, "Connecting to Wi-Fi...");
            esp_wifi_connect();
        }
        else if (event_id == WIFI_EVENT_STA_DISCONNECTED)
        {
            xEventGroupClearBits(
                wifi_event_group,
                WIFI_CONNECTED_BIT
            );

            ESP_LOGW(TAG, "Wi-Fi disconnected. Reconnecting...");

            esp_wifi_connect();
        }
    }

    if (event_base == IP_EVENT &&
        event_id == IP_EVENT_STA_GOT_IP)
    {
        ip_event_got_ip_t *event =
            (ip_event_got_ip_t *)event_data;

        ESP_LOGI(
            TAG,
            "Wi-Fi connected. IP: " IPSTR,
            IP2STR(&event->ip_info.ip)
        );

        xEventGroupSetBits(
            wifi_event_group,
            WIFI_CONNECTED_BIT
        );
    }
}

// --------------------------------------------------
// Initialize Wi-Fi
// --------------------------------------------------

static void wifi_init(void)
{
    wifi_event_group = xEventGroupCreate();

    ESP_ERROR_CHECK(esp_netif_init());

    ESP_ERROR_CHECK(
        esp_event_loop_create_default()
    );

    esp_netif_create_default_wifi_sta();

    wifi_init_config_t wifi_init_config =
        WIFI_INIT_CONFIG_DEFAULT();

    ESP_ERROR_CHECK(
        esp_wifi_init(&wifi_init_config)
    );

    ESP_ERROR_CHECK(
        esp_event_handler_register(
            WIFI_EVENT,
            ESP_EVENT_ANY_ID,
            wifi_event_handler,
            NULL
        )
    );

    ESP_ERROR_CHECK(
        esp_event_handler_register(
            IP_EVENT,
            IP_EVENT_STA_GOT_IP,
            wifi_event_handler,
            NULL
        )
    );

    wifi_config_t wifi_config = {0};

    strncpy(
        (char *)wifi_config.sta.ssid,
        CONFIG_IOT_WIFI_SSID,
        sizeof(wifi_config.sta.ssid)
    );

    strncpy(
        (char *)wifi_config.sta.password,
        CONFIG_IOT_WIFI_PASSWORD,
        sizeof(wifi_config.sta.password) - 1
    );

    ESP_ERROR_CHECK(
        esp_wifi_set_mode(WIFI_MODE_STA)
    );

    ESP_ERROR_CHECK(
        esp_wifi_set_config(
            WIFI_IF_STA,
            &wifi_config
        )
    );

    ESP_ERROR_CHECK(
        esp_wifi_start()
    );

    ESP_LOGI(TAG, "Wi-Fi initialized");
}

// --------------------------------------------------
// Send measurements to Node.js
// --------------------------------------------------

static void send_sensor_data(
    float temperature,
    float humidity)
{
    char json_payload[256];

    snprintf(
        json_payload,
        sizeof(json_payload),
        "{\"temperature\":%.1f,\"humidity\":%.1f}",
        temperature,
        humidity
    );

    esp_http_client_config_t config = {
        .url = API_URL,
        .method = HTTP_METHOD_POST,
        .timeout_ms = 5000
    };

    esp_http_client_handle_t client =
        esp_http_client_init(&config);

    if (client == NULL)
    {
        ESP_LOGE(TAG, "HTTP client initialization failed");
        return;
    }

    esp_http_client_set_header(
        client,
        "Content-Type",
        "application/json"
    );

    esp_http_client_set_header(
        client,
        "x-api-key",
        CONFIG_IOT_API_KEY
    );

    esp_http_client_set_post_field(
        client,
        json_payload,
        strlen(json_payload)
    );

    ESP_LOGI(TAG, "Sending: %s", json_payload);

    esp_err_t result =
        esp_http_client_perform(client);

    if (result == ESP_OK)
    {
        int status =
            esp_http_client_get_status_code(client);

        if (status >= 200 && status < 300)
        {
            ESP_LOGI(
                TAG,
                "Data accepted by server. HTTP %d",
                status
            );
        }
        else
        {
            ESP_LOGW(
                TAG,
                "Server rejected data. HTTP %d",
                status
            );
        }
    }
    else
    {
        ESP_LOGE(
            TAG,
            "HTTP request failed: %s",
            esp_err_to_name(result)
        );
    }

    esp_http_client_cleanup(client);
}

// --------------------------------------------------
// Main application
// --------------------------------------------------

void app_main(void)
{
    ESP_LOGI(TAG, "IoT Environmental Monitor starting...");

    esp_err_t nvs_result = nvs_flash_init();

    if (nvs_result == ESP_ERR_NVS_NO_FREE_PAGES ||
        nvs_result == ESP_ERR_NVS_NEW_VERSION_FOUND)
    {
        ESP_ERROR_CHECK(nvs_flash_erase());
        nvs_result = nvs_flash_init();
    }

    ESP_ERROR_CHECK(nvs_result);

    wifi_init();

    float temperature = 0.0f;
    float humidity = 0.0f;

    vTaskDelay(pdMS_TO_TICKS(2000));

    while (1)
    {
        esp_err_t result = dht_read_float_data(
            DHT_TYPE_DHT11,
            DHT_GPIO,
            &humidity,
            &temperature
        );

        if (result == ESP_OK)
        {
            ESP_LOGI(
                TAG,
                "Temperature: %.1f C | Humidity: %.1f %%",
                temperature,
                humidity
            );

            EventBits_t bits = xEventGroupGetBits(
                wifi_event_group
            );

            if (bits & WIFI_CONNECTED_BIT)
            {
                send_sensor_data(
                    temperature,
                    humidity
                );
            }
            else
            {
                ESP_LOGW(
                    TAG,
                    "Wi-Fi offline. Measurement not uploaded."
                );
            }
        }
        else
        {
            ESP_LOGE(
                TAG,
                "Sensor reading failed: %s",
                esp_err_to_name(result)
            );
        }

        vTaskDelay(
            pdMS_TO_TICKS(READ_INTERVAL_MS)
        );
    }
}