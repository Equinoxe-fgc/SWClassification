package com.equinoxe.swclassification;

import static java.lang.Math.round;

public class SensorData {
    private long []timeStamp = new long[1];
    private float v1;
    private float v2;
    private float v3;
    private double dModule;
    private float fRange;
    private float fFactor;

    public SensorData (float fRange) {
        this.fRange = fRange;

        fFactor = 255.0f / (fFactor - (-fFactor));
    }

    public void setData(long timeStamp, float []values) {
        this.timeStamp[0] = timeStamp;
        this.v1 = values[0];
        // Se tiene solo un valor si es el HR. Se ponen los otros dos a 0
        if (values.length > 1) {
            this.v2 = values[1];
            this.v3 = values[2];
        } else {
            this.v2 = 0.0f;
            this.v3 = 0.0f;
        }
    }

    public double calculateModuleGravity() {
        double v1G = v1 / 9.8;
        double v2G = v2 / 9.8;
        double v3G = v3 / 9.8;
        dModule = Math.sqrt(v1G*v1G + v2G*v2G + v3G*v3G);

        return dModule;
    }

    public double calculateModule() {
        dModule = Math.sqrt(v1*v1 + v2*v2 + v3*v3);

        return dModule;
    }

    public long getTimeStamp() {
        return timeStamp[0];
    }

    public float getV1() {
        return v1;
    }

    public float getV2() {
        return v2;
    }

    public float getV3() {
        return v3;
    }

    public double getModule() {
        return dModule;
    }

    public int getQuantizeValue() {
        return 0xff000000 | ((getQuantizedValue(v1) << 16) & 0xff0000) | ((getQuantizedValue(v2) << 8) & 0xff00) | ((getQuantizedValue(v3)));
    }

    private int getQuantizedValue (float valueIn) {
        int valueOut = Math.round((valueIn - (-fRange)) * fFactor);
        return valueOut;
    }
}

