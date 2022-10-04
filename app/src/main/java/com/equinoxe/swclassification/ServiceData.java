package com.equinoxe.swclassification;

import android.app.Service;
import android.content.Intent;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.equinoxe.swclassification.ml.ModeloKerasSequencialBin;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.FileOutputStream;
import java.io.IOError;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

public class ServiceData extends Service implements SensorEventListener {
    private final static boolean SENSORS_ON = true;
    private final static boolean SENSORS_OFF = false;
    private static final int SAMPLES_PER_SECOND = 32;
    private static final int PERIOD = 1000000 / SAMPLES_PER_SECOND;
    private static final int WINDOW_TIME = (int)TimeUnit.SECONDS.toMillis(3);
    private static final int CLASSIFY_INTERVAL_TIME = (int)TimeUnit.SECONDS.toMillis(1);

    private static final float CNN_MAX_VALUE_IN = 2.0F;


    private float []fMinValues = {10000.0F, 10000.0F, 10000.0F};
    private float []fMaxValues = {-10000.0F, -10000.0F, -10000.0F};

    float fRangeAccelerometer;
    float fAdaptationFactor;

    private ServiceHandler mServiceHandler;
    private SensorManager sensorManager;

    private String sMsgAccelerometer, sMsgGyroscope, sMsgBarometer;
    private String sMsg;
    private String sMsgMinValues, sMsgMaxValues;

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

    private Timer timerClassify;

    Sensor sensorAccelerometer = null;
    /*Sensor sensorGyroscope = null;
    Sensor sensorBarometer = null;*/

    /*ModeloKerasSequencialBin model;
    TensorBuffer inputFeature0;*/

    FileOutputStream fOutDataLog;

    @Override
    public void onCreate() {
        HandlerThread thread = new HandlerThread("ServiceData", HandlerThread.MIN_PRIORITY);
        thread.start();

        // Get the HandlerThread's Looper and use it for our Handler
        Looper mServiceLooper = thread.getLooper();
        mServiceHandler = new com.equinoxe.swclassification.ServiceData.ServiceHandler(mServiceLooper);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        df = new DecimalFormat("##.###");
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
                /*case Sensado.GIROSCOPO:
                    publishSensorValues(Sensado.GIROSCOPO, sMsgGyroscope);
                    break;
                case Sensado.BAROMETER:
                    publishSensorValues(Sensado.BAROMETER, sMsgBarometer);
                    break;*/
                case Sensado.MSG:
                    publishSensorValues(Sensado.MSG, sMsg);
                    break;
                case Sensado.MIN_VALUE:
                    publishSensorValues(Sensado.MIN_VALUE, sMsgMinValues);
                    break;
                case Sensado.MAX_VALUE:
                    publishSensorValues(Sensado.MAX_VALUE, sMsgMaxValues);
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

        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmm", Locale.UK);
        String currentDateandTime = sdf.format(new Date());
        try {
            fOutDataLog = new FileOutputStream(Environment.getExternalStorageDirectory() + "/" + Build.MODEL + "_" + currentDateandTime + "_DataLog.txt", true);
        } catch (IOException e) {}

        final TimerTask timerTaskUpdateData = new TimerTask() {
            public void run() {
                Message msgAccelerometer = mServiceHandler.obtainMessage();
                msgAccelerometer.arg1 = Sensado.ACELEROMETRO;
                mServiceHandler.sendMessage(msgAccelerometer);

                /*Message msgGyroscope = mServiceHandler.obtainMessage();
                msgGyroscope.arg1 = Sensado.GIROSCOPO;
                mServiceHandler.sendMessage(msgGyroscope);

                Message msgBarometer = mServiceHandler.obtainMessage();
                msgBarometer.arg1 = Sensado.BAROMETER;
                mServiceHandler.sendMessage(msgBarometer);*/
            }
        };
        timerUpdateData = new Timer();
        timerUpdateData.scheduleAtFixedRate(timerTaskUpdateData, Sensado.AMBIENT_INTERVAL_MS / 2, Sensado.AMBIENT_INTERVAL_MS);

        final TimerTask timerTaskClassify = new TimerTask() {
            public void run() {
                // El muestreo es cada 32ms. Se tarda unos 20ms en copiar el buffer
                // Debería dar tiempoa copiarlo sin necesidad de parar el sensado

                //long time1 = System.currentTimeMillis();
                //controlSensors(SENSORS_OFF);
                byte[] byteArray= SensorDataArray2ByteArray(dataAccelerometer);
                //controlSensors(SENSORS_ON);
                //long time2 = System.currentTimeMillis();
                //long elapsedTime = time2 - time1;

                /*sMsgMaxValues = "Miliseconds: " + elapsedTime;
                Message msgMaxValue = mServiceHandler.obtainMessage();
                msgMaxValue.arg1 = Sensado.MAX_VALUE;
                mServiceHandler.sendMessage(msgMaxValue);*/

                ByteBuffer byteBuffer = ByteBuffer.wrap(byteArray);
                int iClass = getCNNOutput(byteBuffer);

                Message msgMsg = mServiceHandler.obtainMessage();
                msgMsg.arg1 = Sensado.MSG;
                sMsg = createMessageClass(iClass);
                mServiceHandler.sendMessage(msgMsg);
            }
        };
        timerClassify = new Timer();
        timerClassify.scheduleAtFixedRate(timerTaskClassify, CLASSIFY_INTERVAL_TIME, CLASSIFY_INTERVAL_TIME);

        sensorAccelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        fRangeAccelerometer = sensorAccelerometer.getMaximumRange();
        fAdaptationFactor = CNN_MAX_VALUE_IN / fRangeAccelerometer;
        /*sensorGyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        sensorBarometer = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);*/

        iTamBuffer = SAMPLES_PER_SECOND * WINDOW_TIME/1000 + 1;
        dataAccelerometer = new SensorData[iTamBuffer];
        /*dataGyroscope = new SensorData[iTamBuffer];
        if (sensorBarometer != null)
            dataBarometer = new SensorData[iTamBuffer];*/

        for (int i = 0; i < iTamBuffer; i++) {
            dataAccelerometer[i] = new SensorData();
            /*dataGyroscope[i] = new SensorData(sensorGyroscope.getMaximumRange());
            if (sensorBarometer != null)
                dataBarometer[i] = new SensorData(sensorBarometer.getMaximumRange());*/
        }

        controlSensors(SENSORS_ON);

        return START_NOT_STICKY;
    }

