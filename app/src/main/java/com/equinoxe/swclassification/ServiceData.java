package com.equinoxe.swclassification;

import android.app.Service;
import android.content.Intent;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
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
import android.widget.Toast;

import androidx.annotation.Nullable;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

public class ServiceData extends Service implements SensorEventListener {
    private final static boolean SENSORS_ON = true;
    private final static boolean SENSORS_OFF = false;
    private static final int SAMPLES_PER_SECOND_GAME = 60;
    private static final int WINDOW_TIME = (int)TimeUnit.SECONDS.toMillis(5);
    private static final int CLASSIFY_INTERVAL_TIME = (int)TimeUnit.SECONDS.toMillis(3);

    private ServiceHandler mServiceHandler;
    private SensorManager sensorManager;

    private String sMsgAccelerometer, sMsgGyroscope, sMsgBarometer, sMsg;

    PowerManager powerManager;
    PowerManager.WakeLock wakeLock;

    Timer timerUpdateData;

    DecimalFormat df;

    int iTamBuffer;
    SensorData []dataAccelerometer;
    SensorData []dataGyroscope;
    SensorData []dataBarometer;
    int iPosDataAccelerometer = 0;
    int iPosDataGyroscope = 0;
    int iPosDataBarometer = 0;

    private Classifier.Model model = Classifier.Model.QUANTIZED_EFFICIENTNET;
    private Classifier.Device device = Classifier.Device.CPU;
    private int numThreads = -1;
    private Classifier classifier;
    private Timer timerClassify;
    private Bitmap rgbFrameBitmap = null;
    private int[] rgbBytes = null;

