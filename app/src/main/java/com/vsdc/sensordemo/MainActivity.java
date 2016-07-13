package com.vsdc.sensordemo;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    // Declare an ArrayAdapter to populate data for ListView
    public ArrayAdapter<String> mSensorAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Create a List of String for adapter
        List<String> sensorsInfo = new ArrayList<String>();
        // Get reference to sensors
        SensorManager mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        // Get list of sensors
        List<Sensor> deviceSensors = mSensorManager.getSensorList(Sensor.TYPE_ALL);
        // Iterate through the list and get their infos
        Sensor mSensor = null;
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
    }
}
