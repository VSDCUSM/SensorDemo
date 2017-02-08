package com.vsdc.bluetoothLowEnergy;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import java.nio.charset.Charset;

/**
 * Background BLE advertisement service.
 */
public class BLEAdvertiseService extends Service{
    private static final String LOG_TAG = BLEAdvertiseService.class.getSimpleName();

    public enum AdStatus{
        stopped, waiting, advertising
    }
    public enum AdEvent{
        adStatusUpdated, adFailed, adServiceStopped
    }
    public interface BLEAdCallback{
        /**
         * Callback for important BLE advertisement events.
         *
         * @param context The context from which this method is called (this service)
         * @param adEvent Type of BLE advertisement event that occurs
         */
        void bleNotify(Context context, AdEvent adEvent);
    }

    private static final BLEAdCallback EMPTY_BLE_AD_CALLBACK = new BLEAdCallback(){
        @Override
        public void bleNotify(Context context, AdEvent adEvent){
            // Purposely left blank
        }
    };

    private static BLEAdvertiseService instance;
    private static AdStatus adStatus = AdStatus.stopped;
    private static BLEAdCallback bleAdCallback = EMPTY_BLE_AD_CALLBACK;

    private static AdvertiseSettings mBLEAdSettings = new AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(false)
            .build();
    private static AdvertiseData mBLEData = new AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(true)
            .addServiceUuid(BLEUtil.PARCEL_UUID)
            .addServiceData(BLEUtil.PARCEL_UUID, "New".getBytes(Charset.forName("ASCII")))
            .build();
    private static String mAdvertisedName = "New", mAdNameInUse = "New";

    /**
     * Advertise callback for startAdvertise and stopAdvertise
     */
    private final AdvertiseCallback mAdvertiseCallback = new AdvertiseCallback(){
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect){
            Log.d(LOG_TAG, "AdvertiseCallback onStartSuccess");
            adStatus = AdStatus.advertising;
            bleAdCallback.bleNotify(instance, AdEvent.adStatusUpdated);
        }
        @Override
        public void onStartFailure(int errorCode){
            Log.d(LOG_TAG, "Advertising onStartFailure: " + errorCode);
            Toast.makeText(getApplication(), "Advertisement failed: " + errorCode, Toast.LENGTH_SHORT).show(); /////////
            bleAdCallback.bleNotify(instance, AdEvent.adFailed);
            if (adStatus != AdStatus.stopped){ // Stop waiting
                adStatus = AdStatus.stopped;
                bleAdCallback.bleNotify(instance, AdEvent.adStatusUpdated);
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
                        // Change active advertisement to waiting state for restart later
                        if (adStatus == AdStatus.advertising){
                            adStatus = AdStatus.waiting;
                            bleAdCallback.bleNotify(instance, AdEvent.adStatusUpdated);
                        }
                        // Stop advertising
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
        // Only allow one instance of this service to be running (this service can only be started once)
        if (instance == null){
            instance = this;
            registerReceiver(RECEIVER, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
            if (BLEUtil.bluetoothAdapter.isEnabled()){
                Log.d(LOG_TAG, "onCreate() -> Start advertising");
                // Start advertising with the latest set advertised name
                updateAdvertisement();
                BLEUtil.bluetoothAdapter.getBluetoothLeAdvertiser()
                        .startAdvertising(mBLEAdSettings, mBLEData, mAdvertiseCallback);
                Log.d(LOG_TAG, "onCreate() -> Starting advertisement");
                // bleNotify() will be called in callback
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
            bleAdCallback.bleNotify(this, AdEvent.adServiceStopped);
            // Stop any active advertisement (do not change waiting status)
            if (adStatus == AdStatus.advertising){
                BLEUtil.bluetoothAdapter.getBluetoothLeAdvertiser().stopAdvertising(mAdvertiseCallback);
                adStatus = AdStatus.stopped;
                bleAdCallback.bleNotify(this, AdEvent.adStatusUpdated);
            }
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

    public static void startWaiting(){
        if (adStatus == AdStatus.stopped){
            adStatus = AdStatus.waiting;
            bleAdCallback.bleNotify(instance, AdEvent.adStatusUpdated);
        }
    }
    public static void stopWaiting(){
        if (adStatus == AdStatus.waiting){
            adStatus = AdStatus.stopped;
            bleAdCallback.bleNotify(instance, AdEvent.adStatusUpdated);
        }
    }

    /**
     * Set the advertised name for this device. This name is associated with PARCEL_UUID and will be displayed when
     * this device is discovered through a scan. This name will only be used starting from the next advertisement.
     *
     * @param advertisedName The advertised name for this device (format: 3 alphanumeric letters)
     * @return Whether the specified advertised name is acceptable or not
     */
    public static synchronized boolean setAdvertisedName(String advertisedName){
        if (advertisedName.matches("[A-Za-z0-9]{3}")){
            mAdvertisedName = advertisedName;
            return true;
        }
        return false;
    }
    // Getter for advertised name in use
    public static synchronized String getAdNameInUse(){
        return mAdNameInUse;
    }
    /**
     * Helper method to update the advertisement data to reflect the current advertised name.
     */
    private static synchronized void updateAdvertisement(){
        if (!mAdvertisedName.equals(mAdNameInUse)){
            mBLEData = new AdvertiseData.Builder()
                    .setIncludeDeviceName(false)
                    .setIncludeTxPowerLevel(true)
                    .addServiceUuid(BLEUtil.PARCEL_UUID)
                    .addServiceData(BLEUtil.PARCEL_UUID, mAdvertisedName.getBytes(Charset.forName("ASCII")))
                    .build();
            mAdNameInUse = mAdvertisedName;
        }
    }

    /**
     * Register the specified BLE advertisement callback to this service so it is invoked when a BLE advertisement
     * event occurs. If no BLE ad callback is specified (null), any existing BLE ad callback will be unregistered.
     *
     * @param bleAdCallback The BLE advertisement callback to be invoked (null to unregister exisiting BLE ad callback)
     */
    public static void setBLEAdCallback(BLEAdCallback bleAdCallback){
        if (bleAdCallback == null){
            BLEAdvertiseService.bleAdCallback = EMPTY_BLE_AD_CALLBACK;
        }else{
            BLEAdvertiseService.bleAdCallback = bleAdCallback;
        }
    }

    // Getters
    public static BLEAdvertiseService getInstance(){
        return instance;
    }
    public static AdStatus getAdStatus(){
        return adStatus;
    }
}