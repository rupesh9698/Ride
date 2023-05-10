package com.cabservice.ride;

public class ModelHistory {

    final String rideId, time;

    public ModelHistory(String rideId, String time) {
        this.rideId = rideId;
        this.time = time;
    }

    public String getRideId() {
        return rideId;
    }

    public String getTime() {
        return time;
    }
}
