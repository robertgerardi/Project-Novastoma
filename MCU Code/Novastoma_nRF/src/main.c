#include <zephyr/types.h>
#include <stddef.h>
#include <string.h>
#include <errno.h>
#include <zephyr/sys/byteorder.h>
#include <zephyr/kernel.h>
#include <zephyr/settings/settings.h>
#include <zephyr/bluetooth/bluetooth.h>
#include <zephyr/bluetooth/hci.h>
#include <zephyr/bluetooth/conn.h>
#include <zephyr/bluetooth/uuid.h>
#include <zephyr/bluetooth/gatt.h>
#include <zephyr/drivers/gpio.h>
#include <zephyr/logging/log.h>
#include <battery.h>

// Logging
LOG_MODULE_REGISTER(main, LOG_LEVEL_INF);

// Data
#define 	BUFFER_SIZE 20
#define		DATA_SIZE 12
uint8_t		level = 0;
uint8_t 	incoming_comm = 0;
uint8_t 	outgoing_comm = 0;
char 		outgoing_data[DATA_SIZE];
char	 	data_buffer[BUFFER_SIZE][DATA_SIZE] = {0};
uint8_t 	buffer_ptr = 0;
uint8_t 	buffer_total = 0;
uint64_t 	recieved_time;
uint64_t	system_on_time;
uint64_t 	time_on_when_recieved;
uint64_t 	time_stamp;

// Function definition for creating time stamp data to be sent out
void Create_Outgoing_Data();
// Function definition for sending out the data buffer
int Send_Buffer();

// Timing
#define MILLI  1U	
#define SECOND 1000U
#define MINUTE 60000U

// GPIO
const gpio_pin_t LEVEL1 = 2;
const gpio_pin_t LEVEL2 = 3;
const gpio_pin_t LEVEL3 = 28;

const gpio_pin_t POWER1 = 13;
const gpio_pin_t POWER2 = 14;
const gpio_pin_t POWER3 = 15;

#define HIGH (1)
#define LOW  (0)

// GPIO Devices dev0 = gpio0 (pins 0-5) : dev1 = gpio1 (pins 6-10)
const struct device *dev0;
const struct device *dev1;

// Battery
uint16_t battery_millivolt = 0;
uint8_t battery_percentage = 0;
struct k_work battery_work;
void battery_work_handler(struct k_work *work_item);

// Nova Service
bool ble_connected = false;
bool ble_lost_connection = false;

#define BT_UUID_CUSTOM_SERVICE_VAL \
	BT_UUID_128_ENCODE(0x000070a1, 0x726d, 0x4e69, 0x8172, 0x7eb59ca13ca8)

// Current Connection Pointer
struct bt_conn *cur_conn;

// UUID Service Struct (Main Service)
static struct bt_uuid_128 vnd_uuid = BT_UUID_INIT_128(BT_UUID_CUSTOM_SERVICE_VAL);

// Gatt Characteristics (Command, Data, and Battery)
#define BT_UUID_COMM_CHAR_VAL BT_UUID_128_ENCODE(0x010070a1, 0x726d, 0x4e69, 0x8172, 0x7eb59ca13ca8)
#define BT_UUID_DATA_CHAR_VAL BT_UUID_128_ENCODE(0x020070a1, 0x726d, 0x4e69, 0x8172, 0x7eb59ca13ca8)
#define BT_UUID_BATT_CHAR_VAL BT_UUID_128_ENCODE(0x030070a1, 0x726d, 0x4e69, 0x8172, 0x7eb59ca13ca8)

#define BT_UUID_COMM_CHAR BT_UUID_DECLARE_128(BT_UUID_COMM_CHAR_VAL)
#define BT_UUID_DATA_CHAR BT_UUID_DECLARE_128(BT_UUID_DATA_CHAR_VAL)
#define BT_UUID_BATT_CHAR BT_UUID_DECLARE_128(BT_UUID_BATT_CHAR_VAL)

// Advertising Function Declaration
static void start_advertising(void);

// Notification Enabled
bool notify_data_enabled = false;
bool notify_batt_enabled = false;
bool notify_comm_enabled = false;

