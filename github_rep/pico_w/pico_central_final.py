# This code finds and connects to the phone pheripheral, setting output pins to interface with the Arduino.
# It is modified from picow_ble_temp_reader.py from micropython bluetooth documentation

import bluetooth
import random
import struct
import time
import micropython
from ble_advertising import decode_services, decode_name # Helper module from micropython library
from micropython import const
from machine import Pin

# define pins, sequence variable and initial values
out1 = Pin(3,Pin.OUT) # GPIO 3 != physical 3 (ground)
out0 = Pin(2,Pin.OUT)
seq = Pin(4,Pin.OUT)
seqval = int(0)

out1.value(0)
out0.value(0)
seq.value(0)

_IRQ_CENTRAL_CONNECT = const(1)
_IRQ_CENTRAL_DISCONNECT = const(2)
_IRQ_GATTS_WRITE = const(3)
_IRQ_GATTS_READ_REQUEST = const(4)
_IRQ_SCAN_RESULT = const(5)
_IRQ_SCAN_DONE = const(6)
_IRQ_PERIPHERAL_CONNECT = const(7)
_IRQ_PERIPHERAL_DISCONNECT = const(8)
_IRQ_GATTC_SERVICE_RESULT = const(9)
_IRQ_GATTC_SERVICE_DONE = const(10)
_IRQ_GATTC_CHARACTERISTIC_RESULT = const(11)
_IRQ_GATTC_CHARACTERISTIC_DONE = const(12)
_IRQ_GATTC_DESCRIPTOR_RESULT = const(13)
_IRQ_GATTC_DESCRIPTOR_DONE = const(14)
_IRQ_GATTC_READ_RESULT = const(15)
_IRQ_GATTC_READ_DONE = const(16)
_IRQ_GATTC_WRITE_DONE = const(17)
_IRQ_GATTC_NOTIFY = const(18)
_IRQ_GATTC_INDICATE = const(19)

_ADV_IND = const(0x00)
_ADV_DIRECT_IND = const(0x01)
_ADV_SCAN_IND = const(0x02)
_ADV_NONCONN_IND = const(0x03)

SNAME = "N9DEV" # desired device name for search
_SUUID = bluetooth.UUID(0x1201) # generic networking service UUID
_CUUID = bluetooth.UUID(0x2A46) # new alert characteristic UUID

