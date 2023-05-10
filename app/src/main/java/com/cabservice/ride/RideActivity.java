package com.cabservice.ride;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.LocationListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Objects;

public class RideActivity extends AppCompatActivity implements LocationListener {

    private FirebaseAuth firebaseAuth;
    private FirebaseUser firebaseUser;
    private String user_id;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ride);

        firebaseAuth = FirebaseAuth.getInstance();
        firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        user_id = Objects.requireNonNull(firebaseAuth.getCurrentUser()).getUid();

        Button customer = findViewById(R.id.customer);
        Button driver = findViewById(R.id.driver);

        checkUserStatus();
        grantGpsPermission();
        checkLocationIsEnabledOrNot();

        customer.setOnClickListener(v -> {

            String cordRole = "customer";
            String cordChild = "Customers";
            saveBasicData(cordRole, cordChild);
            startActivity(new Intent(RideActivity.this, CustomerMapActivity.class));
            RideActivity.this.finish();
        });

        driver.setOnClickListener(v -> {

            String cordRole = "driver";
            String cordChild = "Drivers";
            saveBasicData(cordRole, cordChild);
            startActivity(new Intent(RideActivity.this, DriverMapActivity.class));
            RideActivity.this.finish();
        });
    }

    private void showToast(String toast_message) {
        LayoutInflater inflater = getLayoutInflater();
        View layout = inflater.inflate(R.layout.custom_toast_layout,
                findViewById(R.id.toast_layout_root));

        TextView text = layout.findViewById(R.id.text);
        text.setText(toast_message);

        Toast toast = new Toast(getApplicationContext());
        toast.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
        toast.setDuration(Toast.LENGTH_LONG);
        toast.setView(layout);
        toast.show();
    }

    private void saveBasicData(String cordRole, String cordChild) {

        ProgressDialog progressDialog = new ProgressDialog(RideActivity.this);
        progressDialog.setMessage("Please Wait");
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.show();

        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference().child("RideUsers").child(cordChild).child(user_id);

        HashMap hashMap = new HashMap();
        hashMap.put("phone", firebaseUser.getPhoneNumber());
        hashMap.put("uid", user_id);

        userRef.updateChildren(hashMap).addOnCompleteListener(task -> {

            if (task.isSuccessful()) {
                SharedPreferences sharedPreferences = getSharedPreferences("role", MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString("myRole", cordRole);
                editor.apply();
            } else {
                showToast("" + Objects.requireNonNull(task.getException()).getMessage());
            }
            progressDialog.dismiss();
        }).addOnFailureListener(e -> {
            progressDialog.dismiss();
            showToast("" + e.getMessage());
        });
    }

    private void checkLocationIsEnabledOrNot() {
        LocationManager lm = (LocationManager) RideActivity.this.getSystemService(Context.LOCATION_SERVICE);
        boolean gpsEnabled = false;
        boolean networkEnabled = false;

        try {
            gpsEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            networkEnabled = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (!gpsEnabled && !networkEnabled) {
            new AlertDialog.Builder(RideActivity.this)
                    .setTitle("Enable GPS Service")
                    .setCancelable(false)
                    .setPositiveButton("Enable", (dialog, which) -> startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))).setNegativeButton("Cancel", null)
                    .show();
        }
    }

    private void grantGpsPermission() {
        if (ContextCompat.checkSelfPermission(RideActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(RideActivity.this,
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(RideActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, 300);
        }
    }

    private void checkUserStatus() {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user == null) {
            startActivity(new Intent(RideActivity.this, MainActivity.class));
            RideActivity.this.finish();
        }
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {

    }
}