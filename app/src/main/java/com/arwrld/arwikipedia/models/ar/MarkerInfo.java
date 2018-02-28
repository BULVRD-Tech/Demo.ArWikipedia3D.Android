package com.arwrld.arwikipedia.models.ar;

import android.content.Intent;
import android.location.Location;

import com.arwrld.arwikipedia.models.Geosearch;

public class MarkerInfo {
    public Geosearch geosearch;

    //Additional variables
    private float distance;
    private boolean inRange;
    private float[] zeroMatrix;
//    private ObjectRenderer virtualObject;

    public MarkerInfo(Geosearch parseObject) {
        this.geosearch = parseObject;
    }

    public float getDistance() {
        return distance;
    }

    public void setDistance(Float distance) {
        this.distance = distance;
    }

    public boolean getInRange() {
        return inRange;
    }

    public void setInRange(Boolean inRange) {
        this.inRange = inRange;
    }

    public float[] getZeroMatrix() {
        return zeroMatrix;
    }

    public void setZeroMatrix(float[] zeroMatrix) {
        this.zeroMatrix = zeroMatrix;
    }

    public Location returnLocation(){
        Location location = new Location(Integer.toString(geosearch.getPageid()));
        location.setLatitude(geosearch.getLat());
        location.setLongitude(geosearch.getLon());
        location.setAltitude(0);
        location.setBearing(0f);
        return location;
    }
}