class BLECommandCentral:
    def __init__(self, ble):
        self._ble = ble
        self._ble.active(True)
        self._ble.irq(self._irq)
        self._reset()
        self._led = Pin('LED', Pin.OUT)

    def _reset(self):
        # Cached name and address from a successful scan.
        self._name = None
        self._addr_type = None
        self._addr = None

        # Cached value (if we have one)
        self._value = None

        # Callbacks for completion of various operations.
        # These reset back to None after being invoked.
        self._scan_callback = None
        self._conn_callback = None
        self._read_callback = None

        # Persistent callback for when new data is notified from the device.
        self._notify_callback = None

        # Connected device.
        self._conn_handle = None
        self._start_handle = None
        self._end_handle = None
        self._value_handle = None

    def _irq(self, event, data):
        if event == _IRQ_SCAN_RESULT:
            addr_type, addr, adv_type, rssi, adv_data = data
            if adv_type in (_ADV_IND, _ADV_DIRECT_IND):
                self._name = decode_name(adv_data) or "?"
                sMAC = ":".join("{:02x}".format(b) for b in addr)
                print("Advertisement received from device:", sMAC)
                print("Advertisement received from device name:", self._name)
                if self._name == SNAME:
                    print(f"Found target device: {self._name} with RSSI {rssi}")
                    # Found a potential device, remember it and stop scanning.
                    self._addr_type = addr_type
                    self._addr = bytes(addr)  # Note: addr buffer is owned by caller so need to copy it.
                    self._ble.gap_scan(None)
                else:
                    self._name = None

        elif event == _IRQ_SCAN_DONE:
            if self._scan_callback:
                if self._addr:
                    # Found a device during the scan (and the scan was explicitly stopped).
                    self._scan_callback(self._addr_type, self._addr, self._name)
                    self._scan_callback = None
                else:
                    # Scan timed out.
                    self._scan_callback(None, None, None)

        elif event == _IRQ_PERIPHERAL_CONNECT:
            # Connect successful.
            conn_handle, addr_type, addr = data
            if addr_type == self._addr_type and addr == self._addr:
                self._conn_handle = conn_handle
                self._ble.gattc_discover_services(self._conn_handle)

        elif event == _IRQ_PERIPHERAL_DISCONNECT:
            # Disconnect (either initiated by us or the remote end).
            conn_handle, _, _ = data
            if conn_handle == self._conn_handle:
                # If it was initiated by us, it'll already be reset.
                self._reset()

        elif event == _IRQ_GATTC_SERVICE_RESULT:
            # Connected device returned a service.
            conn_handle, start_handle, end_handle, uuid = data
            print("Service UUID: ", uuid) # print out UUID and check with expected in following line
            if conn_handle == self._conn_handle and uuid == _SUUID:
                self._start_handle, self._end_handle = start_handle, end_handle

        elif event == _IRQ_GATTC_SERVICE_DONE:
            # Service query complete.
            if self._start_handle and self._end_handle:
                self._ble.gattc_discover_characteristics(
                    self._conn_handle, self._start_handle, self._end_handle
                )
            else:
                print("Failed to find the service.")

        elif event == _IRQ_GATTC_CHARACTERISTIC_RESULT:
            # Connected device returned a characteristic.
            conn_handle, def_handle, value_handle, properties, uuid = data
            print("Char. UUID: ", uuid) # print out UUID and check with expected in following line
            if conn_handle == self._conn_handle and uuid == _CUUID:
                self._value_handle = value_handle # set value handle of characteristic

        elif event == _IRQ_GATTC_CHARACTERISTIC_DONE:
            # Characteristic query complete.
            if self._value_handle:
                # We've finished connecting and discovering device, fire the connect callback.
                if self._conn_callback:
                    self._conn_callback()
            else:
                print("Failed to find the characteristic.")

        elif event == _IRQ_GATTC_READ_RESULT:
            # A read completed successfully.
            conn_handle, value_handle, char_data = data
            if conn_handle == self._conn_handle and value_handle == self._value_handle:
                self._update_value(char_data)
                if self._read_callback:
                    self._read_callback(self._value)
                    self._read_callback = None

        elif event == _IRQ_GATTC_READ_DONE:
            # Read completed (no-op).
            conn_handle, value_handle, status = data

        elif event == _IRQ_GATTC_NOTIFY:
            # The phone notifies its value on button press/command.
            print("Notification from phone")
            conn_handle, value_handle, notify_data = data
            if conn_handle == self._conn_handle and value_handle == self._value_handle:
                byteval = bytes(notify_data)
                print("Data: ", byteval)
                self._update_value(notify_data)
                global out1 # introduce pin variables to this scope
                global out0
                global seq
                if byteval == b'3': # Button 3, Reset Alarm
                    #print("3")
                    out1.value(1)
                    out0.value(1)
                elif byteval == b'2': # Button 2, Open
                    #print("2")
                    out1.value(1)
                    out0.value(0)
                elif byteval == b'1': # Button 1, Closed Door
                    #print("1")
                    out1.value(0)
                    out0.value(1)
                else:
                    print("no command")
                    out1.value(0)
                    out0.value(0)
                print("P1, P0: ", out1.value(), out0.value()) 
                global seqval
                seqval = (seqval + 1) % 2 # Flip seq # to Arduino, notifying it
                seq.value(seqval)
                print("Seq #: ", seq.value()) 
                if self._notify_callback:
                    self._notify_callback(self._value)

    # Returns true if we've successfully connected and discovered characteristics.
    def is_connected(self):
        return self._conn_handle is not None and self._value_handle is not None

    # Find a device advertising the phone's service.
    def scan(self, callback=None):
        self._addr_type = None
        self._addr = None
        self._scan_callback = callback
        self._ble.gap_scan(2000, 30000, 30000)

    # Connect to the specified device (otherwise use cached address from a scan).
    def connect(self, addr_type=None, addr=None, callback=None):
        self._addr_type = addr_type or self._addr_type
        self._addr = addr or self._addr
        self._conn_callback = callback
        if self._addr_type is None or self._addr is None:
            return False
        self._ble.gap_connect(self._addr_type, self._addr)
        return True

    # Disconnect from current device.
    def disconnect(self):
        if not self._conn_handle:
            return
        self._ble.gap_disconnect(self._conn_handle)
        self._reset()

    # Issues an (asynchronous) read, will invoke callback with data.
    def read(self, callback):
        if not self.is_connected():
            return
        self._read_callback = callback
        try:
            self._ble.gattc_read(self._conn_handle, self._value_handle)
        except OSError as error:
            print(error)

    # Sets a callback to be invoked when the device notifies us.
    def on_notify(self, callback):
        self._notify_callback = callback

    def _update_value(self, data):
        # Data is string (character)
        #print("updating value: ", bytes(data))
        try:
            self._value = data
        except OSError as error:
            print(error)

    def value(self):
        return self._value

def sleep_ms_flash_led(self, flash_count, delay_ms):
    self._led.off()
    while(delay_ms > 0):
        for i in range(flash_count):
            self._led.on()
            time.sleep_ms(100)
            self._led.off()
            time.sleep_ms(100)
            delay_ms -= 200
        time.sleep_ms(1000)
        delay_ms -= 1000

def print_comm(result):
    print("read command: %s" % result)

def demo(ble, central):
    not_found = False

    def on_scan(addr_type, addr, name):
        if addr_type is not None:
            print("Found phone: %s" % name)
            central.connect()
        else:
            nonlocal not_found
            not_found = True
            print("No phone found.")

    central.scan(callback=on_scan)

    # Wait for connection...
    while not central.is_connected():
        time.sleep_ms(100)
        if not_found:
            return

    print("Connected")

    # Explicitly sleep otherwise
    while central.is_connected():
        #central.read(callback=print_comm)
        sleep_ms_flash_led(central, 2, 2000)

    print("Disconnected")

if __name__ == "__main__":
    ble = bluetooth.BLE()
    central = BLECommandCentral(ble)
    while(True):
        demo(ble, central)
        sleep_ms_flash_led(central, 1, 10000)

