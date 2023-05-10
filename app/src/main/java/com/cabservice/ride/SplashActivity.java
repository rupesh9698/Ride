package com.cabservice.ride;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

@SuppressLint("CustomSplashScreen")
public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        checkUserStatus();
    }

    private void checkUserStatus() {

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        if (user != null) {
            loadMyRole();
        } else {
            Intent mainIntent = new Intent(SplashActivity.this, MainActivity.class);
            mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(mainIntent);
            finish();
        }
    }

    private void loadMyRole() {

        SharedPreferences sharedPreferences = getSharedPreferences("role", MODE_PRIVATE);
        String myRole = sharedPreferences.getString("myRole", "default");

        switch (myRole) {
            case "customer": {
                Intent customerIntent = new Intent(SplashActivity.this, CustomerMapActivity.class);
                customerIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(customerIntent);
                finish();
                break;
            }
            case "driver": {
                Intent driverIntent = new Intent(SplashActivity.this, DriverMapActivity.class);
                driverIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(driverIntent);
                finish();
                break;
            }
            default: {
                Intent defaultIntent = new Intent(SplashActivity.this, RideActivity.class);
                defaultIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(defaultIntent);
                finish();
                break;
            }
        }
    }
}