// Notify Client Characteristic Configurations (used to subsribe to gatt notifications)
static void data_ccc_cfg_changed(const struct bt_gatt_attr *attr, uint16_t value);
static void batt_ccc_cfg_changed(const struct bt_gatt_attr *attr, uint16_t value);
static void comm_ccc_cfg_changed(const struct bt_gatt_attr *attr, uint16_t value);

// Gatt Callback Declarations
static ssize_t data_read_characteristic_cb(struct bt_conn *conn, const struct bt_gatt_attr *attr,
									 void *buf, uint16_t len, uint16_t offset);

static ssize_t batt_read_characteristic_cb(struct bt_conn *conn, const struct bt_gatt_attr *attr,
									 void *buf, uint16_t len, uint16_t offset);

static ssize_t comm_read_characteristic_cb(struct bt_conn *conn, const struct bt_gatt_attr *attr,
									 void *buf, uint16_t len, uint16_t offset);		

static ssize_t command_write_characteristic_cb(struct bt_conn *conn, const struct bt_gatt_attr *attr,
									 const void *buf, uint16_t len, uint16_t offset, uint8_t flags);								

// Service/Gatt Definitions
BT_GATT_SERVICE_DEFINE(vnd_svc,
	BT_GATT_PRIMARY_SERVICE(&vnd_uuid),
	BT_GATT_CHARACTERISTIC(BT_UUID_DATA_CHAR,
							BT_GATT_CHRC_READ | BT_GATT_CHRC_NOTIFY,
							BT_GATT_PERM_READ,
							data_read_characteristic_cb, NULL, NULL),
	BT_GATT_CCC(data_ccc_cfg_changed, BT_GATT_PERM_READ | BT_GATT_PERM_WRITE),							
	BT_GATT_CHARACTERISTIC(BT_UUID_BATT_CHAR,
							BT_GATT_CHRC_READ | BT_GATT_CHRC_NOTIFY,
							BT_GATT_PERM_READ,
							batt_read_characteristic_cb, NULL, NULL),
	BT_GATT_CCC(batt_ccc_cfg_changed, BT_GATT_PERM_READ | BT_GATT_PERM_WRITE),
	BT_GATT_CHARACTERISTIC(BT_UUID_COMM_CHAR,
							BT_GATT_CHRC_READ | BT_GATT_CHRC_WRITE | BT_GATT_CHRC_NOTIFY,
							BT_GATT_PERM_READ | BT_GATT_PERM_WRITE,
							comm_read_characteristic_cb, command_write_characteristic_cb, NULL),
	BT_GATT_CCC(comm_ccc_cfg_changed, BT_GATT_PERM_READ | BT_GATT_PERM_WRITE),
);

// Advertising Flags
#define ADV_FLAGS BT_LE_ADV_PARAM(BT_LE_ADV_OPT_CONNECTABLE | BT_LE_ADV_OPT_USE_NAME, \
									BT_GAP_ADV_SLOW_INT_MIN, \
									BT_GAP_ADV_SLOW_INT_MAX, \
									NULL)

// Advertising Struct
static const struct bt_data ad[] = {
	BT_DATA_BYTES(BT_DATA_FLAGS, (BT_LE_AD_GENERAL | BT_LE_AD_NO_BREDR)),
	BT_DATA_BYTES(BT_DATA_UUID128_ALL, BT_UUID_CUSTOM_SERVICE_VAL),
};

// Rx Tx Updated - Will show in terminal new packet sizes 
// Usually Rx=23 Tx=23 bytes, more than enough for our data packets
void mtu_updated(struct bt_conn *conn, uint16_t tx, uint16_t rx)
{
	LOG_INF("Updated MTU: TX: %d RX: %d bytes", tx, rx);
}

// Bluetooth Callback for updated Rx Tx
static struct bt_gatt_cb gatt_callbacks = {
	.att_mtu_updated = mtu_updated
};