    String createMessageClass(int iClass) {
        String sMsg = "";

        switch (iClass) {
            case 0:
                sMsg = Sensado.CLASS_OTHER;
                break;
            case 1:
                sMsg = Sensado.CLASS_BRUSH;
                break;
        }

        return sMsg;
    }

    public byte[] SensorDataArray2ByteArray(@NonNull SensorData[] values){
        SensorData value;
        int iPos;
        ByteBuffer buffer = ByteBuffer.allocate(3 * values.length * Float.BYTES);

        // Va tomando las muestras de la más nueva a la más antigua de forma circular
        for (int i = 0; i < values.length; i++) {
            iPos =  (iPosDataAccelerometer - i - 1 >= 0)?(iPosDataAccelerometer - i - 1):(values.length + iPosDataAccelerometer - i - 1);
            value = adaptValues(values[iPos]);

            buffer.putFloat(value.getX());
            buffer.putFloat(value.getY());
            buffer.putFloat(value.getZ());
        }

        return buffer.array();
    }


    private SensorData adaptValues(@NonNull SensorData value){
        SensorData valueAdaptado = new SensorData();
        float ejes[] = new float[3];

        ejes[0] = value.getX() * fAdaptationFactor;
        ejes[1] = value.getY() * fAdaptationFactor;
        ejes[2] = value.getZ() * fAdaptationFactor;

        valueAdaptado.setData(value.getTimeStamp(), ejes);

        return valueAdaptado;
    }

    private float adaptValue(float value) {
        return value * fAdaptationFactor;
    }

