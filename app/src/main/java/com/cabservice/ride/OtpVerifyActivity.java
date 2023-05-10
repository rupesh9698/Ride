package com.cabservice.ride;

import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.chaos.view.PinView;
import com.google.android.gms.auth.api.phone.SmsRetriever;
import com.google.android.gms.auth.api.phone.SmsRetrieverClient;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;

public class OtpVerifyActivity extends AppCompatActivity implements SMSReceiver.OTPReceiveListener {

    String code;
    ProgressBar progressBar;
    PinView otpPv;

    private SMSReceiver smsReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_otp_verify);

        TextView phoneNumber = findViewById(R.id.phoneNumber);
        progressBar = findViewById(R.id.progressBar);
        otpPv = findViewById(R.id.otpPv);
        otpPv.setAnimationEnable(true);

        phoneNumber.setText(getIntent().getStringExtra("phone"));
        String verificationId = getIntent().getStringExtra("verificationId");

        otpPv.requestFocus();

        startSMSListener();

        otpPv.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.toString().length() == 6) {
                    code = s.toString();
                    try {
                        progressBar.setVisibility(View.VISIBLE);
                        PhoneAuthCredential phoneAuthCredential = PhoneAuthProvider.getCredential(verificationId, code);
                        FirebaseAuth.getInstance().signInWithCredential(phoneAuthCredential)
                                .addOnCompleteListener(task1 -> {
                                    if (task1.isSuccessful()) {

                                        FirebaseUser user = task1.getResult().getUser();

                                        assert user != null;
                                        HashMap<String, Object> hashMap = new HashMap<>();
                                        hashMap.put("phone", user.getPhoneNumber());
                                        hashMap.put("uid", user.getUid());

                                        DatabaseReference reference = FirebaseDatabase.getInstance().getReference("Users");
                                        reference.child(user.getUid()).updateChildren(hashMap).addOnCompleteListener(task2 -> {
                                            if (task2.isSuccessful()) {

                                                Intent intent = new Intent(OtpVerifyActivity.this, RideActivity.class);
                                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                                startActivity(intent);
                                                finish();
                                            }
                                        });
                                    } else {
                                        progressBar.setVisibility(View.GONE);
                                        showToast("Invalid OTP..!!");
                                        otpPv.setText(null);
                                    }
                                });
                    }
                    catch (Exception e) {
                        otpPv.setText(null);
                        showToast("" + e.getMessage());
                    }
                }
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
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

    private void startSMSListener() {
        try {
            smsReceiver = new SMSReceiver();
            smsReceiver.setOTPListener(this);

            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(SmsRetriever.SMS_RETRIEVED_ACTION);
            this.registerReceiver(smsReceiver, intentFilter);

            SmsRetrieverClient client = SmsRetriever.getClient(this);

            Task<Void> task = client.startSmsRetriever();
            task.addOnSuccessListener(aVoid -> {
                // API successfully started
            });

            task.addOnFailureListener(e -> {
                // Fail to start API
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onOTPReceived(String otp) {

        otpPv.setText(otp.substring(0, 6));

        if (smsReceiver != null) {
            unregisterReceiver(smsReceiver);
            smsReceiver = null;
        }
    }


    @Override
    public void onOTPReceivedError(String error) {
        showToast("" + error);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (smsReceiver != null) {
            unregisterReceiver(smsReceiver);
        }
    }
}