// Connected Callback
static void connected(struct bt_conn *conn, uint8_t err)
{
	bt_le_adv_stop();

	if (err) {
		LOG_ERR("Connection failed (err 0x%02x)", err);
	} else {
		LOG_INF("Connected");
	}

	ble_connected = true;
	ble_lost_connection = false;
	cur_conn = bt_conn_ref(conn);

	// Get Connection Parameters
	struct bt_conn_info info;
	err = bt_conn_get_info(conn, &info);
	if (err) {
		LOG_ERR("bt_conn_get_info() returned error: %d", err);
	}
	float conn_interval = info.le.interval * 1.25; // Unit is in 1.25 ms (conversion)
	uint16_t super_timeout = info.le.timeout * 10;	// Unit is in 10 ms (conversion)
	LOG_INF("Connection parameters: interval %.2f ms, latency %d intervals, timout %d ms", conn_interval, info.le.latency, super_timeout);

	// Try to request new parameters for low power
	// The Central dictates the connection - may change or reject these
	struct bt_le_conn_param low_power_params;
	low_power_params.interval_min = 800;
	low_power_params.interval_max = 1200;
	low_power_params.latency = 0;
	low_power_params.timeout = 600;
	bt_conn_le_param_update(conn, &low_power_params); 
}

// Disconnected Callback
static void disconnected(struct bt_conn *conn, uint8_t reason)
{
	LOG_INF("Disconnected (reason 0x%02x)", reason);
	bt_conn_unref(conn);
	ble_connected = false;
	ble_lost_connection = true;

	k_msleep(SECOND);
	start_advertising();
}

// Connection Parameters Changed
void on_le_param_updated(struct bt_conn *conn, uint16_t interval, uint16_t latency, uint16_t timeout)
{
    float conn_interval = interval * 1.25;	// Unit is in 1.25 ms (conversion)
    uint16_t super_timeout = timeout * 10;	// Unit is in 10 ms (conversion)
    LOG_INF("Connection parameters updated: interval %.2f ms, latency %d intervals, timeout %d ms", conn_interval, latency, super_timeout);
}

// Callback Definition
BT_CONN_CB_DEFINE(conn_callbacks) = {
	.connected = connected,
	.disconnected = disconnected,
	.le_param_updated = on_le_param_updated,
};

// Data CCC Configurations Changed Callback (Notify is enabled/disabled by central)
static void data_ccc_cfg_changed(const struct bt_gatt_attr *attr, uint16_t value) {
	notify_data_enabled = (value == BT_GATT_CCC_NOTIFY);
	LOG_INF("Data Characteristic Notifications %s", notify_data_enabled ? "Enabled" : "Disabled");
}

// Battery CCC Configuration Changed Callback (Notify is enabled/disabled by central)
static void batt_ccc_cfg_changed(const struct bt_gatt_attr *attr, uint16_t value) {
	notify_batt_enabled = (value == BT_GATT_CCC_NOTIFY);
	LOG_INF("Battery Characteristic Notifications %s", notify_batt_enabled ? "Enabled" : "Disabled");
}

// Command CCC Configuration Changed Callback (Notify is enabled/disabled by central)
static void comm_ccc_cfg_changed(const struct bt_gatt_attr *attr, uint16_t value) {
	notify_comm_enabled = (value == BT_GATT_CCC_NOTIFY);
	LOG_INF("Command Characteristic Notifications %s", notify_comm_enabled ? "Enabled" : "Disabled");
}

// Data Read BLE Callback
static ssize_t data_read_characteristic_cb(struct bt_conn *conn, const struct bt_gatt_attr *attr,
									 void *buf, uint16_t len, uint16_t offset) {

	return bt_gatt_attr_read(conn, attr, buf, len, offset, &level, sizeof(level));
};

// Battery Read BLE Callback
static ssize_t batt_read_characteristic_cb(struct bt_conn *conn, const struct bt_gatt_attr *attr,
									 void *buf, uint16_t len, uint16_t offset) {

	return bt_gatt_attr_read(conn, attr, buf, len, offset, &battery_percentage, sizeof(battery_percentage));
};

