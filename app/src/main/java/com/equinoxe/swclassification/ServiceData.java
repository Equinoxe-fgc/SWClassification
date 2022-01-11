package com.equinoxe.swclassification;

import android.app.Service;
import android.content.Intent;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.Nullable;

import java.text.DecimalFormat;
import java.util.Timer;
import java.util.TimerTask;

public class ServiceData extends Service implements SensorEventListener {
    private final static boolean SENSORS_ON = true;
    private final static boolean SENSORS_OFF = false;
    private static final int MUESTRAS_POR_SEGUNDO_GAME = 60;
    private static final int iWindowSize = 3000;    // 3 segundos (3000 ms)

    private ServiceHandler mServiceHandler;
    private SensorManager sensorManager;

    private String sCadenaAcelerometro;

    PowerManager powerManager;
    PowerManager.WakeLock wakeLock;

    Timer timerUpdateData;

    DecimalFormat df;

    int iTamBuffer;
    SensorData []dataAccelerometer;
    int iPosDataAccelerometer = 0;

    @Override
    public void onCreate() {
        HandlerThread thread = new HandlerThread("ServiceData", HandlerThread.MIN_PRIORITY);
        thread.start();

        // Get the HandlerThread's Looper and use it for our Handler
        Looper mServiceLooper = thread.getLooper();
        mServiceHandler = new com.equinoxe.swclassification.ServiceData.ServiceHandler(mServiceLooper);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        df = new DecimalFormat("###.##");
    }

    // Handler that receives messages from the thread
    private final class ServiceHandler extends Handler {
        ServiceHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.arg1) {
                case Sensado.ACELEROMETRO:
                    publishSensorValues(Sensado.ACELEROMETRO, msg.arg2, sCadenaAcelerometro);
                    break;
/*                case Sensado.GIROSCOPO:
                    publishSensorValues(Sensado.GIROSCOPO, msg.arg2, sCadenaGiroscopo);
                    break;
                case Sensado.MAGNETOMETRO:
                    publishSensorValues(Sensado.MAGNETOMETRO, msg.arg2, sCadenaMagnetometro);
                    break;
                case Sensado.HEART_RATE:
                    publishSensorValues(Sensado.HEART_RATE, msg.arg2, sCadenaHeartRate);
                    break;
                case Sensado.BAROMETER:
                    publishSensorValues(Sensado.BAROMETER, msg.arg2, sCadenaBarometro);
                    break;*/
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        try {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyApp::WakelockInterno");
            if (wakeLock.isHeld())
                wakeLock.release();
            wakeLock.acquire();
        } catch (NullPointerException e) {
            Log.e("NullPointerException", "ServiceDatosInternalSensor - onStartCommand");
        }

        final TimerTask timerTaskUpdateData = new TimerTask() {
            public void run() {
                    Message msg = mServiceHandler.obtainMessage();
                    msg.arg1 = Sensado.ACELEROMETRO;
                    msg.arg2 = 0;
                    mServiceHandler.sendMessage(msg);
            }
        };
        timerUpdateData = new Timer();
        timerUpdateData.scheduleAtFixedRate(timerTaskUpdateData, Sensado.AMBIENT_INTERVAL_MS / 2, Sensado.AMBIENT_INTERVAL_MS);

        iTamBuffer = MUESTRAS_POR_SEGUNDO_GAME * iWindowSize/1000;
        dataAccelerometer = new SensorData[iTamBuffer];
        for (int i = 0; i < iTamBuffer; i++) {
            dataAccelerometer[i] = new SensorData();
        }

        controlSensors(SENSORS_ON);

        return START_NOT_STICKY;
    }

    private void controlSensors(boolean bSensors_ON) {
            Sensor sensorAcelerometro = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (bSensors_ON)
                sensorManager.registerListener(this, sensorAcelerometro, SensorManager.SENSOR_DELAY_GAME);
            else
                sensorManager.unregisterListener(this, sensorAcelerometro);
    }


    private void publishSensorValues(int iSensor, int iDevice, String sCadena) {
        Intent intent = new Intent(Sensado.NOTIFICATION);
        intent.putExtra("Sensor", iSensor);
        intent.putExtra("Device", iDevice);
        intent.putExtra("Cadena", sCadena);
        sendBroadcast(intent);
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        switch (sensorEvent.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                sCadenaAcelerometro = "A: " + df.format(sensorEvent.values[0]) + " "
                        + df.format(sensorEvent.values[1]) + " "
                        + df.format(sensorEvent.values[2]);
                procesarDatosSensados(Sensor.TYPE_ACCELEROMETER, sensorEvent.timestamp, sensorEvent.values);
                break;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    private void procesarDatosSensados(int iSensor, long timeStamp, float[] values) {
        switch (iSensor) {
            case Sensor.TYPE_ACCELEROMETER:
                dataAccelerometer[iPosDataAccelerometer].setData(timeStamp, values);
                iPosDataAccelerometer = (iPosDataAccelerometer + 1) % iTamBuffer;
                break;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        controlSensors(SENSORS_OFF);
        timerUpdateData.cancel();
        wakeLock.release();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}