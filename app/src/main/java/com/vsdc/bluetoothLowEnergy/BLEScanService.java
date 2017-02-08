package com.vsdc.bluetoothLowEnergy;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.ListIterator;

/**
 * Background BLE scanning service.
 */
public class BLEScanService extends Service{
    private static final String LOG_TAG = BLEScanService.class.getSimpleName();

    public enum ScanStatus{
        stopped, waiting, scanning
    }
    public enum ScanEvent{
        scanStatusUpdated, scanFailed, scanServiceStopped, resultAdded, resultUpdated, resultRemoved
    }
    public enum TrackingEvent{
        newDeviceDetected, trackedDeviceReconnected, trackedDeviceDisconnected
    }
    public interface BLEScanCallback{
        /**
         * Callback for BLE scan events.
         *
         * @param context The context from which this method is called (this service)
         * @param scanEvent Type of BLE scan event that occurs
         */
        void bleNotify(Context context, ScanEvent scanEvent);
        /**
         * Callback for BLE device tracking-related events.
         *
         * @param context The context from which this method is called (this service)
         * @param trackingEvent Type of BLE device tracking-related event that occurs
         * @param scanResult The scan result corresponding to a tracked / new BLE device
         */
        void bleNotifyResult(Context context, TrackingEvent trackingEvent, ScanResult scanResult);
    }

    private static final BLEScanCallback EMPTY_BLE_SCAN_CALLBACK = new BLEScanCallback(){
        @Override
        public void bleNotify(Context context, ScanEvent scanEvent){
            // Purposely left blank
        }
        @Override
        public void bleNotifyResult(Context context, TrackingEvent trackingEvent, ScanResult scanResult){
            // Purposely left blank
        }
    };

    // Minimum number of milliseconds since a device was last detected before that device is considered disconnected
    private static long disconnectionThreshold = 8000;

    private static BLEScanService instance;
    private static ScanStatus scanStatus = ScanStatus.stopped;
    private static ArrayList<String> trackedDevices = new ArrayList<>(), ignoredDevices = new ArrayList<>();
    private static ArrayList<ScanResult> scanResults = new ArrayList<>();
    private static BLEScanCallback bleScanCallback = EMPTY_BLE_SCAN_CALLBACK;

    private Handler mHandler;
    private final Runnable CHECK_DISCONNECTION_RUNNABLE = new Runnable(){
        @Override
        public void run(){
            ListIterator<ScanResult> scanResultIterator = scanResults.listIterator();
            while (scanResultIterator.hasNext()){
                ScanResult scanResult = scanResultIterator.next();
                if (System.currentTimeMillis() - BLEUtil.extractSystemTimestampMillis(scanResult) >
                        disconnectionThreshold){
                    scanResultIterator.remove();
                    if (trackedDevices.contains(BLEUtil.extractAdvertisedName(scanResult))){
                        // If the device is a tracked device
                        bleScanCallback.bleNotifyResult(instance, TrackingEvent.trackedDeviceDisconnected, scanResult);
                    }else{
                        bleScanCallback.bleNotify(instance, ScanEvent.resultRemoved);
                    }
                }
            }
            // Check for disconnection periodically
            mHandler.postDelayed(CHECK_DISCONNECTION_RUNNABLE, disconnectionThreshold / 2);
        }
    };