// Command Read BLE Callback
static ssize_t comm_read_characteristic_cb(struct bt_conn *conn, const struct bt_gatt_attr *attr,
									 void *buf, uint16_t len, uint16_t offset) {

	return bt_gatt_attr_read(conn, attr, buf, len, offset, &outgoing_comm, sizeof(outgoing_comm));
};

// Command Write BLE Callback
static ssize_t command_write_characteristic_cb(struct bt_conn *conn, const struct bt_gatt_attr *attr, const void *buf, uint16_t len, uint16_t offset, uint8_t flags) {
	uint32_t epoc = *((uint32_t *)buf);
	LOG_INF("Unix Time: %d", epoc);
	recieved_time = epoc;
	time_on_when_recieved = k_uptime_get() / 1000;
	return len;
}

// Activate Bluetooth
static void bt_ready(void)
{
	LOG_INF("Bluetooth initialized");

	if (IS_ENABLED(CONFIG_SETTINGS)) {
		settings_load();
	}

	start_advertising();
}

// Begin BLE Advertising
static void start_advertising(void) {
	int err;

	err = bt_le_adv_start(ADV_FLAGS, ad, ARRAY_SIZE(ad), NULL, 0);
	if (err) {
		LOG_ERR("Advertising failed to start (err %d)", err);
		return;
	}

	LOG_INF("Advertising successfully started");
}

// GPIO Initialize
int Init_GPIO() {
	int ret = 0;
	dev0 = DEVICE_DT_GET(DT_NODELABEL(gpio0));
	dev1 = DEVICE_DT_GET(DT_NODELABEL(gpio1));

	if (!device_is_ready(dev0)) {
		ret += 1;
	} else {
		LOG_INF("GPIO-0 Initializing");
		gpio_pin_configure(dev0, LEVEL1, GPIO_INPUT | GPIO_ACTIVE_LOW);
		gpio_pin_configure(dev0, LEVEL2, GPIO_INPUT | GPIO_ACTIVE_LOW);
		gpio_pin_configure(dev0, LEVEL3, GPIO_INPUT | GPIO_ACTIVE_LOW);
	}
	
	if (!device_is_ready(dev1)) {
		ret += 2;
	} else {
		LOG_INF("GPIO-1 Initializing\n");
		gpio_pin_configure(dev1, POWER1, GPIO_OUTPUT_LOW | GPIO_ACTIVE_HIGH);
		gpio_pin_configure(dev1, POWER2, GPIO_OUTPUT_LOW | GPIO_ACTIVE_HIGH);
		gpio_pin_configure(dev1, POWER3, GPIO_OUTPUT_LOW | GPIO_ACTIVE_HIGH);
	}

	return ret;
}

// Battery Work Handler
void battery_work_handler(struct k_work *work_item)
{
	battery_get_millivolt(&battery_millivolt);
	battery_get_percentage(&battery_percentage, battery_millivolt);
}

// Get Waste Level
int Get_Bag_Level() {
	int ret = 0;
	gpio_pin_set(dev1, POWER3, HIGH);
	gpio_pin_set(dev1, POWER2, HIGH);
	gpio_pin_set(dev1, POWER1, HIGH);

	k_msleep(MILLI * 100);

	if (gpio_pin_get(dev0, LEVEL3)) {
		ret = 3;
	} else if (gpio_pin_get(dev0, LEVEL2)) {
		ret = 2;
	} else if (gpio_pin_get(dev0, LEVEL1)) {
		ret = 1;
	}

	k_msleep(MILLI * 100);

	gpio_pin_set(dev1, POWER3, LOW);
	gpio_pin_set(dev1, POWER2, LOW);
	gpio_pin_set(dev1, POWER1, LOW);

	return ret;
}

// Create Outgoing Data
void Create_Outgoing_Data() {
	if (recieved_time) {
		system_on_time = k_uptime_get() / 1000;
		time_stamp = recieved_time + system_on_time - time_on_when_recieved;
		for (int i = 9; i >= 0; i--) {
			int tmp = time_stamp % 10;
			outgoing_data[i] = 48 + tmp;
			time_stamp /= 10;
		}
		outgoing_data[10] = 48 + level;
	}
}

