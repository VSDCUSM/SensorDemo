package com.vsdc.sensordemo;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.support.annotation.NonNull;
import android.util.Log;
import android.widget.Toast;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.UUID;

/**
 * Framework class for BLE related functions (scanning, advertising, connecting).
 */
public class BLEFramework{
    private static final String LOG_TAG = BLEFramework.class.getSimpleName();

    private static final int PERMISSION_COARSE_LOCATION = 1;
    public enum BLEEvent{
        scanStarted, scanStopped, scanFoundNew, scanFailed, adStarted, adStopped, adFailed
    }

    private BLECoreActivity mActivity;
    private Thread mPeriodicScanThread;

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner mBLEScanner;
    private BluetoothLeAdvertiser mBLEAdvertiser;
    private AdvertiseData mBLEData;
    private AdvertiseSettings mBLEAdSettings;
    private BluetoothGatt mBluetoothGatt;

    private boolean mIsScanning;
    volatile private boolean mIsPeriodicScanning;
    private boolean mIsAdvertising;
    private ArrayList<BluetoothDevice> mBLEDevicesFound;

    /**
     * Activity class used to instantiate the BLEFramework.
     */
    public static class BLECoreActivity extends Activity{
        private static final int REQUEST_ENABLE_BT = 1; //Request code for Intent to enable Bluetooth

        /**
         * Callback for important BLE events (to be overridden optionally by its subclasses).
         * @param bleEvent Type of BLE event
         */
        public void bleNotify(BLEEvent bleEvent){
        }

