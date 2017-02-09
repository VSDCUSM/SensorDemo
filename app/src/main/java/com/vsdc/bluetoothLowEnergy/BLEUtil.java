package com.vsdc.bluetoothLowEnergy;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.ScanResult;
import android.os.ParcelUuid;
import android.os.SystemClock;
import android.util.Log;

import java.nio.charset.Charset;

/**
 * Utility class for BLE frameworks.
 */
public final class BLEUtil{
    static final ParcelUuid PARCEL_UUID = ParcelUuid.fromString("0000b81d-0000-1000-8000-00805f9b34fb");

    static BluetoothAdapter bluetoothAdapter;
    static boolean isActivityActive = false;

    /**
     * Extract the timestamp in the specified scan result and convert it into its corresponding system timestamp
     * (millisecond) (as will be returned by System.currentTimeMillis()).
     *
     * @param scanResult The specified scan result from which the timestamp will be extracted
     * @return The corresponding system timestamp (millisecond)
     */
    public static long extractSystemTimestampMillis(ScanResult scanResult){
        return System.currentTimeMillis() - SystemClock.elapsedRealtime() + scanResult.getTimestampNanos() / 1000000;
    }
    /**
     * Extract the advertised name of the BLE device (associated with PARCEL_UUID) from the specified scan result.
     *
     * @param scanResult The specified scan result for the BLE device
     * @return The advertised name of the BLE device, empty string if the BLE device does not have an advertised name
     */
    public static String extractAdvertisedName(ScanResult scanResult){
        try{
            byte[] advertisedNameBytes = scanResult.getScanRecord().getServiceData(BLEUtil.PARCEL_UUID);
            if (advertisedNameBytes == null){
                return "";
            }
            String advertisedName = new String(advertisedNameBytes, Charset.forName("ASCII"));
            if (advertisedName.matches("[A-Za-z0-9]{3}")){
                return advertisedName;
            }else{
                return "";
            }
        }catch (NullPointerException e){
            e.printStackTrace();
            return "";
        }
    }
    /**
     * Estimate the distance of this phone from the device contained in the specified scan result.
     * Return -1.0 if the distance cannot be estimated (due to the lack of scan record or transmission power).
     *
     * @param scanResult The scan result containing the device whose distance from this phone is to be estimated
     * @return The estimated distance (in meters), -1.0 if the distance cannot be estimated
     */
    public static double estimateDistance(ScanResult scanResult){
        if (scanResult.getScanRecord() == null){
            return -1.0;
        }
        final int TX_POWER = scanResult.getScanRecord().getTxPowerLevel(), RSSI = scanResult.getRssi();
        if (TX_POWER == Integer.MIN_VALUE){ // The transmission power level cannot be obtained
            return -1.0;
        }
        // Calculate using ITU model for indoor attenuation
        // (refer to https://en.wikipedia.org/wiki/ITU_model_for_indoor_attenuation)
        return Math.pow(10, (TX_POWER - RSSI + 28 - 15 - 20 * Math.log10(2450)) / 30.0);
    }
}