    // Scan callback for startScan and stopScan.
    private final ScanCallback SCAN_CALLBACK = new ScanCallback(){
        @Override
        public void onScanResult(int callbackType, ScanResult newScanResult){
            super.onScanResult(callbackType, newScanResult);
            Log.d(LOG_TAG, "onScanResult()");
            boolean isExisting = false;
            // Process existing scan results
            ListIterator<ScanResult> scanResultIterator = scanResults.listIterator();
            while (scanResultIterator.hasNext()){
                ScanResult existingScanResult = scanResultIterator.next();
                String existingAdvertisedName = BLEUtil.extractAdvertisedName(existingScanResult);
                // Merge with corresponding existing device
                if (existingScanResult.getDevice().equals(newScanResult.getDevice()) ||
                        (!existingAdvertisedName.equals("")
                        && existingAdvertisedName.equals(BLEUtil.extractAdvertisedName(newScanResult)))){
                    scanResultIterator.set(newScanResult);
                    bleScanCallback.bleNotify(instance, ScanEvent.resultUpdated);
                    isExisting = true;
                }else if (System.currentTimeMillis() - BLEUtil.extractSystemTimestampMillis(existingScanResult) >
                        disconnectionThreshold){ // Check if other devices are disconnected
                    scanResultIterator.remove();
                    if (trackedDevices.contains(existingAdvertisedName)){
                        // If the device is a tracked device
                        bleScanCallback.bleNotifyResult(instance, TrackingEvent.trackedDeviceDisconnected,
                                existingScanResult);
                    }else{
                        bleScanCallback.bleNotify(instance, ScanEvent.resultRemoved);
                    }
                }
            }
            // Add new scan result
            if (!isExisting){
                scanResults.add(newScanResult);
                if (trackedDevices.contains(BLEUtil.extractAdvertisedName(newScanResult))){
                    // If the device is a tracked device
                    bleScanCallback.bleNotifyResult(instance, TrackingEvent.trackedDeviceReconnected, newScanResult);
                }else if (!ignoredDevices.contains(BLEUtil.extractAdvertisedName(newScanResult))){
                    // Otherwise if the device is a newly discovered device
                    bleScanCallback.bleNotifyResult(instance, TrackingEvent.newDeviceDetected, newScanResult);
                }else{
                    bleScanCallback.bleNotify(instance, ScanEvent.resultAdded);
                }
            }
        }
        @Override
        public void onScanFailed(int errorCode){
            super.onScanFailed(errorCode);
            Log.d(LOG_TAG, "Scan failure: " + errorCode);
            Toast.makeText(getApplicationContext(), "Scan failed: " + errorCode, Toast.LENGTH_SHORT).show(); //////////
            bleScanCallback.bleNotify(instance, ScanEvent.scanFailed);
            if (scanStatus != ScanStatus.stopped){ // Normally scan status should be scanning
                scanStatus = ScanStatus.stopped;
                bleScanCallback.bleNotify(instance, ScanEvent.scanStatusUpdated);
            }
            stopSelf();
        }
    };

