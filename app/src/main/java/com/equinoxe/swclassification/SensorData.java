package com.equinoxe.swclassification;

import static java.lang.Math.round;

public class SensorData {
    private long []timeStamp = new long[1];
    private float vX;
    private float vY;
    private float vZ;
    private double dModule;
    private float fRange;
    private float fFactor;

    public SensorData (float fRange) {
        this.fRange = fRange;

        fFactor = 255.0f / (fFactor - (-fFactor));
    }

    public void setData(long timeStamp, float []values) {
        this.timeStamp[0] = timeStamp;
        this.vX = values[0];
        // Se tiene solo un valor si es el HR. Se ponen los otros dos a 0
        if (values.length > 1) {
            this.vY = values[1];
            this.vZ = values[2];
        } else {
            this.vY = 0.0f;
            this.vZ = 0.0f;
        }
    }

    public double calculateModuleGravity() {
        double vXG = vX / 9.8;
        double vYG = vY / 9.8;
        double vZG = vZ / 9.8;
        dModule = Math.sqrt(vXG*vXG + vYG*vYG + vZG*vZG);

        return dModule;
    }

    public double calculateModule() {
        dModule = Math.sqrt(vX*vX + vY*vY + vZ*vZ);

        return dModule;
    }

    public long getTimeStamp() {
        return timeStamp[0];
    }

    public float getV1() {
        return vX;
    }

    public float getV2() {
        return vY;
    }

    public float getV3() {
        return vZ;
    }

    public double getModule() {
        return dModule;
    }

    // TODO: Asegurarse que la CNN está entrenada con valores entre 0 y 255 en las 3 componentes X, Y y Z
    public int getQuantizeValue() {
        return 0xff000000 | ((getQuantizedValue(vX) << 16) & 0xff0000) | ((getQuantizedValue(vY) << 8) & 0xff00) | ((getQuantizedValue(vZ)));
    }

    private int getQuantizedValue (float valueIn) {
        int valueOut = Math.round((valueIn - (-fRange)) * fFactor);
        return valueOut;
    }
}

