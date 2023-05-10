package com.cabservice.ride;

import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.AppCompatEditText;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class KycActivity extends AppCompatActivity {

    AppCompatEditText accountNumberEt, reEnterAccountNumberEt, ifscEt, recipientNameEt;
    AppCompatButton kycSaveBtn;
    String currentUserID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_kyc);

        accountNumberEt = findViewById(R.id.accountNumberEt);
        reEnterAccountNumberEt = findViewById(R.id.reEnterAccountNumberEt);
        ifscEt = findViewById(R.id.ifscEt);
        recipientNameEt = findViewById(R.id.recipientNameEt);
        kycSaveBtn = findViewById(R.id.kycSaveBtn);

        currentUserID = FirebaseAuth.getInstance().getCurrentUser().getUid();

        getInfo();

        kycSaveBtn.setOnClickListener(v -> {

            String accountNumber = accountNumberEt.getText().toString();
            String reEnterAccountNumber = reEnterAccountNumberEt.getText().toString();
            String ifsc = ifscEt.getText().toString().toUpperCase(Locale.ROOT);
            String recipientName = recipientNameEt.getText().toString().toUpperCase(Locale.ROOT);

            if (accountNumber.equals("")) {
                showToast("Enter Account Number");
            }
            else if (reEnterAccountNumber.equals("")) {
                showToast("Re-enter Account Number");
            }
            else if (!accountNumber.equals(reEnterAccountNumber)) {
                showToast("Account Number should be same");
            }
            else if (ifsc.equals("")) {
                showToast("Enter IFSC Code");
            }
            else if (recipientName.equals("")) {
                showToast("Enter Recipient Name");
            }
            else {
                DatabaseReference reference = FirebaseDatabase.getInstance().getReference("RideUsers");

                Map kycMap = new HashMap();
                kycMap.put("kyc", true);

                reference.child("Drivers").child(currentUserID).updateChildren(kycMap).addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Map kycDetailsMap = new HashMap();
                        kycDetailsMap.put("account number", accountNumber);
                        kycDetailsMap.put("ifsc", ifsc);
                        kycDetailsMap.put("recipient name", recipientName);
                        reference.child("Drivers").child(currentUserID).child("KYC").updateChildren(kycDetailsMap).addOnCompleteListener(task1 -> {
                            if (task1.isSuccessful()) {
                                showToast("Details Saved Successfully");
                            }
                        });
                    }
                });
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

    private void getInfo() {

        DatabaseReference driverDatabase = FirebaseDatabase.getInstance().getReference("RideUsers").child("Drivers").child(currentUserID).child("KYC");
        driverDatabase.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists() && snapshot.getChildrenCount() > 0) {
                    Map<String, Object> map = (Map<String, Object>) snapshot.getValue();
                    assert map != null;
                    if (map.get("account number") != null) {
                        String accountNumber = Objects.requireNonNull(map.get("account number")).toString();
                        accountNumberEt.setText(accountNumber);
                        reEnterAccountNumberEt.setText(accountNumber);
                    }

                    if (map.get("ifsc") != null) {
                        String ifsc = Objects.requireNonNull(map.get("ifsc")).toString();
                        ifscEt.setText(ifsc);
                    }

                    if (map.get("recipient name") != null) {
                        String recipientName = Objects.requireNonNull(map.get("recipient name")).toString();
                        recipientNameEt.setText(recipientName);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }
}