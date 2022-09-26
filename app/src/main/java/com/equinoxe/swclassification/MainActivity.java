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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());


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
}