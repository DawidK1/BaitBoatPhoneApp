package com.urmom.baitboatphoneapp;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;

public class UartService extends Service {
    private final static String TAG = UartService.class.getSimpleName();

    private int mConnectionState = STATE_DISCONNECTED;

    private  boolean canTx = false;

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    private static final int TX_STATE_IDLE = 0;
    private static final int TX_STATE_WAIT_FOR_CONFIRM = 1;

    private static final int WRITE_TIMEOUT_LIMIT = 30;

    String mDeviceName = "noname";


    public final static String ACTION_GATT_CONNECTED =
            "com.nordicsemi.nrfUART.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.nordicsemi.nrfUART.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.nordicsemi.nrfUART.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.nordicsemi.nrfUART.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA =
            "com.nordicsemi.nrfUART.EXTRA_DATA";
    public final static String DEVICE_DOES_NOT_SUPPORT_UART =
            "com.nordicsemi.nrfUART.DEVICE_DOES_NOT_SUPPORT_UART";
    public final static String DEVICE_DEAD_CONNECTION =
            "com.nordicsemi.nrfUART.DEVICE_DEAD_CONNECTION";

    public static final UUID TX_POWER_UUID = UUID.fromString("00001804-0000-1000-8000-00805f9b34fb");
    public static final UUID TX_POWER_LEVEL_UUID = UUID.fromString("00002a07-0000-1000-8000-00805f9b34fb");
    public static final UUID CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    public static final UUID FIRMWARE_REVISON_UUID = UUID.fromString("00002a26-0000-1000-8000-00805f9b34fb");
    public static final UUID DIS_UUID = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb");
    public static final UUID RX_SERVICE_UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");
    public static final UUID RX_CHAR_UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e");
    public static final UUID TX_CHAR_UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e");

    private final Semaphore writeSema = new Semaphore(1);

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner mBluetoothScanner;
    private BluetoothGatt mBluetoothGatt;


    int gattWriteState = TX_STATE_IDLE;

    public int disconnectReason;
    private int writeTimeoutCounter = 0;
    private ConcurrentLinkedQueue<Byte> rxQueue = new ConcurrentLinkedQueue<Byte>();
    private ConcurrentLinkedQueue<Byte> txQueue = new ConcurrentLinkedQueue<Byte>();


    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction;

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                mConnectionState = STATE_CONNECTED;
                Log.i(TAG, "Connected to GATT server.");
                // Attempts to discover services after successful connection.
                Log.i(TAG, "Attempting to start service discovery:" +
                        mBluetoothGatt.discoverServices());
                broadcastUpdate(ACTION_GATT_CONNECTED);

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                mConnectionState = STATE_DISCONNECTED;
                broadcastUpdate(ACTION_GATT_DISCONNECTED);
                Log.i(TAG, "Disconnected from GATT server.");
                Log.d(TAG, "reason " + status);
                canTx = false;
                disconnectReason = status;
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            Log.d(TAG, "discovered status = " + status );

            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "mBluetoothGatt = " + mBluetoothGatt );
                enableTXNotification();
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
                canTx = true;
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt,
                                          BluetoothGattCharacteristic characteristic,
                                          int status) {
            Log.d(TAG, "Write done, status " + status);
            if(txQueue.size() > 0)
            {
                startGattWrite();
            }
            else{
                gattWriteState = TX_STATE_IDLE;
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            Log.d(TAG, "Notification: "  +  characteristic.getStringValue(0)  + "\n");

            byte[] val = characteristic.getValue();
            for(int i = 0; i < val.length; i++)
            {
                rxQueue.add(new Byte(val[i]));
            }
            broadcastUpdate(ACTION_DATA_AVAILABLE);
        }
    };

    // Device scan callback.
    private ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
