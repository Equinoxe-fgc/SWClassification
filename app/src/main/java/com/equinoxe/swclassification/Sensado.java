package com.equinoxe.swclassification;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.FragmentActivity;
import androidx.wear.ambient.AmbientModeSupport;

import com.equinoxe.swclassification.databinding.ActivitySensadoBinding;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

public class Sensado extends FragmentActivity implements AmbientModeSupport.AmbientCallbackProvider {
    private static final String AMBIENT_UPDATE_ACTION = "com.equinoxe.swclassification.action.AMBIENT_UPDATE";
    public static final String NOTIFICATION = "com.equinoxe.swclassification.NOTIFICACION";
    public static final long AMBIENT_INTERVAL_MS = TimeUnit.SECONDS.toMillis(1);
    public static final long DELAY_BRUSH = TimeUnit.SECONDS.toMillis(3);
    public static final int WINDOW_FILTER_DETECTION_SIZE = 5;

    public static final String CLASS_OTHER = "other";
    public static final String CLASS_BRUSH = "brush_teeth";

    final static int ACELEROMETRO = 0;
    final static int GIROSCOPO    = 1;
    final static int MAGNETOMETRO = 2;
    final static int HEART_RATE   = 3;
    final static int BAROMETER    = 4;
    final static int MSG          = 200;

    private TextView textViewAcceleration, textViewGyroscope, textViewBarometer, textViewMsg;
    private TextView textViewMsg2;
    private TextView textViewBattery, textViewHora;
    private Button buttonBrush;

    private ActivitySensadoBinding binding;

    AmbientModeSupport.AmbientController controller;
    private AlarmManager ambientUpdateAlarmManager;
    private PendingIntent ambientUpdatePendingIntent;
    private BroadcastReceiver ambientUpdateBroadcastReceiver;

    Timer timerBrush;
    TimerTask timerTaskBrush;

    Intent intentServicioDatos;

    private String sMsgAccelerometer, sMsgGyroscope, sMsgBarometer, sMsg, sMsg2, startDateandTime, dateAndTimeFile;

    SimpleDateFormat sdfHora;
    static SimpleDateFormat sdfFechaHora, sdFechaHoraNombreFichero;

    int iDetectCount = 0;

    boolean bOffline, bDetectionLog, bVibrate;
    static boolean bLog;

    static boolean bBrush = false;
    int iNegativos = 0;
    int iPositivos = 0;
    int iFalsoPositivo = 0;
    int iFalsoNegativo = 0;

    static FileOutputStream fLogBrush;

    int []windowFilterDetection = new int[WINDOW_FILTER_DETECTION_SIZE];
    int iPosWindowsFilterDetection;

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
        textViewMsg2 = findViewById(R.id.textViewMsg2);
        buttonBrush = findViewById(R.id.buttonBrush);

        Bundle extras = getIntent().getExtras();
        bOffline = extras.getBoolean("Offline");
        bLog = extras.getBoolean("Log");
        bDetectionLog = extras.getBoolean("DetectionLog");
        bVibrate = extras.getBoolean("Vibrate");

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

