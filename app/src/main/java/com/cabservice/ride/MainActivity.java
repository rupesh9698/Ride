package com.cabservice.ride;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.credentials.Credential;
import com.google.android.gms.auth.api.credentials.Credentials;
import com.google.android.gms.auth.api.credentials.CredentialsApi;
import com.google.android.gms.auth.api.credentials.HintRequest;
import com.google.firebase.FirebaseException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.hbb20.CountryCodePicker;

import java.util.HashMap;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private CountryCodePicker ccp;
    private EditText phoneEt;
    private ProgressBar progressBar;

    private static final int CREDENTIAL_PICKER_REQUEST = 10;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ccp = findViewById(R.id.ccp);
        phoneEt = findViewById(R.id.phoneEt);
        progressBar = findViewById(R.id.progressBar);

        mAuth = FirebaseAuth.getInstance();

        HintRequest hintRequest = new HintRequest.Builder()
                .setPhoneNumberIdentifierSupported(true)
                .build();

        PendingIntent intent = Credentials.getClient(MainActivity.this).getHintPickerIntent(hintRequest);
        try {
            startIntentSenderForResult(intent.getIntentSender(), CREDENTIAL_PICKER_REQUEST, null, 0, 0, 0,new Bundle());
        }
        catch (IntentSender.SendIntentException e) {
            e.printStackTrace();
        }

        phoneEt.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.toString().length() == 10) {
                    try {
                        String countryCode = ccp.getSelectedCountryCode();
                        String phoneNumber = phoneEt.getText().toString().trim();
                        String completePhoneNumber = "+" + countryCode + phoneNumber;
                        otpSend(phoneNumber, completePhoneNumber);
                    }
                    catch (Exception e) {
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

    private void otpSend(String phoneNumber, String completePhoneNumber) {

        progressBar.setVisibility(View.VISIBLE);

        PhoneAuthProvider.OnVerificationStateChangedCallbacks mCallbacks = new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

            @Override
            public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
                signInWithPhoneAuthCredential(credential);
            }

            @Override
            public void onVerificationFailed(@NonNull FirebaseException e) {
                progressBar.setVisibility(View.GONE);
                showToast("" + e.getLocalizedMessage());
            }

            @Override
            public void onCodeSent(@NonNull String verificationId,
                                   @NonNull PhoneAuthProvider.ForceResendingToken token) {
                showToast("OTP Send Successfully to " + phoneNumber);
                Intent intent = new Intent(MainActivity.this, OtpVerifyActivity.class);
                intent.putExtra("phone", phoneEt.getText().toString().trim());
                intent.putExtra("completePhoneNo", completePhoneNumber);
                intent.putExtra("verificationId", verificationId);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }
        };

        PhoneAuthOptions options = PhoneAuthOptions.newBuilder(mAuth)
                .setPhoneNumber(completePhoneNumber)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(this)
                .setCallbacks(mCallbacks)
                .build();
        PhoneAuthProvider.verifyPhoneNumber(options);
    }

    private void signInWithPhoneAuthCredential(PhoneAuthCredential credential) {
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {

                        FirebaseUser user = task.getResult().getUser();

                        assert user != null;
                        HashMap<Object, String> hashMap = new HashMap<>();
                        hashMap.put("phone", user.getPhoneNumber());
                        hashMap.put("uid", user.getUid());

                        DatabaseReference reference = FirebaseDatabase.getInstance().getReference("Users");
                        reference.child(user.getUid()).setValue(hashMap).addOnCompleteListener(task1 -> {
                            if (task1.isSuccessful()) {
                                Intent intent = new Intent(MainActivity.this, RideActivity.class);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(intent);
                                finish();
                            }
                        });
                    } else {
                        showToast("" + task.getException());
                    }
                });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CREDENTIAL_PICKER_REQUEST && resultCode == RESULT_OK) {
            Credential credentials = data.getParcelableExtra(Credential.EXTRA_KEY);
            phoneEt.setText(credentials.getId().substring(credentials.getId().length() - 10));
        }
        else if (requestCode == CREDENTIAL_PICKER_REQUEST && resultCode == CredentialsApi.ACTIVITY_RESULT_NO_HINTS_AVAILABLE) {
            showToast("No phone numbers found");
        }
    }
}