//            Log.d(TAG, "Device Name: " + result.getDevice().getName() + " rssi: " + result.getRssi() + "\n");
            if(result.getDevice().getName() != null && result.getDevice().getName().equals(mDeviceName))
            {
                Log.d(TAG, "Found correct device, addr: " + result.getDevice().getAddress() + "\n");
                connect(result.getDevice().getAddress());
                mBluetoothScanner.stopScan(leScanCallback);
            }
            }
    };

    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }


    public class LocalBinder extends Binder {
        UartService getService() {
            return UartService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        close();
        return super.onUnbind(intent);
    }

    private final IBinder mBinder = new LocalBinder();

    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    public boolean initialize() {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.

        Log.d(TAG, "ble init start");
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        if (mBluetoothScanner == null)
        {
            mBluetoothScanner = mBluetoothAdapter.getBluetoothLeScanner();
            if (mBluetoothScanner == null) {
                Log.e(TAG, "Unable to obtain a BluetoothScanner.");
                return false;
            }
        }
        Log.d(TAG, "ble init end" );
        return true;
    }

    public void startScanning() {
        boolean device_found = false;
        List<BluetoothDevice> devices = mBluetoothManager.getConnectedDevices(BluetoothProfile.GATT);
        Log.d(TAG, "Currently connected " + devices.size() + " devices");
        for(BluetoothDevice d: devices)
        {
            Log.d(TAG, "Device name: " + d.getName());
            if(d.getName() != null && d.getName().equals(mDeviceName))
            {
                mBluetoothGatt = d.connectGatt(this, false, mGattCallback);
                Log.d(TAG, "Connecting to device from connected list");
                device_found = true;
            }
        }
        if(!device_found)
        {
            mBluetoothScanner.startScan(leScanCallback);
            Log.d(TAG, "Requesting scanning");
        }
    }

    public boolean connect(final String address) {
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        Log.d(TAG, "Trying to create a new connection.");
        mConnectionState = STATE_CONNECTING;
        return true;
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
        // mBluetoothGatt.close();
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        Log.w(TAG, "mBluetoothGatt closed");
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    /**
     * Request a read on a given {@code BluetoothGattCharacteristic}. The read result is reported
     * asynchronously through the {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
     * callback.
     *
     * @param characteristic The characteristic to read from.
     */
    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.readCharacteristic(characteristic);
    }

    /**
     * Enables or disables notification on a give characteristic.
     *

     */

    /**
     * Enable Notification on TX characteristic
     *
     * @return
     */
    public void enableTXNotification()
    {
        BluetoothGattService RxService = mBluetoothGatt.getService(RX_SERVICE_UUID);
        if (RxService == null) {
            showMessage("Rx service not found!");
//            broadcastUpdate(DEVICE_DOES_NOT_SUPPORT_UART);
            return;
        }
        BluetoothGattCharacteristic TxChar = RxService.getCharacteristic(TX_CHAR_UUID);
        if (TxChar == null) {
            showMessage("Tx charateristic not found!");
//            broadcastUpdate(DEVICE_DOES_NOT_SUPPORT_UART);
            return;
        }
        mBluetoothGatt.setCharacteristicNotification(TxChar,true);

        BluetoothGattDescriptor descriptor = TxChar.getDescriptor(CCCD);
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        mBluetoothGatt.writeDescriptor(descriptor);

    }

    private void startGattWrite()
    {
        Log.d(TAG, "Starting sending by GATT queue " + txQueue.size());
        if(txQueue.size() == 0)
        {
            return;
        }
        BluetoothGattService RxService = mBluetoothGatt.getService(RX_SERVICE_UUID);
        if (RxService == null) {
            showMessage("Rx service not found!");
//            broadcastUpdate(DEVICE_DOES_NOT_SUPPORT_UART);
            return;
        }
        BluetoothGattCharacteristic RxChar = RxService.getCharacteristic(RX_CHAR_UUID);
        if (RxChar == null) {
            showMessage("Rx charateristic not found!");
//            broadcastUpdate(DEVICE_DOES_NOT_SUPPORT_UART);
            return;
        }

        int bytes_to_send = Math.min(20, txQueue.size());

        byte[] bytes = new byte[bytes_to_send];
        for(int i = 0; i < bytes_to_send; i++)
        {
            bytes[i] = txQueue.poll();
        }

        RxChar.setValue(bytes);
        mBluetoothGatt.writeCharacteristic(RxChar);
        Log.d(TAG, "Sent " + bytes.length);
        gattWriteState = TX_STATE_WAIT_FOR_CONFIRM;
        writeTimeoutCounter = 0;
    }


    public void sendData(byte[] data)
    {
        for(int i = 0; i < data.length; i++)
        {
            txQueue.add(new Byte(data[i]));
        }

        if (gattWriteState == TX_STATE_IDLE)
        {
            startGattWrite();
        }
    }

    public boolean isBleSending()
    {
        return gattWriteState != TX_STATE_IDLE;
    }

    private void showMessage(String msg) {
        Log.e(TAG, msg);
    }
    /**
     * Retrieves a list of supported GATT services on the connected device. This should be
     * invoked only after {@code BluetoothGatt#discoverServices()} completes successfully.
     *
     * @return A {@code List} of supported services.
     */
    public List<BluetoothGattService> getSupportedGattServices() {
        if (mBluetoothGatt == null) return null;

        return mBluetoothGatt.getServices();
    }

    public boolean isTxPossible()
    {
        return canTx && (writeSema.availablePermits() > 0);
    }

    public boolean hasRxBytes()
    {
        return rxQueue.size() > 0;
    }

    public Byte getRxByte()
    {
        return rxQueue.poll();
    }

    public void setDeviceName(String newName)
    {
        mDeviceName = newName;
    }

    void timeoutWatchdog()
    {
        if(gattWriteState == TX_STATE_WAIT_FOR_CONFIRM)
        {
            writeTimeoutCounter++;
            if(writeTimeoutCounter >= WRITE_TIMEOUT_LIMIT)
            {
                broadcastUpdate(DEVICE_DEAD_CONNECTION);
                disconnect();
            }
        }
    }
}