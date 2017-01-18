package com.vsdc.bluetoothLowEnergy;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelUuid;
import android.support.annotation.NonNull;
import android.util.Log;
import android.widget.Toast;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.UUID;

/**
 * Activity class that contains the framework for BLE related functions (scanning, advertising, connecting).
 */
public class BLEFrameworkActivity extends Activity{
    private static final String LOG_TAG = BLEFrameworkActivity.class.getSimpleName();

    private static final int PERMISSION_COARSE_LOCATION = 1;
    private static final int REQUEST_ENABLE_BLUETOOTH = 1;
    private static final ParcelUuid PARCEL_UUID = ParcelUuid.fromString("0000b81d-0000-1000-8000-00805f9b34fb");

    public enum BLEEvent{
        scanStatusUpdated, scanFoundNew, scanUpdatedOld, scanFailed,
        adStatusUpdated, adFailed
    }
    public enum ScanStatus{
        stopped, waitForContinuous, waitForPeriodic, continuousScan, periodicScan, periodicWait
    }
    public enum AdStatus{
        stopped, waiting, advertising
    }

    protected AdvertiseSettings mBLEAdSettings = new AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(false)
            .build();
    protected AdvertiseData mBLEData = new AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(true)
            .addServiceUuid(PARCEL_UUID)
            .addServiceData(PARCEL_UUID, "Data".getBytes(Charset.forName("ASCII")))
            .build();

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner mBLEScanner;
    private BluetoothLeAdvertiser mBLEAdvertiser;

    private boolean mIsBluetoothEnabled = false;
    private ScanStatus mScanStatus = ScanStatus.stopped;
    private AdStatus mAdStatus = AdStatus.stopped;
    private ArrayList<ScanResult> mScanResults = new ArrayList<>();

    public synchronized void debug(){ //////////////////////////////////////////////////////////////////////////////////
        Log.d(LOG_TAG, "  mBluetoothAdapter = " + mBluetoothAdapter + ", mBLEScanner = " + mBLEScanner
                + ", mBLEAdvertiser = " + mBLEAdvertiser + "\n  mIsBluetoothEnabled = " + mIsBluetoothEnabled
                + ", mScanStatus = " + mScanStatus + ", mAdStatus = " + mAdStatus);
    }

    private final Handler HANDLER = new Handler();
    private long mScanDuration = 10000; // Scanning duration in millisecond
    private long mWaitDuration = 5000; // Waiting duration in millisecond
    // Runnables for periodic scan
    private final Runnable PERIODIC_START_RUNNABLE = new Runnable(){
        @Override
        public void run(){
            synchronized (HANDLER){
                if (mIsBluetoothEnabled && mBLEScanner != null){
                    // Start scan
                    mBLEScanner.startScan(SCAN_CALLBACK);
                    mScanStatus = ScanStatus.periodicScan;
                    bleNotify(BLEEvent.scanStatusUpdated);
                    HANDLER.postDelayed(PERIODIC_STOP_RUNNABLE, mScanDuration);
                }
            }
        }
    };
    private final Runnable PERIODIC_STOP_RUNNABLE = new Runnable(){
        @Override
        public void run(){
            synchronized (HANDLER){
                if (mIsBluetoothEnabled && mBLEScanner != null){
                    // Stop scan
                    mBLEScanner.stopScan(SCAN_CALLBACK);
                    mScanStatus = ScanStatus.periodicWait;
                    bleNotify(BLEEvent.scanStatusUpdated);
                    HANDLER.postDelayed(PERIODIC_START_RUNNABLE, mWaitDuration);
                }
            }
        }
    };