        sdfHora = new SimpleDateFormat("HH:mm", Locale.UK);
        sdfFechaHora = new SimpleDateFormat("yyyyMMdd_HH:mm:ss:SSS", Locale.UK);
        sdFechaHoraNombreFichero = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.UK);

        ambientUpdateAlarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, AMBIENT_INTERVAL_MS, AMBIENT_INTERVAL_MS, ambientUpdatePendingIntent);

        startDateandTime = sdfFechaHora.format(new Date());
        dateAndTimeFile = sdFechaHoraNombreFichero.format(new Date());

        if (bLog) {
            createBrushLogFile();

            textViewBattery.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24.0F);
            textViewHora.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 48.0F);

            textViewAcceleration.setVisibility(View.GONE);
            textViewMsg.setVisibility(View.GONE);
            textViewMsg2.setVisibility(View.GONE);
        }

        for (int i=0; i < WINDOW_FILTER_DETECTION_SIZE; i++)
            windowFilterDetection[i] = 0;
        iPosWindowsFilterDetection = 0;

        crearServicio();
    }

    public void createBrushLogFile() {
        //currentDateandTimeFile = sdFechaHoraNombreFichero.format(new Date());
        String sFileName = Build.MODEL + "_" + dateAndTimeFile + "_BrushLog.txt";
        File filePath;

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            filePath = getFilesDir();
        else
            filePath = Environment.getExternalStorageDirectory();

        File fileBrushLog;
        try {
            fileBrushLog = new File(filePath, sFileName);
            fLogBrush = new FileOutputStream(fileBrushLog, false);

            // Se empieza grabando que no se está lavando los dientes
            String sCadena = sdfFechaHora.format(new Date()) + " 0\n";
            fLogBrush.write(sCadena.getBytes());
        } catch (IOException e) {
            Toast toast = Toast.makeText(this, "Error opening file", Toast.LENGTH_SHORT);
            toast.show();
        }
    }

    protected static TimerTask initBrushTask() {
        return new TimerTask() {

            @Override
            public void run() {
                if (bLog) {
                    try {
                        String currentDateandTime = sdfFechaHora.format(new Date()) + " 1\n";
                        fLogBrush.write(currentDateandTime.getBytes());
                    } catch (IOException e) {
                    }

                bBrush = true;
                }
            }
        };
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

        textViewHora.setText(sdfHora.format(new Date()));

        textViewAcceleration.setText(sMsgAccelerometer);
        /*textViewGyroscope.setText(sMsgGyroscope);
        textViewBarometer.setText(sMsgBarometer);*/
        textViewMsg.setText(sMsg);

        sMsg2 = "P: " + iPositivos + " N: " + iNegativos + " FP: " + iFalsoPositivo + " FN: " + iFalsoNegativo;
        textViewMsg2.setText(sMsg2);

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
        grabarEstadisticas();

        if (bLog) {
            try {
                // Se graba la hora de finalización
                String sCadena = sdfFechaHora.format(new Date()) + " 0\n";
                fLogBrush.write(sCadena.getBytes());

                fLogBrush.close();
            } catch (Exception e) {
            }
        }
        super.onDestroy();

        unregisterReceiver(receiver);
    }

    public void onClickStop(View v) {
        stopService(intentServicioDatos);
        finish();
    }

    public void onClickBrush(View v) {
        if (bBrush) {
            if (bLog) {
                try {
                    String currentDateandTime = sdfFechaHora.format(new Date()) + " 0\n";
                    fLogBrush.write(currentDateandTime.getBytes());
                } catch (IOException e) {
                }
            }

            bBrush = false;
            timerBrush.cancel();

            buttonBrush.setText(getString(R.string.START_BRUSH));
        } else {
                timerBrush = new Timer();
                timerTaskBrush = initBrushTask();
                timerBrush.schedule(timerTaskBrush, DELAY_BRUSH);

                buttonBrush.setText(getString(R.string.STOP_BRUSH));
        }
    }

    private void grabarEstadisticas() {
        String currentDateandTime = sdfFechaHora.format(new Date());
        try {
            FileOutputStream fOutStatsLog = new FileOutputStream(Environment.getExternalStorageDirectory() + "/" + Build.MODEL + "_Stats.txt", true);
            String sCadena = startDateandTime + " " + currentDateandTime + " " + iPositivos + "," + iNegativos + "," + iFalsoPositivo + "," + iFalsoNegativo + "\n";
            fOutStatsLog.write(sCadena.getBytes());
            fOutStatsLog.close();
        } catch (IOException e) {
        }
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
                        if (bOffline)
                            sMsg = sCadena;
                        else
                            sMsg = sCadena + " - " + iDetectCount;

                        procesaDeteccion(sCadena);
                        break;
                        /*case MAGNETOMETRO:
                            sMsgMagnetometer = sCadena;
                            break;
                        case HEART_RATE:
                            sMsgHR = sCadena;
                            break;*/
                    }
                }
        }
    };

    private void procesaDeteccion(String sCadena) {
        int iBrushDetectado;
        boolean bBrushFinalDetectado;

        if (sCadena.compareTo(CLASS_BRUSH) == 0)
            iBrushDetectado = 1;
        else if (sCadena.compareTo(CLASS_OTHER) == 0)
            iBrushDetectado = 0;
        else
            return;

        windowFilterDetection[iPosWindowsFilterDetection] = iBrushDetectado;
        iPosWindowsFilterDetection = (iPosWindowsFilterDetection + 1) % WINDOW_FILTER_DETECTION_SIZE;

        int iNumDetecciones = 0;
        for (int i = 0; i < WINDOW_FILTER_DETECTION_SIZE; i++)
            iNumDetecciones += windowFilterDetection[i];
        bBrushFinalDetectado = iNumDetecciones > WINDOW_FILTER_DETECTION_SIZE/2;

        if (bBrushFinalDetectado) {
            if (bVibrate)
                vibrate();

            iDetectCount++;
            if (bBrush) {
                iPositivos++;
            }
            else
                iFalsoPositivo++;
        } else {
            if (bBrush)
                iFalsoNegativo++;
            else
                iNegativos++;
        }
    }


    private void vibrate() {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        v.vibrate(VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE));
    }


    private void crearServicio() {
        intentServicioDatos = new Intent(this, ServiceData.class);
        intentServicioDatos.putExtra("Offline", bOffline);
        intentServicioDatos.putExtra("Log", bLog);
        intentServicioDatos.putExtra("DetectionLog", bDetectionLog);
        intentServicioDatos.putExtra("DateTime", startDateandTime);
        intentServicioDatos.putExtra("DateTimeFile", dateAndTimeFile);
        startService(intentServicioDatos);
    }
}