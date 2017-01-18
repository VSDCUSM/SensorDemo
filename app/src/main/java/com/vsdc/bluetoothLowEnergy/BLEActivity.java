package com.vsdc.bluetoothLowEnergy;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

public class BLEActivity extends BLEFrameworkActivity{
    private static final String LOG_TAG = BLEActivity.class.getSimpleName();

    private static final SimpleDateFormat DATE_FORMATTER = new SimpleDateFormat("HH:mm:ss");

    private DeviceListAdapter mBLEDeviceListAdapter;
    private Button mBtnScan, mBtnAdvertise, mBtnPeriodicScan;

    private TextView mTxtScanStatus, mTxtAdStatus, mTxtNotification, mTxtUuid;
    private EditText mEditLog;
    private Button mBtnLog;

    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ble);
        // Initialise text views
        mTxtScanStatus = (TextView) findViewById(R.id.txt_scan_status);
        mTxtScanStatus.setText(getScanStatus().toString());
        mTxtAdStatus = (TextView) findViewById(R.id.txt_ad_status);
        mTxtAdStatus.setText(getAdStatus().toString());
        mTxtNotification = (TextView) findViewById(R.id.txt_notification);
        mTxtUuid = (TextView) findViewById(R.id.txt_uuid);
        mTxtUuid.setText(getUUID().toString());
        // Set button listener
        mBtnScan = (Button) findViewById(R.id.btn_ble_scan);
        mBtnScan.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view){
                if (getScanStatus() == ScanStatus.stopped){
                    bleStartContinuousScan();
                }else{
                    bleStopScan();
                }
            }
        });
        mBtnPeriodicScan = (Button) findViewById(R.id.btn_ble_periodic_scan);
        mBtnPeriodicScan.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view){
                if (getScanStatus() == ScanStatus.stopped){
                    bleStartPeriodicScan();
                }else{
                    bleStopScan();
                }
            }
        });
        mBtnAdvertise = (Button) findViewById(R.id.btn_ble_advertise);
        mBtnAdvertise.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view){
                if (getAdStatus() == AdStatus.stopped){
                    bleStartAdvertise();
                }else{
                    bleStopAdvertise();
                }
            }
        });
        mBtnLog = (Button) findViewById(R.id.btn_log);
        mEditLog = (EditText) findViewById(R.id.edit_log);
        mBtnLog.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view){
                if (mBtnLog.getText().equals("LOG")){
                    try{
                        Process process = Runtime.getRuntime().exec("logcat -d");
                        BufferedReader bufferedReader = new BufferedReader(
                                new InputStreamReader(process.getInputStream()));
                        StringBuilder log = new StringBuilder();
                        String line;
                        while ((line = bufferedReader.readLine()) != null){
                            log.append(line).append("\n");
                        }
                        mEditLog.append(log.toString() + "\n\n");
                        mEditLog.setVisibility(View.VISIBLE);
                        mBtnLog.setText("CLEAR");
                    }catch (IOException e){
                        e.printStackTrace();
                    }
                }else{
                    mEditLog.setVisibility(View.GONE);
                    mEditLog.setText("");
                    mBtnLog.setText("LOG");
                }
            }
        });
        // Set adapter
        mBLEDeviceListAdapter = new DeviceListAdapter(getScanResults());
        ((ListView) findViewById(R.id.listview_devices)).setAdapter(mBLEDeviceListAdapter);
    }

    @Override
    protected void onPause(){
        super.onPause();
        // Stop scanning and advertising to avoid draining battery (MAY CAUSE PROBLEM FOR BACKGROUND SERVICE)
        bleStopScan();
        bleStopAdvertise();
    }

    @Override
    public void bleNotify(BLEEvent bleEvent){
        mTxtNotification.setText(bleEvent.toString());
        mTxtScanStatus.setText(getScanStatus().toString());
        mTxtAdStatus.setText(getAdStatus().toString());
        switch (bleEvent){
            case scanStatusUpdated:
                switch (getScanStatus()){
                    case stopped:
                        mBtnScan.setText(getString(R.string.btn_text_ble_scan));
                        mBtnPeriodicScan.setText(getString(R.string.btn_text_ble_periodic_scan));
                        break;
                    case waitForContinuous:
                        /////
                        break;
                    case waitForPeriodic:
                        /////
                        break;
                    case continuousScan:
                        mBtnScan.setText(getString(R.string.btn_text_ble_scanning));
                        break;
                    case periodicScan:
                        mBtnPeriodicScan.setText(getString(R.string.btn_text_ble_periodic_scanning));
                        break;
                    case periodicWait:
                        /////
                        break;
                }
                break;
            case scanFoundNew:
            case scanUpdatedOld:
                mBLEDeviceListAdapter.notifyDataSetChanged();
                break;
            case scanFailed:
                /////
                break;
            case adStatusUpdated:
                switch (getAdStatus()){
                    case stopped:
                        mBtnAdvertise.setText(R.string.btn_text_ble_advertise);
                        break;
                    case waiting:
                        /////
                        break;
                    case advertising:
                        mBtnAdvertise.setText(R.string.btn_text_ble_advertising);
                        break;
                }
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
        private ArrayList<ScanResult> mScanResults;

        DeviceListAdapter(ArrayList<ScanResult> bleDevices){
            super();
            mScanResults = bleDevices;
        }
        @Override
        public int getCount(){
            return mScanResults.size();
        }
        @Override
        public Object getItem(int position){
            return mScanResults.get(position);
        }
        @Override
        public long getItemId(int i){
            return i;
        }
        @Override
        public View getView(int position, View view, ViewGroup viewGroup){
            if (view == null){
                view = getLayoutInflater().inflate(R.layout.list_item_devices, null);
            }
            // Get handles to text views
            TextView mTextDeviceName = (TextView) view.findViewById(R.id.textview_device_name);
            TextView mTextDeviceAddress = (TextView) view.findViewById(R.id.textview_device_address);
            TextView mTextDeviceDetails = (TextView) view.findViewById(R.id.textview_device_details);
            // Get device selected and set information
            ScanResult scanResult = mScanResults.get(position);
            ScanRecord scanRecord = scanResult.getScanRecord();
            BluetoothDevice bluetoothDevice = scanResult.getDevice();
            final String deviceName = bluetoothDevice.getName();
            if (deviceName != null && deviceName.length() > 0){
                mTextDeviceName.setText(deviceName);
            }else{
                mTextDeviceName.setText(R.string.ble_unknown_device);
            }
            mTextDeviceAddress.setText(bluetoothDevice.getAddress());
            long timestampMillis = System.currentTimeMillis() - SystemClock.elapsedRealtime() +
                    scanResult.getTimestampNanos() / 1000000;
            Date date = new Date(timestampMillis);
            StringBuilder stringBuilder = new StringBuilder()
                    .append("Timestamp: ").append(DATE_FORMATTER.format(date))
                    .append("\t\tRSSI: ").append(scanResult.getRssi()).append(" dBm\t");
            if (scanRecord != null){
                int pathLoss = scanRecord.getTxPowerLevel() - scanResult.getRssi();
                // Using simplified Friis transmission equation
                double distance = Math.pow(10, pathLoss * 0.05) * 0.125 / (4 * Math.PI);
                stringBuilder.append("Tx: ").append(scanRecord.getTxPowerLevel()).append(" dBm\n")
                        .append("Path loss: ").append(pathLoss).append(" dBm\t")
                        .append("Distance: ").append(distance).append(" m");
                if (scanRecord.getServiceUuids() != null){
                    for (ParcelUuid parcelUuid : scanRecord.getServiceData().keySet()){
                        stringBuilder.append("\n").append(parcelUuid.getUuid().toString()).append(" -> ");
                        if (scanRecord.getServiceData(parcelUuid) != null){
                            stringBuilder.append(new String(scanRecord.getServiceData(parcelUuid),
                                    Charset.forName("ASCII")));
                        }else{
                            stringBuilder.append("null");
                        }
                    }
                }
            }
            mTextDeviceDetails.setText(stringBuilder.toString());
            return view;
        }
    }
}