// Send out data buffer
int Send_Buffer() {
	// Give the Bluetooth some time
	k_msleep(MILLI * 500);

	int tmpPtr = 0;

	if (buffer_total == BUFFER_SIZE) {
		if (buffer_ptr != (BUFFER_SIZE - 1)) {
			tmpPtr = buffer_ptr;
		}
	}
	
	while (buffer_total > 0) {
		LOG_INF("BuffPtr: %d", tmpPtr);
		char tmpData[DATA_SIZE];
		strcpy(tmpData, data_buffer[tmpPtr]);
		bt_gatt_notify(cur_conn, &vnd_svc.attrs[1], tmpData, sizeof(tmpData));
		buffer_total--;

		tmpPtr++;
		if (tmpPtr == BUFFER_SIZE) {
			tmpPtr = 0;
		}

		LOG_INF("Buffer data at index %d sent: %s. Total buffer left: %d", buffer_ptr, tmpData, buffer_total);

		k_msleep(MILLI * 5);
	}
	buffer_ptr = 0;
	return buffer_total;
}

/* 	This program only has the main thread.
	If more threads are implemented, the syntax is:
	K_THREAD_DEFINE(name, stack_size, function_entry, param 1, param 2, param 3, priority, options, delay);
*/

// Main Program
int main() {
	// Give Serial Monitor Time to Wake Up (Can be removed after debug)
	k_msleep(SECOND);

	// Error handling
	int err;

	// GPIO Initialization will return non-zero error
	err = Init_GPIO();
	switch (err) {
		case 0 : LOG_INF("GPIO Initialized."); break;
		case 1 : LOG_ERR("GPIO 1 Not Initialized."); break;
		case 2 : LOG_ERR("GPIO 2 Not Initialized."); break;
		case 3 : LOG_ERR("GPIO 1 and 2 Not Initialized."); break;
		default : break;
	}

	// Bluetooth Initialization will return non-zero error
	err = bt_enable(NULL);
	if (err) {
		LOG_ERR("Bluetooth Init failed (err %d).", err);
	}

	// Load Bluetooth settings
	settings_load();

	// Ready the Bluetooth stack
	bt_ready();

	// Register the GATT callbacks
	bt_gatt_cb_register(&gatt_callbacks);

	// Ready the battery helper
	int ret = 0;
	ret |= battery_init();
	ret |= battery_charge_start();

	if (ret) {
		LOG_ERR("Failed to Initialize Battery Helper.");
	} else {
		LOG_INF("Battery Helper Initialized.");
	}

	// Initialize the battery helper
	k_work_init(&battery_work, battery_work_handler);

	// Main loop
	for(;;) {
		// Get Data
		level = Get_Bag_Level();
		Create_Outgoing_Data();
		k_work_submit(&battery_work);
		LOG_INF("Waste Level: %d - Battery: %d mV - Percentage: %d%%", level, battery_millivolt, battery_percentage);
		
		if (ble_connected) {
			// If connected, notify the data.
			if (buffer_total) {
				// Send out buffer if needed.
				err = Send_Buffer();
			}
			bt_gatt_notify(cur_conn, &vnd_svc.attrs[1], outgoing_data, sizeof(outgoing_data));
			bt_gatt_notify(cur_conn, &vnd_svc.attrs[4], &battery_percentage, sizeof(battery_percentage));
		} else {
			if (ble_lost_connection) {
				// If there was a previous connection, buffer the data.
				buffer_total++;
				if (buffer_total > BUFFER_SIZE) {
					buffer_total = BUFFER_SIZE;
				}
				strcpy(data_buffer[buffer_ptr], outgoing_data);
				LOG_INF("Data Buffered at index %d: %s. Buffer Size: %d", buffer_ptr, outgoing_data, buffer_total);
				buffer_ptr++;
				if (buffer_ptr > (BUFFER_SIZE - 1)) {
					buffer_ptr = 0;
				}
			}
		}

		k_msleep(SECOND * 5);
	}
}

