package com.vsdc.sensordemo;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2) //This activity works for API level 18 and above only
public class BLEActivity extends Activity {
    private static final String LOG_TAG = BLEActivity.class.getSimpleName();
    private static final long SCAN_PERIOD = 10000; // Stops scanning after 10 seconds
    private static final int PERMISSION_COARSE_LOCATION = 1;

    private DeviceListAdapter mBLEDeviceListAdapter;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner mBLEScanner;
    private int REQUEST_ENABLE_BT = 1; // Request code for Intent to enable Bluetooth

    private boolean mScanning; // Whether the app is scanning for BLE devices
    private Handler mHandler;
    private Button mBtnScan;

    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ble);
        mHandler = new Handler();

        // Get Bluetooth Adapter
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
        // Check if Bluetooth is enabled
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            // Prompt user to enable Bluetooth if not enabled
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
        // Get BLE Scanner
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mBLEScanner = mBluetoothAdapter.getBluetoothLeScanner();
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
        // Set button listener
        mBtnScan = (Button) findViewById(R.id.btn_ble_scan);
        mBtnScan.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view){
                // Scan for devices
                if (!mScanning){ // If not currently scanning
                    // Schedule scan to stop after SCAN_PERIOD
                    mHandler.postDelayed(new Runnable(){
                        @Override
                        public void run(){
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP){
                                mBLEScanner.stopScan(mScanCallback);
                            }else{
                                mBluetoothAdapter.stopLeScan(mBLEScanCallback);
                            }
                            mScanning = false;
                            mBtnScan.setText(getString(R.string.btn_text_ble_scan));
                        }
                    }, SCAN_PERIOD);
                    // Start scan
                    mScanning = true;
                    mBtnScan.setText(getString(R.string.btn_text_ble_scanning));
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP){
                        mBLEScanner.startScan(mScanCallback);
                    }else{
                        mBluetoothAdapter.startLeScan(mBLEScanCallback);
                    }
                }
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data){
        if (requestCode == REQUEST_ENABLE_BT && resultCode != RESULT_OK){
            // If user doesn't enable Bluetooth
            // Alert the user
            Toast.makeText(this, R.string.ble_enable_fail, Toast.LENGTH_SHORT).show();
            // Return to the previous activity
            finish();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results){
        if (requestCode == PERMISSION_COARSE_LOCATION){
            if (results[0] == PackageManager.PERMISSION_GRANTED){
                Log.d(LOG_TAG, "Coarse location permission granted.");
                mBtnScan.setEnabled(true);
            }else{
                Log.d(LOG_TAG, "Coarse location permission denied.");
                Toast.makeText(this, R.string.ble_permission_denied, Toast.LENGTH_SHORT).show();
                mBtnScan.setEnabled(false);
            }
        }
    }

    private BluetoothAdapter.LeScanCallback mBLEScanCallback =
            new BluetoothAdapter.LeScanCallback(){
        @Override
        public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
            Toast.makeText(getApplicationContext(), "Found a device!", Toast.LENGTH_SHORT)
                    .show();
            mBLEDeviceListAdapter.addDevice(device);
            mBLEDeviceListAdapter.notifyDataSetChanged();
        }
    };

    @SuppressLint("NewApi")
    private ScanCallback mScanCallback = new ScanCallback(){
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            BluetoothDevice device = result.getDevice();
            // Alert device name, address and rssi
            StringBuffer info = new StringBuffer();
            info.append(device.getName() + "\n");
            info.append(device.getAddress() + "\n");
            info.append(result.getRssi());
            Toast.makeText(getApplicationContext(), info.toString(), Toast.LENGTH_SHORT).show();
            mBLEDeviceListAdapter.addDevice(result.getDevice());
            mBLEDeviceListAdapter.notifyDataSetChanged();
        }
        @Override
        public void onScanFailed(int errorCode){
            Toast.makeText(getApplicationContext(), "Scan failed!", Toast.LENGTH_SHORT).show();
        }
    };

    // Customised list adapter (BaseAdapter) for list of BLE devices found.
    private class DeviceListAdapter extends BaseAdapter{
        private ArrayList<BluetoothDevice> mBLEDevices;

        public DeviceListAdapter(){
            super();
            mBLEDevices = new ArrayList<BluetoothDevice>();
        }
        @Override
        public int getCount(){
            return mBLEDevices.size();
        }
        @Override
        public Object getItem(int position) {
            return mBLEDevices.get(position);
        }
        @Override
        public long getItemId(int i) {
            return i;
        }
        @Override
        public View getView(int position, View view, ViewGroup viewGroup) {
            if (view == null){
                view = getLayoutInflater().inflate(R.layout.list_item_devices, null);
            }
            // Get handles to text views
            TextView mTextDeviceName = (TextView) view.findViewById(R.id.textview_device_name);
            TextView mTextDeviceAddress = (TextView) view.findViewById(R.id.textview_device_address);
            // Get device selected and set information
            BluetoothDevice device = mBLEDevices.get(position);
            final String deviceName = device.getName();
            if (deviceName != null && deviceName.length() > 0){
                mTextDeviceName.setText(deviceName);
            }else{
                mTextDeviceName.setText(R.string.ble_unknown_device);
            }
            mTextDeviceAddress.setText(device.getAddress());
            return null;
        }
        public void addDevice(BluetoothDevice device){
            // Add found device to list
            if (!mBLEDevices.contains(device)){
                mBLEDevices.add(device);
            }
        }
    }
}