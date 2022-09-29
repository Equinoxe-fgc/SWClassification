package com.equinoxe.swclassification;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;
import androidx.wear.ambient.AmbientModeSupport;

import com.equinoxe.swclassification.databinding.ActivitySensadoBinding;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class Sensado extends FragmentActivity implements AmbientModeSupport.AmbientCallbackProvider {
    private static final String AMBIENT_UPDATE_ACTION = "com.equinoxe.swclassification.action.AMBIENT_UPDATE";
    public static final String NOTIFICATION = "com.equinoxe.swclassification.NOTIFICACION";
    public static final long AMBIENT_INTERVAL_MS = TimeUnit.SECONDS.toMillis(1);

    public static final String CLASS_OTHER = "other";
    public static final String CLASS_BRUSH = "brush_teeth";

    final static int ACELEROMETRO = 0;
    final static int GIROSCOPO    = 1;
    final static int MAGNETOMETRO = 2;
    final static int HEART_RATE   = 3;
    final static int BAROMETER    = 4;
    final static int ERROR        = 100;
    final static int MSG          = 200;
    final static int MIN_VALUE    = 300;
    final static int MAX_VALUE    = 400;

    private TextView textViewAcceleration, textViewGyroscope, textViewBarometer, textViewMsg;
    private TextView textViewMinValues, textViewMaxValues;
    private TextView textViewBattery, textViewHora;

    private ActivitySensadoBinding binding;

    AmbientModeSupport.AmbientController controller;
    private AlarmManager ambientUpdateAlarmManager;
    private PendingIntent ambientUpdatePendingIntent;
    private BroadcastReceiver ambientUpdateBroadcastReceiver;

    Intent intentServicioDatos;

    private String sMsgAccelerometer, sMsgGyroscope, sMsgBarometer, sMsg;
    private String sMsgMinValues, sMsgMaxValues;

    SimpleDateFormat sdf;

    int iDetectCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivitySensadoBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        textViewBattery = findViewById(R.id.textViewBattery);
        textViewHora = findViewById(R.id.textViewHora);
        textViewAcceleration = findViewById(R.id.textViewAcceleration);
        textViewGyroscope = findViewById(R.id.textViewGyroscope);
        textViewBarometer = findViewById(R.id.textViewBarometer);
        textViewMsg = findViewById(R.id.textViewMsg);
        textViewMinValues = findViewById(R.id.textViewMinValues);
        textViewMaxValues = findViewById(R.id.textViewMaxValues);

        registerReceiver(receiver, new IntentFilter(NOTIFICATION));

        controller = AmbientModeSupport.attach(this);
        ambientUpdateAlarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent ambientUpdateIntent = new Intent(AMBIENT_UPDATE_ACTION);
        ambientUpdatePendingIntent = PendingIntent.getBroadcast(
                this, 0, ambientUpdateIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        ambientUpdateBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                refreshDisplayAndSetNextUpdate();
            }
        };

        sdf = new SimpleDateFormat("HH:mm", Locale.UK);

        ambientUpdateAlarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, AMBIENT_INTERVAL_MS, AMBIENT_INTERVAL_MS, ambientUpdatePendingIntent);
        crearServicio();
    }

    @Override
    public AmbientModeSupport.AmbientCallback getAmbientCallback() {
        return new AmbientModeSupport.AmbientCallback() {
            public void onEnterAmbient(Bundle ambientDetails) {
                refreshDisplayAndSetNextUpdate();
            }

            public void onExitAmbient(Bundle ambientDetails) {
                ambientUpdateAlarmManager.cancel(ambientUpdatePendingIntent);
            }

            @Override
            public void onUpdateAmbient() {
                refreshDisplayAndSetNextUpdate();
            }
        };
    }

    private void refreshDisplayAndSetNextUpdate() {
        // Implement data retrieval and update the screen
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, ifilter);
        String sBateria = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) + " %";
        textViewBattery.setText(sBateria);

        textViewHora.setText(sdf.format(new Date()));

        textViewAcceleration.setText(sMsgAccelerometer);
        /*textViewGyroscope.setText(sMsgGyroscope);
        textViewBarometer.setText(sMsgBarometer);*/
        textViewMsg.setText(sMsg);

        textViewMaxValues.setText(sMsgMaxValues);
        textViewMinValues.setText(sMsgMinValues);

        /*long timeMs = System.currentTimeMillis();
        // Schedule a new alarm
        // Calculate the next trigger time
        long delayMs = AMBIENT_INTERVAL_MS - (timeMs % AMBIENT_INTERVAL_MS);
        long triggerTimeMs = timeMs + delayMs;
        ambientUpdateAlarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                triggerTimeMs,
                ambientUpdatePendingIntent);*/
    }

    @Override
    public void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter(AMBIENT_UPDATE_ACTION);
        registerReceiver(ambientUpdateBroadcastReceiver, filter);
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(ambientUpdateBroadcastReceiver);
        ambientUpdateAlarmManager.cancel(ambientUpdatePendingIntent);
    }

    @Override
    public void onDestroy() {
        //ambientUpdateAlarmManager.cancel(ambientUpdatePendingIntent);
        super.onDestroy();

        unregisterReceiver(receiver);
    }

    public void onClickStop(View v) {
        stopService(intentServicioDatos);
        finish();
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle bundle = intent.getExtras();

            if (bundle != null) {
                int iSensor = bundle.getInt("Sensor");
                String sCadena = bundle.getString("Cadena");

                switch (iSensor) {
                    case ACELEROMETRO:
                        sMsgAccelerometer = sCadena;
                        break;
                    case GIROSCOPO:
                        sMsgGyroscope = sCadena;
                        break;
                    case BAROMETER:
                        sMsgBarometer = sCadena;
                        break;
                    case MSG:
                        sMsg = sCadena + " - " + iDetectCount;
                        if (sCadena.compareTo(CLASS_BRUSH) == 0) {
                            iDetectCount++;
                            //vibrate();
                        }
                        break;
                        /*case MAGNETOMETRO:
                            sMsgMagnetometer = sCadena;
                            break;
                        case HEART_RATE:
                            sMsgHR = sCadena;
                            break;*/
                    case MAX_VALUE:
                        sMsgMaxValues = sCadena;
                        break;
                    case MIN_VALUE:
                        sMsgMinValues = sCadena;
                        break;
                    }
                }
        }
    };

    private void vibrate() {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        v.vibrate(VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE));
    }


    private void crearServicio() {
        intentServicioDatos = new Intent(this, ServiceData.class);

        startService(intentServicioDatos);
    }
}