    @Override
    public void onCreate() {
        HandlerThread thread = new HandlerThread("ServiceData", HandlerThread.MIN_PRIORITY);
        thread.start();

        // Get the HandlerThread's Looper and use it for our Handler
        Looper mServiceLooper = thread.getLooper();
        mServiceHandler = new com.equinoxe.swclassification.ServiceData.ServiceHandler(mServiceLooper);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        df = new DecimalFormat("###.##");

        SharedPreferences pref = getApplicationContext().getSharedPreferences("Settings", MODE_PRIVATE);
        model = Classifier.Model.valueOf(pref.getString("Model", Classifier.Model.QUANTIZED_EFFICIENTNET.toString()));
        device = Classifier.Device.valueOf(pref.getString("Device", Classifier.Device.CPU.toString()));
        numThreads = pref.getInt("Threads", 1);

        try {
            classifier = Classifier.create(this, model, device, numThreads);
        } catch (IOException | IllegalArgumentException e) {
        }

        rgbFrameBitmap = Bitmap.createBitmap(iTamBuffer, 1, Bitmap.Config.ARGB_8888);
        rgbBytes = new int[iTamBuffer * 1];  // Ancho * alto
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
                    publishSensorValues(Sensado.ACELEROMETRO, sMsgAccelerometer);
                    break;
                case Sensado.GIROSCOPO:
                    publishSensorValues(Sensado.GIROSCOPO, sMsgGyroscope);
                    break;
                case Sensado.BAROMETER:
                    publishSensorValues(Sensado.BAROMETER, sMsgBarometer);
                    break;
                case Sensado.MSG:
                        publishSensorValues(Sensado.MSG, sMsg);
                        break;
                /*case Sensado.MAGNETOMETRO:
                    publishSensorValues(Sensado.MAGNETOMETRO, msg.arg2, sCadenaMagnetometro);
                    break;
                case Sensado.HEART_RATE:
                    publishSensorValues(Sensado.HEART_RATE, msg.arg2, sCadenaHeartRate);
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
                Message msgAccelerometer = mServiceHandler.obtainMessage();
                msgAccelerometer.arg1 = Sensado.ACELEROMETRO;
                mServiceHandler.sendMessage(msgAccelerometer);

                Message msgGyroscope = mServiceHandler.obtainMessage();
                msgGyroscope.arg1 = Sensado.GIROSCOPO;
                mServiceHandler.sendMessage(msgGyroscope);

                Message msgBarometer = mServiceHandler.obtainMessage();
                msgBarometer.arg1 = Sensado.BAROMETER;
                mServiceHandler.sendMessage(msgBarometer);
            }
        };
        timerUpdateData = new Timer();
        timerUpdateData.scheduleAtFixedRate(timerTaskUpdateData, Sensado.AMBIENT_INTERVAL_MS / 2, Sensado.AMBIENT_INTERVAL_MS);

        final TimerTask timerTaskClassify = new TimerTask() {
            public void run() {
                if (classifier != null) {
                    controlSensors(SENSORS_OFF);
                    convertSensorData2RGBBytes();
                    controlSensors(SENSORS_ON);

                    rgbFrameBitmap.setPixels(rgbBytes, 0, iTamBuffer, 0,0, iTamBuffer, 1);
                    final List<Classifier.Recognition> results = classifier.recognizeImage(rgbFrameBitmap);

                    Message msgMsg = mServiceHandler.obtainMessage();
                    msgMsg.arg1 = Sensado.MSG;
                    sMsg = results.get(1).getId();
                    mServiceHandler.sendMessage(msgMsg);
                }
            }
        };
        timerClassify = new Timer();
        timerClassify.scheduleAtFixedRate(timerTaskClassify, CLASSIFY_INTERVAL_TIME, CLASSIFY_INTERVAL_TIME);

        iTamBuffer = SAMPLES_PER_SECOND_GAME * WINDOW_TIME/1000;
        dataAccelerometer = new SensorData[iTamBuffer];
        dataGyroscope = new SensorData[iTamBuffer];
        dataBarometer = new SensorData[iTamBuffer];

        for (int i = 0; i < iTamBuffer; i++) {
            dataAccelerometer[i] = new SensorData();
            dataGyroscope[i] = new SensorData();
            dataBarometer[i] = new SensorData();
        }

        controlSensors(SENSORS_ON);

        return START_NOT_STICKY;
    }

    private void convertSensorData2RGBBytes() {
        SensorData data;

        for (int i = 0; i < iTamBuffer; i++) {
            data = dataAccelerometer[iPosDataAccelerometer];
            iPosDataAccelerometer = (iPosDataAccelerometer + 1) % iTamBuffer;
            rgbBytes[i] = data.getQuantizeValueAccelerometer();
        }
    }

    private void controlSensors(boolean bSensors_ON) {
            Sensor sensorAccelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            Sensor sensorGyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            Sensor sensorBarometer = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);

            if (bSensors_ON) {
                sensorManager.registerListener(this, sensorAccelerometer, SensorManager.SENSOR_DELAY_GAME);
                sensorManager.registerListener(this, sensorGyroscope, SensorManager.SENSOR_DELAY_GAME);
                if (sensorBarometer != null)
                    sensorManager.registerListener(this, sensorBarometer, SensorManager.SENSOR_DELAY_GAME);
            }
            else {
                sensorManager.unregisterListener(this, sensorAccelerometer);
                sensorManager.unregisterListener(this, sensorGyroscope);
                if (sensorBarometer != null)
                    sensorManager.unregisterListener(this, sensorBarometer);
            }
    }


    private void publishSensorValues(int iSensor, String sCadena) {
        Intent intent = new Intent(Sensado.NOTIFICATION);
        intent.putExtra("Sensor", iSensor);
        intent.putExtra("Cadena", sCadena);
        sendBroadcast(intent);
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        switch (sensorEvent.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                sMsgAccelerometer = "A: " + df.format(sensorEvent.values[0]) + " "
                        + df.format(sensorEvent.values[1]) + " "
                        + df.format(sensorEvent.values[2]);
                procesarDatosSensados(Sensor.TYPE_ACCELEROMETER, sensorEvent.timestamp, sensorEvent.values);
                break;
            case Sensor.TYPE_GYROSCOPE:
                sMsgGyroscope = "G: " + df.format(sensorEvent.values[0]) + " "
                        + df.format(sensorEvent.values[1]) + " "
                        + df.format(sensorEvent.values[2]);
                procesarDatosSensados(Sensor.TYPE_GYROSCOPE, sensorEvent.timestamp, sensorEvent.values);
                break;
            case Sensor.TYPE_PRESSURE:
                sMsgBarometer = "B: " + df.format(sensorEvent.values[0]);
                procesarDatosSensados(Sensor.TYPE_PRESSURE, sensorEvent.timestamp, sensorEvent.values);
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
            case Sensor.TYPE_GYROSCOPE:
                dataGyroscope[iPosDataGyroscope].setData(timeStamp, values);
                iPosDataGyroscope = (iPosDataGyroscope + 1) % iTamBuffer;
                break;
            case Sensor.TYPE_PRESSURE:
                dataBarometer[iPosDataBarometer].setData(timeStamp, values);
                iPosDataBarometer = (iPosDataBarometer + 1) % iTamBuffer;
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