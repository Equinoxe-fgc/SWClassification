package com.equinoxe.swclassification;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;

import com.equinoxe.swclassification.databinding.ActivityMainBinding;

public class MainActivity extends Activity {

    private ActivityMainBinding binding;

    private TextView textViewRange, textViewResolution;

    private SensorManager sensorManager;
    Sensor sensorAccelerometer = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        textViewRange = findViewById(R.id.textViewRange);
        textViewResolution = findViewById(R.id.textViewResolution);

        checkForPermissions();

        infoSensores();

        /*SharedPreferences pref = getApplicationContext().getSharedPreferences("Settings", MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        editor.clear();
        editor.commit();*/

        //model = Classifier.Model.valueOf(spinnerModel.getSelectedItem().toString().toUpperCase());
        //device = Classifier.Device.valueOf(spinnerDevice.getSelectedItem().toString());
    }

    public void onStartClick(View v) {
        /* SharedPreferences pref = getApplicationContext().getSharedPreferences("Settings", MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        editor.putInt("Threads", numThreads - 1);
        editor.apply();*/

        Intent intent = new Intent(this, Sensado.class);
        startActivity(intent);
    }

    private void checkForPermissions() {
        String[] PERMISSIONS_STORAGE = {
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        };

        int permission = ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(this, PERMISSIONS_STORAGE, 1);
        }

        permission = ActivityCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS);
        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BODY_SENSORS}, 1);
        }
    }

    public void infoSensores() {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        sensorAccelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        float fRange = sensorAccelerometer.getMaximumRange();
        float fResolution = sensorAccelerometer.getResolution();

        textViewRange.setText("Range: " + fRange);
        textViewResolution.setText("Resolution: " + fResolution);
    }
}