    // Scan callback for startScan and stopScan.
    private final ScanCallback SCAN_CALLBACK = new ScanCallback(){
        @Override
        public void onScanResult(int callbackType, ScanResult result){
            super.onScanResult(callbackType, result);
            // Update scan results
            for (int i = 0; i < mScanResults.size(); i++){
                // Merge with existing device
                if (mScanResults.get(i).getDevice().equals(result.getDevice())){
                    mScanResults.set(i, result);
                    bleNotify(BLEEvent.scanUpdatedOld);
                    return;
                }
            }
            mScanResults.add(result);
            bleNotify(BLEEvent.scanFoundNew);
        }
        @Override
        public void onScanFailed(int errorCode){
            super.onScanFailed(errorCode);
            Log.d(LOG_TAG, "Scan failure: " + errorCode);
            Toast.makeText(getApplication(), "Scan failed: " + errorCode, Toast.LENGTH_SHORT).show(); ////////////////
            bleNotify(BLEEvent.scanFailed);
            bleStopScan();
        }
    };
    /**
     * Advertise callback for startAdvertise and stopAdvertise
     */
    private final AdvertiseCallback mAdvertiseCallback = new AdvertiseCallback(){
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect){
            super.onStartSuccess(settingsInEffect);
            Log.d(LOG_TAG, "AdvertiseCallback onStartSuccess");
            mAdStatus = AdStatus.advertising;
            bleNotify(BLEEvent.adStatusUpdated);
        }
        @Override
        public void onStartFailure(int errorCode){
            super.onStartFailure(errorCode);
            Log.d(LOG_TAG, "Advertising onStartFailure: " + errorCode);
            Toast.makeText(getApplication(), "Advertisement failed: " + errorCode, Toast.LENGTH_SHORT).show(); /////////
            bleNotify(BLEEvent.adFailed);
            bleStopAdvertise();
        }
    };
    // Broadcast receiver to sense bluetooth state change (enable / disable).
    private final BroadcastReceiver RECEIVER = new BroadcastReceiver(){
        @Override
        public void onReceive(Context context, Intent intent){
            if (intent.getAction().equals(BluetoothAdapter.ACTION_STATE_CHANGED)){
                switch (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)){
                    case BluetoothAdapter.STATE_ON:
                        initBLE(); // Initialise BLE (start any waiting scan or advertisement)
                        // mIsBluetoothEnabled will be set to true in initBLE()
                        Log.d(LOG_TAG, "BroadcastReceiver: Bluetooth is on");
                        debug();
                        break;
                    case BluetoothAdapter.STATE_OFF:
                        mIsBluetoothEnabled = false;
                        // Change any active scan or advertisement to waiting state for restart later
                        if (mScanStatus == ScanStatus.continuousScan){
                            mScanStatus = ScanStatus.waitForContinuous;
                            bleNotify(BLEEvent.scanStatusUpdated);
                        }else if (mScanStatus == ScanStatus.periodicScan || mScanStatus == ScanStatus.periodicWait){
                            mScanStatus = ScanStatus.waitForPeriodic;
                            bleNotify(BLEEvent.scanStatusUpdated);
                        }
                        if (mAdStatus == AdStatus.advertising){
                            mAdStatus = AdStatus.waiting;
                            bleNotify(BLEEvent.adStatusUpdated);
                        }
                        Log.d(LOG_TAG, "BroadcastReceiver: Bluetooth is off");
                        debug();
                        // If any scan or advertisement is running and require Bluetooth
                        if (mScanStatus != ScanStatus.stopped || mAdStatus != AdStatus.stopped){
                            initBLE(); // Try to reinitialise BLE and request for Bluetooth
                        }
                        break;
                }
            }
        }
    };

    @Override
    protected void onResume(){
        super.onResume();
        registerReceiver(RECEIVER, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
        Log.d(LOG_TAG, "Register receiver");
        debug();
    }
    @Override
    protected void onPause(){
        super.onPause();
        unregisterReceiver(RECEIVER);
        Log.d(LOG_TAG, "Deregister receiver");
        debug();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        // Request runtime permission for Android Marshmallow and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED){
                Toast.makeText(this, R.string.ble_permission_request, Toast.LENGTH_SHORT).show();
                requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                        PERMISSION_COARSE_LOCATION);
            }
        }
        // Try to initialise Bluetooth Adapter so that Bluetooth Adapter state change listener works
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] results){
        if (requestCode == PERMISSION_COARSE_LOCATION){
            if (results[0] == PackageManager.PERMISSION_GRANTED){
                Log.d(LOG_TAG, "Coarse location permission granted.");
            }else{
                Log.d(LOG_TAG, "Coarse location permission denied.");
                // If user doesn't allow course location permission, alert the user
                Toast.makeText(this, R.string.ble_permission_denied, Toast.LENGTH_SHORT).show();
                // End this activity
                finish();
            }
        }
    }

    /**
     * Initialise BLE-related variables.
     */
    private synchronized void initBLE(){
        // Check if Bluetooth Adapter is already initialised to prevent duplicate work
        Log.d(LOG_TAG, "initBLE()");
        debug();
        if (!mIsBluetoothEnabled){
            // Try to get the Bluetooth Adapter
            final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            mBluetoothAdapter = bluetoothManager.getAdapter();
            Log.d(LOG_TAG, "initBLE() -> getAdapter(): mBluetoothAdapter = " + mBluetoothAdapter);
            // If successful
            if (mBluetoothAdapter != null && mBluetoothAdapter.isEnabled()){
                mIsBluetoothEnabled = true;
                // Initialise scanner and advertiser
                mBLEScanner = mBluetoothAdapter.getBluetoothLeScanner();
                mBLEAdvertiser = mBluetoothAdapter.getBluetoothLeAdvertiser();
                // Start any waiting scan
                if (mScanStatus == ScanStatus.waitForContinuous){
                    bleStartContinuousScan();
                }else if (mScanStatus == ScanStatus.waitForPeriodic){
                    bleStartPeriodicScan();
                }
                // Start waiting advertisement if any
                if (mAdStatus == AdStatus.waiting){
                    bleStartAdvertise();
                }
            }else{ // Otherwise if not successful
                mIsBluetoothEnabled = false;
                // Prompt user to enable Bluetooth if not enabled
                startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_ENABLE_BLUETOOTH);
            }
        }
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data){
        Log.d(LOG_TAG, "onActivityResult(): resultCode = " + resultCode + ", intent = " + data);
        if (requestCode == REQUEST_ENABLE_BLUETOOTH){
            if (resultCode == RESULT_OK){
                Log.d(LOG_TAG, "  RESULT_OK");
                initBLE();
            }else{
                // If user doesn't enable Bluetooth, alert the user
                Toast.makeText(this, R.string.ble_enable_fail, Toast.LENGTH_SHORT).show();
                // Stop any ongoing scan or advertisement
                bleStopScan();
                bleStopAdvertise();
                // Stop waiting
                if (mScanStatus != ScanStatus.stopped){
                    mScanStatus = ScanStatus.stopped;
                    bleNotify(BLEEvent.scanStatusUpdated);
                }
                if (mAdStatus != AdStatus.stopped){
                    mAdStatus = AdStatus.stopped;
                    bleNotify(BLEEvent.adStatusUpdated);
                }
            }
        }
    }

    /**
     * Scan for BLE devices and display the result on the list.
     */
    public synchronized void bleStartContinuousScan(){
        // Check if bluetooth is enabled
        if (mIsBluetoothEnabled && mBLEScanner != null){
            // Stop any ongoing scan
            bleStopScan();
            // Start scan
            mBLEScanner.startScan(SCAN_CALLBACK);
            mScanStatus = ScanStatus.continuousScan;
            bleNotify(BLEEvent.scanStatusUpdated);
        }else{
            mScanStatus = ScanStatus.waitForContinuous;
            bleNotify(BLEEvent.scanStatusUpdated);
            initBLE();
        }
    }
    /**
     * Scan for BLE devices at fixed intervals
     */
    public synchronized void bleStartPeriodicScan(){
        // Check if bluetooth is enabled
        if (mIsBluetoothEnabled && mBLEScanner != null){
            // Stop any ongoing scan
            bleStopScan();
            // Start periodic scan
            HANDLER.postDelayed(PERIODIC_START_RUNNABLE, 0);
        }else{
            mScanStatus = ScanStatus.waitForPeriodic;
            bleNotify(BLEEvent.scanStatusUpdated);
            initBLE();
        }
    }
    /**
     * Stop any ongoing scan (may stop waiting for scanning if bluetooth is enabled).
     */
    public synchronized void bleStopScan(){
        if (mIsBluetoothEnabled && mBLEScanner != null && mScanStatus != ScanStatus.stopped){
            if (mScanStatus == ScanStatus.continuousScan){
                mBLEScanner.stopScan(SCAN_CALLBACK);
            }else if (mScanStatus == ScanStatus.periodicScan){
                HANDLER.removeCallbacks(PERIODIC_STOP_RUNNABLE);
            }else if (mScanStatus == ScanStatus.periodicWait){
                HANDLER.removeCallbacks(PERIODIC_START_RUNNABLE);
            }
            mScanStatus = ScanStatus.stopped;
            bleNotify(BLEEvent.scanStatusUpdated);
        }
    }

    /**
     * Advertise BLE signal packet
     */
    public synchronized void bleStartAdvertise(){
        // Check if Bluetooth is enabled
        if (mIsBluetoothEnabled && mBLEAdvertiser != null){
            // Stop any ongoing advertisement
            bleStopAdvertise();
            // Start advertising
            mBLEAdvertiser.startAdvertising(mBLEAdSettings, mBLEData, mAdvertiseCallback);
            mAdStatus = AdStatus.advertising;
            Log.d(LOG_TAG, "Starting advertisement");
            // bleNotify() will be called in callback
        }else{
            mAdStatus = AdStatus.waiting;
            bleNotify(BLEEvent.adStatusUpdated);
            initBLE();
        }
    }
    /**
     * Stop advertising (may stop waiting for advertisement if bluetooth is enabled)
     */
    public synchronized void bleStopAdvertise(){
        if (mIsBluetoothEnabled && mBLEAdvertiser != null && mAdStatus == AdStatus.advertising){
            mBLEAdvertiser.stopAdvertising(mAdvertiseCallback);
            mAdStatus = AdStatus.stopped;
            bleNotify(BLEEvent.adStatusUpdated);
        }
    }

    /**
     * Callback for important BLE events (to be overridden optionally by its subclasses).
     *
     * @param bleEvent Type of BLE event
     */
    public synchronized void bleNotify(BLEEvent bleEvent){
    }

    /**
     * Getter for mScanResults (list of BLE devices found)
     */
    public ArrayList<ScanResult> getScanResults(){
        return mScanResults;
    }

    // Getters and setters for scan duration and wait duration for periodic scan
    public long getScanDuration(){
        return mScanDuration;
    }
    public void setScanDuration(long millis){
        if (millis > 0){
            mScanDuration = millis;
        }
    }
    public long getWaitDuration(){
        return mWaitDuration;
    }
    public void setWaitDuration(long millis){
        if (millis > 0){
            mWaitDuration = millis;
        }
    }

    // Getters for UUID and status
    public UUID getUUID(){
        return PARCEL_UUID.getUuid();
    }
    public ScanStatus getScanStatus(){
        return mScanStatus;
    }
    public AdStatus getAdStatus(){
        return mAdStatus;
    }
    public boolean isBluetoothEnabled(){
        return mIsBluetoothEnabled;
    }
}