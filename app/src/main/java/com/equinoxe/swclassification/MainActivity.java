package com.equinoxe.swclassification;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Spinner;
import com.equinoxe.swclassification.databinding.ActivityMainBinding;

public class MainActivity extends Activity {

    private ActivityMainBinding binding;

    private Spinner spinnerModel;
    private Spinner spinnerDevice;
    private Spinner spinnerThreads;

    private Classifier.Model model = Classifier.Model.QUANT_EFF;
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

        /*SharedPreferences pref = getApplicationContext().getSharedPreferences("Settings", MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        editor.clear();
        editor.commit();*/

        SharedPreferences pref = getApplicationContext().getSharedPreferences("Settings", MODE_PRIVATE);
        model = Classifier.Model.valueOf(pref.getString("Model", Classifier.Model.QUANT_EFF.toString()));
        device = Classifier.Device.valueOf(pref.getString("Device", Classifier.Device.CPU.toString()));
        numThreads = pref.getInt("Threads", 1) + 1;

        spinnerModel.setSelection(model.ordinal());
        spinnerDevice.setSelection(device.ordinal());
        spinnerThreads.setSelection(numThreads - 1);

        //model = Classifier.Model.valueOf(spinnerModel.getSelectedItem().toString().toUpperCase());
        //device = Classifier.Device.valueOf(spinnerDevice.getSelectedItem().toString());
    }

    public void onStartClick(View v) {
        SharedPreferences pref = getApplicationContext().getSharedPreferences("Settings", MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();

        editor.putString("Model", model.name());
        editor.putString("Device", device.name());
        editor.putInt("Threads", numThreads - 1);
        editor.apply();

        Intent intent = new Intent(this, Sensado.class);
        startActivity(intent);
    }
}