        @Override
        protected void onCreate(Bundle savedInstanceState){
            super.onCreate(savedInstanceState);
            // Get Bluetooth Adapter
            final BluetoothManager bluetoothManager =
                    (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            final BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
            // Check if Bluetooth is enabled
            if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()){
                // Prompt user to enable Bluetooth if not enabled
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
            // Request runtime permission for Android Marshmallow and above
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
                if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED){
                    Toast.makeText(this, R.string.ble_permission_request, Toast.LENGTH_SHORT).show();
                    requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                            PERMISSION_COARSE_LOCATION);
                }
            }
        }

        @Override
        protected void onActivityResult(int requestCode, int resultCode, Intent data){
            if (requestCode == REQUEST_ENABLE_BT && resultCode != RESULT_OK){
                // If user doesn't enable Bluetooth // Alert the user
                Toast.makeText(this, R.string.ble_enable_fail, Toast.LENGTH_SHORT).show();
                // Return to the previous activity
                finish();
            }
        }
        @Override
        public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                               @NonNull int[] results){
            if (requestCode == PERMISSION_COARSE_LOCATION){
                if (results[0] == PackageManager.PERMISSION_GRANTED){
                    Log.d(LOG_TAG, "Coarse location permission granted.");
                }else{
                    Log.d(LOG_TAG, "Coarse location permission denied.");
                    // If user doesn't enable Bluetooth // Alert the user
                    Toast.makeText(this, R.string.ble_permission_denied, Toast.LENGTH_SHORT).show();
                    // Return to the previous activity
                    finish();
                }
            }
        }
    }

    /**
     * Constructor to get an Activity instance from the calling activity and initialise BLE adapter.
     */
    public BLEFramework(BLECoreActivity activity){
        mActivity = activity;
        // Get Bluetooth Adapter
        final BluetoothManager bluetoothManager =
                (BluetoothManager) mActivity.getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
        // Initialise other member variables
        mPeriodicScanThread = null;
        mBLEScanner = mBluetoothAdapter.getBluetoothLeScanner();
        mBLEAdvertiser = mBluetoothAdapter.getBluetoothLeAdvertiser();
        mIsScanning = false;
        mIsPeriodicScanning = false;
        mBLEDevicesFound = new ArrayList<>();
    }

    /**
     * Scan for BLE devices and display the result on the list.
     */
    public void bleStartScan(){
        // Start scan if not scanning
        if (!mIsScanning){ // If not currently scanning
            // Start scan
            mBLEScanner.startScan(mScanCallback);
            mIsScanning = true;
            if (!mIsPeriodicScanning){
                mActivity.bleNotify(BLEEvent.scanStarted); // Only notify for one-off scans
            }
        }
    }
    /**
     * Scan callback for startScan and stopScan.
     */
    private final ScanCallback mScanCallback = new ScanCallback(){
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            BluetoothDevice device = result.getDevice();
            // Alert device name, address and rssi
            String info = device.getName() + " | " + device.getAddress() + " | " + result.getRssi();
            Toast.makeText(mActivity, info, Toast.LENGTH_SHORT).show();
            // Add found device to current list of devices
            if (!mBLEDevicesFound.contains(device)){
                mBLEDevicesFound.add(device);
                mActivity.bleNotify(BLEEvent.scanFoundNew);
            }
        }
        @Override
        public void onScanFailed(int errorCode){
            Toast.makeText(mActivity, "Scan failed!", Toast.LENGTH_SHORT).show();
            mActivity.bleNotify(BLEEvent.scanFailed);
        }
    };
    /**
     * Stop scanning.
     * ### BEWARE THAT THIS FUNCTION MAY BE CALLED EXTERNALLY WHILE PERIODICALLY SCANNING
     */
    public void bleStopScan(){
        if (mIsScanning){
            mBLEScanner.stopScan(mScanCallback);
            mIsScanning = false;
            if (!mIsPeriodicScanning){
                mActivity.bleNotify(BLEEvent.scanStopped); // Only notify for one-off scans
            }
        }
    }

    /**
     * Scan for BLE devices at fixed intervals
     */
    public void bleStartPeriodicScan(final long scanPeriod, final long waitPeriod){
        if (!mIsPeriodicScanning){
            mIsPeriodicScanning = true;
            mPeriodicScanThread = new Thread(new Runnable(){
                @Override
                public void run() {
                    while (mIsPeriodicScanning){
                        if (mIsScanning){
                            bleStopScan();
                            try{
                                Thread.sleep(waitPeriod);
                            }catch(InterruptedException e){
                                return;
                            }
                        }else{
                            mBLEDevicesFound.clear();
                            bleStartScan();
                            try{
                                Thread.sleep(scanPeriod);
                            }catch(InterruptedException e){
                                return;
                            }
                        }
                    }
                }
            });
            mPeriodicScanThread.start();
        }
    }

    /**
     * Stop the periodic scan.
     */
    public void bleStopPeriodicScan(){
        if (mIsPeriodicScanning && mPeriodicScanThread != null){
            mPeriodicScanThread.interrupt();
            mPeriodicScanThread = null;
            mIsPeriodicScanning = false;
        }
    }

    /**
     * Advertise BLE signal packet
     */
    public void bleStartAdvertise(){
        // Check if multiple advertisement is supported && advertising is started
        if (mBluetoothAdapter.isMultipleAdvertisementSupported() && !mIsAdvertising) {
            // Initialize settings
            mBLEAdSettings = new AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                    .setConnectable(false).build();
            // Create ParcelUUID
            UUID uuid = UUID.randomUUID();
            ParcelUuid pUuid = new ParcelUuid(uuid);
            Toast.makeText(mActivity, uuid.toString(), Toast.LENGTH_SHORT).show();
            // Create advertisement data
            mBLEData = new AdvertiseData.Builder()
                    .setIncludeDeviceName(false)
                    .addServiceUuid(pUuid)
                    .addServiceData(pUuid, "Data".getBytes(Charset.forName("UTF-8")))
                    .build();
        }else{
            Toast.makeText(mActivity, "BLE multiple advertising not supported", Toast.LENGTH_SHORT)
                    .show();
        }
    }
    /**
     * Advertise callback for startAdvertise and stopAdvertise
     */
    private final AdvertiseCallback mAdvertiseCallback = new AdvertiseCallback(){
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect){
            super.onStartSuccess(settingsInEffect);
            Log.d(LOG_TAG, "AdvertiseCallback onStartSuccess");
            mActivity.bleNotify(BLEEvent.adStarted);
        }

        @Override
        public void onStartFailure(int errorCode){
            super.onStartFailure(errorCode);
            Log.d(LOG_TAG, "Advertising onStartFailure: " + errorCode);
            mActivity.bleNotify(BLEEvent.adFailed);
        }
    };

    /**
     * Stop advertising
     */
    public void bleStopAdvertise(){
        if (mIsAdvertising){
            mBLEAdvertiser.stopAdvertising(mAdvertiseCallback);
            mIsAdvertising = false;
            mActivity.bleNotify(BLEEvent.adStopped);
        }
    }

    /**
     * Connect to a Bluetooth GATT server
     */
    public void bleConnectToServer(BluetoothDevice serverDevice){
        if (serverDevice != null){
            mBluetoothGatt = serverDevice.connectGatt(mActivity, false, mBluetoothGattCallback);
        }
    }
    /**
     * BluetoothGATT connection callback
     */
    private final BluetoothGattCallback mBluetoothGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorRead(gatt, descriptor, status);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorWrite(gatt, descriptor, status);
        }

        @Override
        public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
            super.onReliableWriteCompleted(gatt, status);
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            super.onReadRemoteRssi(gatt, rssi, status);
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            super.onMtuChanged(gatt, mtu, status);
        }
    };

    /**
     * Getter for mBLEDevicesFound (list of BLE devices found)
     */
    public ArrayList<BluetoothDevice> getBLEDevicesFound(){
        return mBLEDevicesFound;
    }
}
