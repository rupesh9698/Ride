package com.cabservice.ride;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.Nullable;

import com.firebase.geofire.GeoFire;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.Objects;

public class OnDriverActivityKilled extends Service {
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        String user_id = Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getUid();
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Available Drivers");
        GeoFire geoFire = new GeoFire(ref);
        geoFire.removeLocation(user_id);
        super.onTaskRemoved(rootIntent);
    }
}
