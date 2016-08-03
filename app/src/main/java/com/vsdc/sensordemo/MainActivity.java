package com.vsdc.sensordemo;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity implements SensorEventListener {
    // Declare an ArrayAdapter to populate data for ListView
    public ArrayAdapter<String> mSensorAdapter;
    // Declare sensor manager and sensor object
    private SensorManager mSensorManager;
    private Sensor mLight;
    // Declare handles to Textview and Button
    private TextView mTextLight;
    private TextView mTextAccuracy;
    private Button mBtnBLE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Get reference to sensors
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        // Get handle to TextView
        TextView mTextList = (TextView) findViewById(R.id.textview_list);
        // Create a String for listing available sensor types
        String availableTypes = mTextList.getText().toString();
        if (mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null){
            availableTypes += this.getString(R.string.type_acc);
        }
        if (mSensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE) != null){
            availableTypes += this.getString(R.string.type_amb);
        }
        if (mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY) != null){
            availableTypes += this.getString(R.string.type_grav);
        }
        if (mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null){
            availableTypes += this.getString(R.string.type_gyro);
        }
        if (mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT) != null){
            availableTypes += this.getString(R.string.type_light);
        }
        if (mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION) != null){
            availableTypes += this.getString(R.string.type_lin);
        }
        if (mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null){
            availableTypes += this.getString(R.string.type_mag);
        }
        if (mSensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE) != null){
            availableTypes += this.getString(R.string.type_pres);
        }
        if (mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY) != null){
            availableTypes += this.getString(R.string.type_prox);
        }
        if (mSensorManager.getDefaultSensor(Sensor.TYPE_RELATIVE_HUMIDITY) != null){
            availableTypes += this.getString(R.string.type_humid);
        }
        if (mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null){
            availableTypes += this.getString(R.string.type_rot);
        }
        mTextList.setText(availableTypes);

        // Create a List of String for adapter
        List<String> sensorsInfo = new ArrayList<String>();
        // Get list of sensors
        List<Sensor> deviceSensors = mSensorManager.getSensorList(Sensor.TYPE_ALL);
        // Iterate through the list and get their infos
        Sensor mSensor;
        String sensorInfo = "";
        for (int i = 0; i < deviceSensors.size(); i++){
            mSensor = deviceSensors.get(i);
            sensorInfo = mSensor.getName() + "\nRes: " + mSensor.getResolution() + "\nRange: "
                    + mSensor.getMaximumRange() + "\nMin Delay: " + mSensor.getMinDelay();
            sensorsInfo.add(sensorInfo);
        }
        // Initialize the ArrayAdapter
        mSensorAdapter = new ArrayAdapter<String>(
                this, // The current context (this activity)
                R.layout.list_item_sensors, // Layout of each item to display
                R.id.list_item_sensors_textview, // ID of the textview to display String
                sensorsInfo);   // List of String to display
        // Get reference to ListView in activity_main.xml
        ListView listView = (ListView) findViewById(R.id.listview_sensors);
        // Bind the adapter to this ListView
        listView.setAdapter(mSensorAdapter);

        // Get light sensor
        mLight = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        // Initialize handles
        mTextLight = (TextView) findViewById(R.id.textview_light);
        mTextAccuracy = (TextView) findViewById(R.id.textview_accuracy);

        // Check for Bluetooth Low Energy
        mBtnBLE = (Button) findViewById(R.id.btn_ble);
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            mBtnBLE.setText(getString(R.string.btn_text_ble_na));
            mBtnBLE.setEnabled(false);
        }else{
            // Define click action
            mBtnBLE.setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View view) {
                    // Call up BLEActivity
                    Intent intent = new Intent(getBaseContext(), BLEActivity.class);
                    startActivity(intent);
                }
            });
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy){
        // Get the string from res
        String text = this.getString(R.string.text_accuracy);
        // Set the text
        mTextAccuracy.setText(String.format(text, accuracy));
    }

    @Override
    public void onSensorChanged(SensorEvent event){
        // Get the value detected by sensor
        float ambientLight = event.values[0];
        // Display the value
        // Get the string from res
        String text = this.getString(R.string.text_light);
        mTextLight.setText(String.format(text, ambientLight));
    }

    @Override
    protected void onResume(){
        super.onResume();
        mSensorManager.registerListener(
                this, // SensorEventListener to register
                mLight, // Sensor which broadcasts events
                SensorManager.SENSOR_DELAY_NORMAL); // Data delay time
    }

    @Override
    protected void onPause(){
        super.onPause();
        mSensorManager.unregisterListener(this); // Unregister listener to reduce power consumption
    }
}
