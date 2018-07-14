package com.example.drp.tracedemo;

public class MyLocationPoint {
    double latitude, longitude;
    String addrStr, locationDescribe;

    public MyLocationPoint(double latitude, double longitude, String addrStr, String locationDescribe) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.addrStr = addrStr;
        this.locationDescribe = locationDescribe;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public String getAddrStr() {
        return addrStr;
    }

    public void setAddrStr(String addrStr) {
        this.addrStr = addrStr;
    }

    public String getLocationDescribe() {
        return locationDescribe;
    }

    public void setLocationDescribe(String locationDescribe) {
        this.locationDescribe = locationDescribe;
    }

    @Override
    public String toString() {
        return "{\"latitude\":" + "\"" + latitude + "\""
                + ",\"longitude\":" + "\"" + longitude + "\""
                + ",\"addrStr\":" + "\"" + addrStr + "\""
                + ",\"locationDescribe\":" + "\"" + locationDescribe + "\"}";
    }
}
