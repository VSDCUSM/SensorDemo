package com.vsdc.sensordemo;

import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;

public class BLEActivity extends BLEFramework.BLECoreActivity {
    private static final String LOG_TAG = BLEActivity.class.getSimpleName();
    private static final long SCAN_PERIOD = 10000; // Stops scanning after 10 seconds

    private BLEFramework mBleFramework;

    private DeviceListAdapter mBLEDeviceListAdapter;
    private Button mBtnScan;
    private Button mBtnAdvertise;
    private Button mBtnPeriodicScan;

    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ble);
        mBleFramework = new BLEFramework(this);

        // Set button listener
        mBtnScan = (Button) findViewById(R.id.btn_ble_scan);
        mBtnScan.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view){
                if (mBtnScan.getText().toString().equals(
                        getString(R.string.btn_text_ble_scanning))){
                    // Actively scanning
                    mBleFramework.bleStopScan();
                }else{
                    mBleFramework.bleStartScan();
                }
            }
        });
        mBtnAdvertise = (Button) findViewById(R.id.btn_ble_advertise);
        mBtnAdvertise.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view){
                if (mBtnAdvertise.getText().toString().equals(
                        getString(R.string.btn_text_ble_advertising))){
                    // Actively advertising
                    mBleFramework.bleStopAdvertise();
                    mBtnAdvertise.setText(R.string.btn_text_ble_advertise);
                }else{
                    mBleFramework.bleStartAdvertise();
                    mBtnAdvertise.setText(R.string.btn_text_ble_advertising);
                }
            }
        });
        mBtnPeriodicScan = (Button) findViewById(R.id.btn_ble_periodic_scan);
        mBtnPeriodicScan.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view){
                if (mBtnPeriodicScan.getText().toString().equals(
                        getString(R.string.btn_text_ble_periodic_scanning))){
                    // Periodic scan is active
                    mBleFramework.bleStopPeriodicScan();
                    mBtnPeriodicScan.setText(R.string.btn_text_ble_periodic_scan);
                    mBtnScan.setEnabled(true);
                }else{
                    mBleFramework.bleStartPeriodicScan(10000, 5000);
                    mBtnPeriodicScan.setText(R.string.btn_text_ble_periodic_scanning);
                    mBtnScan.setEnabled(false);
                }
            }
        });

        // Set adapter
        mBLEDeviceListAdapter = new DeviceListAdapter(mBleFramework.getBLEDevicesFound());
        ((ListView) findViewById(R.id.listview_devices)).setAdapter(mBLEDeviceListAdapter);
    }

    @Override
    protected void onPause(){
        super.onPause();
        // Stop scanning and advertising to avoid draining battery (MAY CAUSE PROBLEM FOR BACKGROUND SERVICE)
        mBleFramework.bleStopScan();
        mBleFramework.bleStopAdvertise();
        mBleFramework.bleStopPeriodicScan();
    }

    @Override
    public void bleNotify(BLEFramework.BLEEvent bleEvent){
        switch (bleEvent){
            case scanStarted:
                mBtnScan.setText(getString(R.string.btn_text_ble_scanning));
                break;
            case scanStopped:
                mBtnScan.setText(getString(R.string.btn_text_ble_scan));
                break;
            case scanFoundNew:
                mBLEDeviceListAdapter.updateDeviceList(mBleFramework.getBLEDevicesFound());
                mBLEDeviceListAdapter.notifyDataSetChanged();
                break;
            case scanFailed:
                /////
                break;
            case adStarted:
                mBtnAdvertise.setText(R.string.btn_text_ble_advertising);
                break;
            case adStopped:
                mBtnAdvertise.setText(R.string.btn_text_ble_advertise);
                break;
            case adFailed:
                /////
                break;
        }
    }


    /**
     * Customised list adapter (BaseAdapter) for list of BLE devices found.
     */
    private class DeviceListAdapter extends BaseAdapter{
        private ArrayList<BluetoothDevice> mBLEDevices;

        public DeviceListAdapter(ArrayList<BluetoothDevice> bleDevices){
            super();
            mBLEDevices = bleDevices;
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
            return view;
        }
        public void updateDeviceList(ArrayList<BluetoothDevice> bleDevices){
            mBLEDevices = bleDevices;
        }
    }
}