    private int getCNNOutput(ByteBuffer byteBuffer) {
        int iFinalClass = 0;

        try {
            ModeloKerasSequencialBin model = ModeloKerasSequencialBin.newInstance(this);

            // Creates inputs for reference.
            TensorBuffer inputFeature0 = TensorBuffer.createFixedSize(new int[]{1, 97, 3}, DataType.FLOAT32);
            inputFeature0.loadBuffer(byteBuffer);

            // Runs model inference and gets result.
            ModeloKerasSequencialBin.Outputs outputs = model.process(inputFeature0);
            TensorBuffer outputFeature0 = outputs.getOutputFeature0AsTensorBuffer();

            float[] data = outputFeature0.getFloatArray();
            iFinalClass = getFinalClass(data);

            // Releases model resources if no longer used.
            model.close();
        } catch (Exception e) {
            Log.e(e.getLocalizedMessage(), e.getMessage());
        }

        return iFinalClass;
    }


    private int getFinalClass(float []weights) {
        int iFinalClass = 0;
        float fMaxValue = weights[0];

        for (int iPos= 1; iPos < weights.length; iPos++) {
            if (weights[iPos] > fMaxValue) {
                fMaxValue = weights[iPos];
                iFinalClass = iPos;
            }
        }

        return iFinalClass;
    }

    private void controlSensors(boolean bSensors_ON) {
            if (bSensors_ON) {
                sensorManager.registerListener(this, sensorAccelerometer, PERIOD);
                //sensorManager.registerListener(this, sensorAccelerometer, SensorManager.SENSOR_DELAY_GAME);
                /*sensorManager.registerListener(this, sensorGyroscope, SensorManager.SENSOR_DELAY_GAME);
                if (sensorBarometer != null)
                    sensorManager.registerListener(this, sensorBarometer, SensorManager.SENSOR_DELAY_GAME);*/
            }
            else {
                sensorManager.unregisterListener(this, sensorAccelerometer);
                /*sensorManager.unregisterListener(this, sensorGyroscope);
                if (sensorBarometer != null)
                    sensorManager.unregisterListener(this, sensorBarometer);*/
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
                sMsgAccelerometer = "A: " + df.format(adaptValue(sensorEvent.values[0])) + " "
                        + df.format(adaptValue(sensorEvent.values[1])) + " "
                        + df.format(adaptValue(sensorEvent.values[2]));
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
                SensorData data = dataAccelerometer[iPosDataAccelerometer];

                // Si es el acelerómetro se le quita el sesgo de la gravedad y se satura
                dataAccelerometer[iPosDataAccelerometer].deleteGravityBias();
                dataAccelerometer[iPosDataAccelerometer].saturate();

                iPosDataAccelerometer = (iPosDataAccelerometer + 1) % iTamBuffer;

                String sCadenaFichero =  "" + timeStamp + " " + data.getX() + " " + data.getY() + " " + data.getZ() + "\n";
                try {
                    fOutDataLog.write(sCadenaFichero.getBytes());
                } catch (Exception e) {}

                // comprobarValoresMinMax(values);
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

    private void comprobarValoresMinMax(float []values) {
        boolean bMaxChange = false;
        boolean bMinChange = false;

        for (int i = 0; i < 3; i++) {
            if (values[i] > fMaxValues[i]) {
                fMaxValues[i] = values[i];
                bMaxChange = true;
            }

            if (values[i] < fMinValues[i]) {
                fMinValues[i] = values[i];
                bMinChange = true;
            }
        }

        if (bMaxChange) {
            sMsgMaxValues = "A Max: " + df.format(fMaxValues[0]) + " "
                    + df.format(fMaxValues[1]) + " "
                    + df.format(fMaxValues[2]);

            Message msgMaxValue = mServiceHandler.obtainMessage();
            msgMaxValue.arg1 = Sensado.MAX_VALUE;
            mServiceHandler.sendMessage(msgMaxValue);
        }

        if (bMinChange) {
            sMsgMinValues = "A Min: " + df.format(fMinValues[0]) + " "
                    + df.format(fMinValues[1]) + " "
                    + df.format(fMinValues[2]);

            Message msgMinValue = mServiceHandler.obtainMessage();
            msgMinValue.arg1 = Sensado.MIN_VALUE;
            mServiceHandler.sendMessage(msgMinValue);
        }
    }


    @Override
    public void onDestroy() {
        super.onDestroy();

        controlSensors(SENSORS_OFF);

        try {
            fOutDataLog.close();
        } catch (Exception e) {}

        timerUpdateData.cancel();
        timerClassify.cancel();
        wakeLock.release();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}