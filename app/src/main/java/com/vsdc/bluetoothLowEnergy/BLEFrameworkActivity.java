package com.vsdc.bluetoothLowEnergy;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;
import android.widget.Toast;

/**
 * Activity class that contains the framework for BLE related functions (scanning and advertising).
 */
public class BLEFrameworkActivity extends Activity{
    private static final String LOG_TAG = BLEFrameworkActivity.class.getSimpleName();

    private static final int PERMISSION_COARSE_LOCATION = 1;
    private static final int REQUEST_ENABLE_BLUETOOTH = 1;
    private static Intent scanServiceIntent, advertiseServiceIntent, enableBluetoothIntent;

    @Override
    protected void onCreate(Bundle savedInstanceState){
        Log.d(LOG_TAG, "onCreate()");
        super.onCreate(savedInstanceState);
        scanServiceIntent = new Intent(this, BLEScanService.class);
        advertiseServiceIntent = new Intent(this, BLEAdvertiseService.class);
        enableBluetoothIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        // Request runtime permission for Android Marshmallow and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED){
                Toast.makeText(this, R.string.ble_permission_request, Toast.LENGTH_SHORT).show();
                requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                        PERMISSION_COARSE_LOCATION);
            }
        }
        // Initialise Bluetooth Adapter
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        BLEUtil.bluetoothAdapter = bluetoothManager.getAdapter();
        if (BLEUtil.bluetoothAdapter == null){
            Log.d(LOG_TAG, "Bluetooth adapter returns null.");
        }
    }
    @Override
    protected void onResume(){
        super.onResume();
        Log.d(LOG_TAG, "onResume()");
        BLEUtil.isActivityActive = true;
        bleRetryWaitingTask();
    }
    @Override
    protected void onPause(){
        super.onPause();
        Log.d(LOG_TAG, "onPause()");
        BLEUtil.isActivityActive = false;
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] results){
        if (requestCode == PERMISSION_COARSE_LOCATION){
            if (results[0] == PackageManager.PERMISSION_GRANTED){
                Log.d(LOG_TAG, "Coarse location permission granted.");
                final BluetoothManager bluetoothManager =
                        (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
                BLEUtil.bluetoothAdapter = bluetoothManager.getAdapter();
                if (BLEUtil.bluetoothAdapter == null){
                    Log.d(LOG_TAG, "ERROR: BluetoothAdapter is still null after permission is granted!");
                }
            }else{
                Log.d(LOG_TAG, "Coarse location permission denied.");
                // If user doesn't allow coarse location permission, alert the user
                Toast.makeText(this, R.string.ble_permission_denied, Toast.LENGTH_SHORT).show();
                // End this activity and stop all services
                stopService(scanServiceIntent);
                stopService(advertiseServiceIntent);
                finish();
            }
        }
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data){
        Log.d(LOG_TAG, "onActivityResult(): resultCode = " + resultCode + ", intent = " + data);
        if (requestCode == REQUEST_ENABLE_BLUETOOTH){
            if (resultCode == RESULT_OK){
                // Start any waiting BLE scan and/or advertisement
                if (BLEScanService.getScanStatus() == BLEScanService.ScanStatus.waiting){
                    startService(scanServiceIntent);
                }
                if (BLEAdvertiseService.getAdStatus() == BLEAdvertiseService.AdStatus.waiting){
                    startService(advertiseServiceIntent);
                }
            }else{
                // If user doesn't enable Bluetooth, alert the user
                Toast.makeText(this, R.string.ble_enable_fail, Toast.LENGTH_SHORT).show();
                // Stop any ongoing scan or advertisement
                bleStopScan();
                bleStopAdvertise();
                // Stop waiting
                BLEScanService.stopWaiting();
                BLEAdvertiseService.stopWaiting();
            }
        }
    }

    /**
     * Scan for BLE devices and display the result on the list.
     */
    public synchronized void bleStartScan(){
        Log.d(LOG_TAG, "Start scan");
        // Check if bluetooth is enabled
        if (BLEUtil.bluetoothAdapter.isEnabled()){
            // Check if scan service is running
            if (BLEScanService.getInstance() == null){
                // Start scan if service is not already running
                startService(scanServiceIntent);
            }else{
                Log.d(LOG_TAG, "ERROR: Scan service is already started!");
            }
        }else{
            // Request for bluetooth to be enabled
            BLEScanService.startWaiting();
            startActivityForResult(enableBluetoothIntent, REQUEST_ENABLE_BLUETOOTH);
        }
    }
    /**
     * Stop any ongoing scan including wait for scan.
     */
    public synchronized void bleStopScan(){
        Log.d(LOG_TAG, "Stop scan");
        BLEScanService.stopWaiting();
        stopService(scanServiceIntent);
    }
    /**
     * Advertise BLE signal packet.
     */
    public synchronized void bleStartAdvertise(){
        Log.d(LOG_TAG, "Start advertise");
        // Check if Bluetooth is enabled
        if (BLEUtil.bluetoothAdapter.isEnabled()){
            // Check if advertisement service is running
            if (BLEAdvertiseService.getInstance() == null){
                // Start advertising with the latest set advertised name
                startService(advertiseServiceIntent);
            }else{
                Log.d(LOG_TAG, "ERROR: Advertisement service is already started!");
            }
        }else{
            BLEAdvertiseService.startWaiting();
            startActivityForResult(enableBluetoothIntent, REQUEST_ENABLE_BLUETOOTH);
        }
    }
    /**
     * Stop any ongoing advertisement including wait for advertisement.
     */
    public synchronized void bleStopAdvertise(){
        Log.d(LOG_TAG, "Stop advertise");
        BLEAdvertiseService.stopWaiting();
        stopService(advertiseServiceIntent);
    }

    /**
     * Try to restart any waiting scan or advertisement due to Bluetooth being turned off by requesting for Bluetooth.
     */
    public synchronized void bleRetryWaitingTask(){
        // Check if there is any waiting scan or advertisement
        if (BLEScanService.getScanStatus() == BLEScanService.ScanStatus.waiting
                || BLEAdvertiseService.getAdStatus() == BLEAdvertiseService.AdStatus.waiting){
            if (BLEUtil.bluetoothAdapter.isEnabled()){
                Log.d(LOG_TAG, "ERROR: Scan or advertisement is still waiting although Bluetooth is enabled!");
            }else{
                // Request for Bluetooth to be enabled
                startActivityForResult(enableBluetoothIntent, REQUEST_ENABLE_BLUETOOTH);
            }
        }
    }
}