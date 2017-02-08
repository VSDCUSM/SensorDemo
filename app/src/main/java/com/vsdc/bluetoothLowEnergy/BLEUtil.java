package com.vsdc.bluetoothLowEnergy;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.ScanResult;
import android.os.ParcelUuid;
import android.os.SystemClock;

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
}