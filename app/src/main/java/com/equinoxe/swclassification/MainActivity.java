package com.equinoxe.swclassification;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Spinner;
import android.widget.TextView;

import com.equinoxe.swclassification.databinding.ActivityMainBinding;

public class MainActivity extends Activity {

    private ActivityMainBinding binding;

    private Spinner spinnerModel;
    private Spinner spinnerDevice;
    private Spinner spinnerThreads;

    private Classifier.Model model = Classifier.Model.QUANTIZED_EFFICIENTNET;
    private Classifier.Device device = Classifier.Device.CPU;
    private int numThreads = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        spinnerModel = findViewById(R.id.spinner_model);
        spinnerDevice = findViewById(R.id.spinner_device);
        spinnerThreads = findViewById(R.id.spinner_threads);

        spinnerModel.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                model = Classifier.Model.valueOf(adapterView.getItemAtPosition(i).toString().toUpperCase());
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        spinnerDevice.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                device = Classifier.Device.valueOf(adapterView.getItemAtPosition(i).toString());
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        spinnerThreads.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                numThreads = adapterView.getSelectedItemPosition() + 1;
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        model = Classifier.Model.valueOf(spinnerModel.getSelectedItem().toString().toUpperCase());
        device = Classifier.Device.valueOf(spinnerDevice.getSelectedItem().toString());
    }

    public void onStartClick(View v) {
        SharedPreferences pref = getApplicationContext().getSharedPreferences("Settings", MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();

        editor.putString("Model", model.name());
        editor.putString("Device", device.name());
        editor.putInt("Threads", numThreads);
        editor.apply();

        Intent intent = new Intent(this, Sensado.class);
        startActivity(intent);
    }
}