    // Broadcast receiver to sense bluetooth state change (enable / disable).
    private final BroadcastReceiver RECEIVER = new BroadcastReceiver(){
        @Override
        public void onReceive(Context context, Intent intent){
            if (intent.getAction().equals(BluetoothAdapter.ACTION_STATE_CHANGED)){
                switch (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)){
                    case BluetoothAdapter.STATE_ON:
                        Log.d(LOG_TAG, "BroadcastReceiver: Bluetooth is on (debug only)");
                        break;
                    case BluetoothAdapter.STATE_OFF:
                        Log.d(LOG_TAG, "BroadcastReceiver: Bluetooth is off");
                        // Change active scan to waiting state for restart later
                        if (scanStatus == ScanStatus.scanning){
                            scanStatus = ScanStatus.waiting;
                            bleScanCallback.bleNotify(instance, ScanEvent.scanStatusUpdated);
                        }
                        // Stop scanning
                        stopSelf();
                        break;
                }
            }
        }
    };

    @Override
    public void onCreate(){
        super.onCreate();
        Log.d(LOG_TAG, "onCreate()");
        if (instance == null){
            instance = this;
            registerReceiver(RECEIVER, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
            if (BLEUtil.bluetoothAdapter.isEnabled()){
                Log.d(LOG_TAG, "onCreate() -> Start scanning");
                BLEUtil.bluetoothAdapter.getBluetoothLeScanner().startScan(SCAN_CALLBACK);
                scanStatus = ScanStatus.scanning;
                bleScanCallback.bleNotify(this, ScanEvent.scanStatusUpdated);
                // Start up the background thread running the service with background priority to avoid disrupting UI
                HandlerThread backgroundThread = new HandlerThread(getClass().getName(),
                        android.os.Process.THREAD_PRIORITY_BACKGROUND);
                backgroundThread.start();
                // Initialise the handler using the looper of this background thread
                mHandler = new Handler(backgroundThread.getLooper());
                // Check for disconnection periodically
                mHandler.postDelayed(CHECK_DISCONNECTION_RUNNABLE, disconnectionThreshold / 2);
            }else{
                Log.d(LOG_TAG, "ERROR: Service started with Bluetooth disabled");
                stopSelf();
            }
        }else{
            Log.d(LOG_TAG, "ERROR: Duplicate service!");
            stopSelf();
        }
    }
    @Override
    public void onDestroy(){
        super.onDestroy();
        Log.d(LOG_TAG, "onDestroy()");
        if (instance == this){
            bleScanCallback.bleNotify(this, ScanEvent.scanServiceStopped);
            // Stop any active scan (do not change waiting status)
            if (scanStatus == ScanStatus.scanning){
                BLEUtil.bluetoothAdapter.getBluetoothLeScanner().stopScan(SCAN_CALLBACK);
                scanStatus = ScanStatus.stopped;
                bleScanCallback.bleNotify(this, ScanEvent.scanStatusUpdated);
            }
            // Clear any existing scan results (no notification will be issued)
            scanResults.clear();
            instance = null;
            unregisterReceiver(RECEIVER);
        }else{
            Log.d(LOG_TAG, "ERROR: Instance not found!");
        }
    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId){
        Log.d(LOG_TAG, "onStartCommand() (debug only)");
        return START_STICKY; // Restart this service if the system kills this service after this method returns
    }
    @Override
    public IBinder onBind(Intent intent){
        return null; // No binding
    }

    /**
     * Start waiting for continuous scan. Allow only change from stopped to waiting status.
     */
    public static void startWaiting(){
        if (scanStatus == ScanStatus.stopped){
            scanStatus = ScanStatus.waiting;
            bleScanCallback.bleNotify(instance, ScanEvent.scanStatusUpdated);
        }
    }
    /**
     * Stop waiting for any scan. Allow only change from waiting to stopped status.
     */
    public static void stopWaiting(){
        if (scanStatus == ScanStatus.waiting){
            scanStatus = ScanStatus.stopped;
            bleScanCallback.bleNotify(instance, ScanEvent.scanStatusUpdated);
        }
    }

    /**
     * Add the specified BLE device to the list of tracked BLE devices. The scan result must have its advertised
     * name (used for tracking) associated with PARCEL_UUID.
     *
     * @param scanResult The scan result containing the specified BLE device
     */
    public static void trackDevice(ScanResult scanResult){
        String advertisedName = BLEUtil.extractAdvertisedName(scanResult);
        if (advertisedName == null){
            Log.e(LOG_TAG, "trackDevice() only works with BLE device that have its advertised name associated with " +
                    "PARCEL_UUID");
        }else if (!trackedDevices.contains(advertisedName)){
            trackedDevices.add(advertisedName);
            if (instance != null){
                Toast.makeText(instance, R.string.ble_track_device, Toast.LENGTH_SHORT).show();
            }
        }
    }
    /**
     * Add the specified BLE device to the list of ignored BLE devices. The scan result must have its advertised
     * name (used for identification) associated with PARCEL_UUID.
     *
     * @param scanResult The scan result containing the specified BLE device
     */
    public static void ignoreDevice(ScanResult scanResult){
        String advertisedName = BLEUtil.extractAdvertisedName(scanResult);
        if (advertisedName == null){
            Log.e(LOG_TAG, "ignoreDevice() only works with BLE device that have its advertised name associated with " +
                    "PARCEL_UUID");
        }else if (!ignoredDevices.contains(advertisedName)){
            ignoredDevices.add(advertisedName);
            if (instance != null){
                Toast.makeText(instance, R.string.ble_ignore_device, Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * Register the specified BLE scan callback to this service so that it is invoked when a BLE scan event occurs.
     * If no BLE scan callback is specified (null), any existing BLE scan callback will be unregistered.
     *
     * @param bleScanCallback The BLE callback to be invoked (null to unregister any existing BLE scan callback)
     */
    public static void setBLEScanCallback(BLEScanCallback bleScanCallback){
        if (bleScanCallback == null){
            BLEScanService.bleScanCallback = EMPTY_BLE_SCAN_CALLBACK;
        }else{
            BLEScanService.bleScanCallback = bleScanCallback;
        }
    }

    // Getters
    public static BLEScanService getInstance(){
        return instance;
    }
    public static ScanStatus getScanStatus(){
        return scanStatus;
    }
    public static ArrayList<ScanResult> getScanResults(){
        return scanResults;
    }
}
