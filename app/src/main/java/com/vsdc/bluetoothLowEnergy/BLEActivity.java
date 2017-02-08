package com.vsdc.bluetoothLowEnergy;

import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.text.InputFilter;
import android.text.Spanned;
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

/**
 * The main activity of this app.
 */
public class BLEActivity extends BLEFrameworkActivity{
    private static final String LOG_TAG = BLEActivity.class.getSimpleName();

    private static final SimpleDateFormat DATE_FORMATTER = new SimpleDateFormat("HH:mm:ss");
    private static final int RECONN_NOTIFY_ID = 1, DISCONN_NOTIFY_ID = 2, NEW_NOTIFY_ID = 3, STOPPED_NOTIFY_ID = 4;

    private DeviceListAdapter mBLEDeviceListAdapter;
    private Button mBtnScan, mBtnAdvertise, mBtnLog;
    private TextView mTxtScanStatus, mTxtAdStatus, mTxtNotification;
    private EditText mEditLog, mEditAdName;
    private Context mActivityContext;
    private NotificationManager mNotificationManager;

    private final BLEScanService.BLEScanCallback BLE_SCAN_CALLBACK = new BLEScanService.BLEScanCallback(){
        @Override
        public synchronized void bleNotify(Context context, final BLEScanService.ScanEvent scanEvent){
            if (BLEUtil.isActivityActive){ // Foreground
                Log.d(LOG_TAG, "Foreground bleNotify() -> " + scanEvent.toString());
                // Run the following code on the UI thread to enable UI display
                runOnUiThread(new Runnable(){
                    @Override
                    public void run(){
                        mTxtNotification.setText(scanEvent.toString());
                        switch (scanEvent){
                            case resultAdded:
                            case resultUpdated:
                            case resultRemoved:
                                mBLEDeviceListAdapter.notifyDataSetChanged();
                                break;
                            case scanStatusUpdated:
                                updateUIStatus();
                                break;
                            case scanFailed:
                                /////
                                break;
                            case scanServiceStopped:
                                mBLEDeviceListAdapter.notifyDataSetChanged();
                                bleRetryWaitingTask();
                                break;
                        }
                    }
                });
            }else{ // Background
                Log.d(LOG_TAG, "Background bleNotify() -> " + scanEvent.toString());
                if (scanEvent == BLEScanService.ScanEvent.scanServiceStopped){
                    String content = (!BLEUtil.bluetoothAdapter.isEnabled()) ? "Please turn Bluetooth on and restart" :
                            "Please restart manually";
                    showNotification(context, "BLE Service Stopped", content, STOPPED_NOTIFY_ID);
                }
            }
        }
        @Override
        public synchronized void bleNotifyResult(Context context, final BLEScanService.TrackingEvent trackingEvent,
                                                 final ScanResult scanResult){
            final String ADVERTISED_NAME = BLEUtil.extractAdvertisedName(scanResult);
            // Update UI on foreground
            if (BLEUtil.isActivityActive){
                runOnUiThread(new Runnable(){
                    @Override
                    public void run(){
                        mTxtNotification.setText(trackingEvent.toString());
                        mBLEDeviceListAdapter.notifyDataSetChanged();
                    }
                });
            }
            if (trackingEvent == BLEScanService.TrackingEvent.newDeviceDetected){
                // Check if the device is a recognised device
                if (!ADVERTISED_NAME.equals("")){
                    // Get device infos
                    String deviceName = scanResult.getDevice().getName(),
                            deviceAddress = scanResult.getDevice().getAddress();
                    if (deviceName == null || deviceName.length() == 0){
                        deviceName = getString(R.string.ble_unknown_device);
                    }
                    if (BLEUtil.isActivityActive){ // Foreground
                        Log.d(LOG_TAG, "Foreground bleNotifyResult() -> newDeviceDetected : " + ADVERTISED_NAME);
                        // Show dialog box
                        AlertDialog.Builder builder = new AlertDialog.Builder(mActivityContext);
                        builder.setTitle("Do you want to track this BLE device?")
                                .setMessage("Device Name: " + deviceName + "\nAdvertised Name: " + ADVERTISED_NAME +
                                        "\nMAC Address: " + deviceAddress)
                                .setPositiveButton("Yes", new DialogInterface.OnClickListener(){
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int id){
                                        BLEScanService.trackDevice(scanResult);
                                    }
                                })
                                .setNegativeButton("No", new DialogInterface.OnClickListener(){
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i){
                                        BLEScanService.ignoreDevice(scanResult);
                                    }
                                })
                                .setNeutralButton("Ask me later", new DialogInterface.OnClickListener(){
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i){
                                        // Purposely left blank
                                    }
                                });
                        builder.create().show();
                    }else{ // Background
                        Log.d(LOG_TAG, "Background bleNotifyResult() -> newDeviceDetected : " + ADVERTISED_NAME);
                        showNotification(context, "New Device Detected", "Advertised Name: " + ADVERTISED_NAME,
                                NEW_NOTIFY_ID);
                    }
                }
            }else{ // trackedDeviceReconnected or trackedDeviceDisonnected
                Log.d(LOG_TAG, "Common bleNotifyResult() -> " + trackingEvent.toString() + ": " + ADVERTISED_NAME);
                if (trackingEvent == BLEScanService.TrackingEvent.trackedDeviceReconnected){
                    showNotification(context, "Tracked Device Detected", "Advertised Name: " + ADVERTISED_NAME,
                            RECONN_NOTIFY_ID);
                }else{
                    showNotification(context, "Tracked Device Disconnected", "Advertised Name: " + ADVERTISED_NAME,
                            DISCONN_NOTIFY_ID);
                }
            }
        }
    };
    private final BLEAdvertiseService.BLEAdCallback BLE_AD_CALLBACK = new BLEAdvertiseService.BLEAdCallback(){
        @Override
        public synchronized void bleNotify(Context context, BLEAdvertiseService.AdEvent adEvent){
            if (BLEUtil.isActivityActive){ // Foreground
                Log.d(LOG_TAG, "Foreground bleNotify() -> " + adEvent.toString());
                // Advertisement service is run on main thread, so runOnUiThread() is not needed
                mTxtNotification.setText(adEvent.toString());
                switch (adEvent){
                    case adStatusUpdated:
                        updateUIStatus();
                        break;
                    case adFailed:
                        /////
                        break;
                    case adServiceStopped:
                        bleRetryWaitingTask();
                        break;
                }
            }else{ // Background
                Log.d(LOG_TAG, "Background bleNotify() -> " + adEvent.toString());
                if (adEvent == BLEAdvertiseService.AdEvent.adServiceStopped){
                    String content = (!BLEUtil.bluetoothAdapter.isEnabled()) ? "Please turn Bluetooth on and restart" :
                            "Please restart manually";
                    showNotification(context, "BLE Service Stopped", content, STOPPED_NOTIFY_ID);
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        Log.d(LOG_TAG, "onCreate()");
        setContentView(R.layout.activity_ble);
        mActivityContext = this;
        // Initialise notification manager and clear previous notifications
        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.cancelAll();
        // Initialise text views
        mTxtScanStatus = (TextView) findViewById(R.id.txt_scan_status);
        mTxtScanStatus.setText(BLEScanService.getScanStatus().toString());
        mTxtAdStatus = (TextView) findViewById(R.id.txt_ad_status);
        mTxtAdStatus.setText(BLEAdvertiseService.getAdStatus().toString());
        mTxtNotification = (TextView) findViewById(R.id.txt_notification);
        ((TextView) findViewById(R.id.txt_uuid)).setText(BLEUtil.PARCEL_UUID.getUuid().toString());
        // Set button listener
        mBtnScan = (Button) findViewById(R.id.btn_ble_scan);
        mBtnScan.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view){
                if (BLEScanService.getScanStatus() == BLEScanService.ScanStatus.stopped){
                    bleStartScan();
                }else{
                    bleStopScan();
                }
            }
        });
        mBtnAdvertise = (Button) findViewById(R.id.btn_ble_advertise);
        mBtnAdvertise.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view){
                if (BLEAdvertiseService.getAdStatus() == BLEAdvertiseService.AdStatus.stopped){
                    if (!BLEAdvertiseService.setAdvertisedName(mEditAdName.getText().toString())){
                        // The advertised name input by user is not valid
                        mEditAdName.setBackgroundResource(R.drawable.edit_border_error);
                    }else{
                        mEditAdName.setBackgroundResource(android.R.drawable.editbox_background);
                    }
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
        // Set edit text filters
        mEditAdName = (EditText) findViewById(R.id.edit_advertised_name);
        mEditAdName.setFilters(new InputFilter[]{
                new InputFilter.LengthFilter(3),
                new InputFilter(){
                    @Override
                    public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dStart,
                                               int dEnd){
                        // Extract the new input
                        String filteredStr = source.subSequence(start, end).toString();
                        // Remove all disallowed characters
                        filteredStr = filteredStr.replaceAll("[^A-Za-z0-9]", "");
                        // Return the filtered text
                        if (filteredStr.equals(source)){
                            return null;
                        }else{
                            return filteredStr;
                        }
                    }
                }
        });
        // Set adapter
        mBLEDeviceListAdapter = new DeviceListAdapter(BLEScanService.getScanResults());
        ((ListView) findViewById(R.id.listview_devices)).setAdapter(mBLEDeviceListAdapter);
        // Register BLE scan and advertisement callbacks (these callbacks will not be unregistered)
        BLEScanService.setBLEScanCallback(BLE_SCAN_CALLBACK);
        BLEAdvertiseService.setBLEAdCallback(BLE_AD_CALLBACK);
    }
    @Override
    protected void onResume(){
        super.onResume();
        Log.d(LOG_TAG, "onResume()");
        // Check current status (a service may be runnning)
        updateUIStatus();
    }
    @Override
    protected void onPause(){
        super.onPause();
        Log.d(LOG_TAG, "onPause() (debug only)");
    }

    /**
     * Helper method to display the current scan status and ad status on the UI.
     */
    private void updateUIStatus(){
        mTxtScanStatus.setText(BLEScanService.getScanStatus().toString());
        mTxtAdStatus.setText(BLEAdvertiseService.getAdStatus().toString());
        switch (BLEScanService.getScanStatus()){
            case stopped:
            case waiting:
                mBtnScan.setText(getString(R.string.btn_text_ble_scan));
                break;
            case scanning:
                mBtnScan.setText(getString(R.string.btn_text_ble_scanning));
                break;
        }
        switch (BLEAdvertiseService.getAdStatus()){
            case stopped:
            case waiting:
                mBtnAdvertise.setText(R.string.btn_text_ble_advertise);
                break;
            case advertising:
                mBtnAdvertise.setText(R.string.btn_text_ble_advertising);
                mEditAdName.setText(BLEAdvertiseService.getAdNameInUse());
                break;
        }
    }
    /**
     * Helper method to build and show notification with the specified title, content and ID. The notification will
     * use the app icon and open this activity when it is clicked.
     *
     * @param context The context to build this notification
     * @param title The title of the notification
     * @param content The content text of the notification
     * @param id The ID to be associated with this notification (for overwriting it with similar notification)
     */
    private void showNotification(Context context, String title, String content, int id){
        // Intent to open this activity from the notification
        Intent intent = new Intent(context, BLEActivity.class);
        // Ensure that navigating backward from the activity leads out to the home screen
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
        stackBuilder.addNextIntent(intent);
        PendingIntent pendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
        // Build notification
        Notification notification = new Notification.Builder(context)
                .setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build();
        // Show notification
        mNotificationManager.notify(id, notification);
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
            long timestampMillis = BLEUtil.extractSystemTimestampMillis(scanResult);
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