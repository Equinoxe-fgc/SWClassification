package com.equinoxe.swclassification;

import static java.lang.Math.round;

public class SensorData {
    private final static float SATURATE_VALUE = 2.0F;

    private long []timeStamp = new long[1];
    private float vX;
    private float vY;
    private float vZ;
    private double dModule;


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

    public void deleteGravityBias() {
        vX = vX / 9.8F;
        vY = vY / 9.8F;
        vZ = vZ / 9.8F;
    }

    public void saturate() {
            if (vX > SATURATE_VALUE)
                vX = SATURATE_VALUE;
            else if (vX < -SATURATE_VALUE)
                vX = -SATURATE_VALUE;

        if (vY > SATURATE_VALUE)
            vY = SATURATE_VALUE;
        else if (vY < -SATURATE_VALUE)
            vY = -SATURATE_VALUE;

        if (vZ > SATURATE_VALUE)
            vZ = SATURATE_VALUE;
        else if (vZ < -SATURATE_VALUE)
            vZ = -SATURATE_VALUE;
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

    public float getX() {
        return vX;
    }

    public float getY() {
        return vY;
    }

    public float getZ() {
        return vZ;
    }

    public double getModule() {
        return dModule;
    }
}

