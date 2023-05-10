package com.cabservice.ride;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.razorpay.Checkout;
import com.razorpay.PaymentResultListener;
import com.squareup.picasso.Picasso;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@SuppressLint("SetTextI18n")
public class HistoryActivity extends AppCompatActivity implements PaymentResultListener {

    private final ArrayList resultsHistory = new ArrayList<ModelHistory>();
    private String customerOrDriver, userId, balance;
    private RecyclerView.Adapter mHistoryAdapter;
    private ProgressDialog progressDialog;
    private TextView balanceTv;
    private Boolean kycStatus;
    private Dialog dialog;
    private Button payBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        LinearLayoutCompat paymentLl = findViewById(R.id.paymentLl);
        balanceTv = findViewById(R.id.balanceTv);
        payBtn = findViewById(R.id.payBtn);
        dialog = new Dialog(HistoryActivity.this);
        progressDialog = new ProgressDialog(HistoryActivity.this);

        RecyclerView mHistoryRecyclerView = findViewById(R.id.historyRecyclerView);
        mHistoryRecyclerView.setNestedScrollingEnabled(false);
        mHistoryRecyclerView.setHasFixedSize(true);
        LinearLayoutManager mHistoryLayoutManager = new LinearLayoutManager(HistoryActivity.this);

        mHistoryLayoutManager.setStackFromEnd(true);
        mHistoryLayoutManager.setReverseLayout(true);

        mHistoryRecyclerView.setLayoutManager(mHistoryLayoutManager);
        mHistoryAdapter = new AdapterHistory(getDataSetHistory());
        mHistoryRecyclerView.setAdapter(mHistoryAdapter);

        userId = Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getUid();

        customerOrDriver = getIntent().getExtras().getString("customerOrDriver");

        if (customerOrDriver.equals("Drivers")) {
            paymentLl.setVisibility(View.VISIBLE);
            DatabaseReference reference = FirebaseDatabase.getInstance().getReference("RideUsers");
            reference.child("Drivers").child(Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getUid()).addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    if (dataSnapshot.child("balance").exists()) {
                        balance = Objects.requireNonNull(dataSnapshot.child("balance").getValue()).toString();
                        kycStatus = (Boolean) dataSnapshot.child("kyc").getValue();
                        balanceTv.setText("Rs " + balance + "/-");
                        float balanceFloat = Float.parseFloat(balance);
                        if (balanceFloat < 0) {
                            payBtn.setVisibility(View.VISIBLE);
                            payBtn.setText("PAY");
                            payBtn.setOnClickListener(v -> {
                                progressDialog = new ProgressDialog(HistoryActivity.this);
                                progressDialog.setMessage("Processing");
                                progressDialog.setCanceledOnTouchOutside(false);
                                progressDialog.show();
                                String phone = Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getPhoneNumber();
                                int amount = (int) (Math.abs(balanceFloat) * 100);
                                Checkout checkout = new Checkout();
                                checkout.setKeyID("rzp_test_A2JXTpLOBHAhHp");
                                JSONObject object = new JSONObject();
                                try {
                                    assert phone != null;
                                    object.put("name", "Ride");
                                    object.put("description", "Payment Gateway");
                                    object.put("currency", "INR");
                                    object.put("amount", amount);
                                    object.put("prefill.contact", phone.substring(phone.length() - 10));
                                    checkout.open(HistoryActivity.this, object);
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                    progressDialog.dismiss();
                                    dialog.setContentView(R.layout.payment_result_dialog);
                                    dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                                    ImageView resultIv = dialog.findViewById(R.id.resultIv);
                                    Picasso.get().load(R.drawable.failed).into(resultIv);
                                    TextView resultTv = dialog.findViewById(R.id.resultTv);
                                    resultTv.setText("Payment Failed");
                                    Button okBtn = dialog.findViewById(R.id.okBtn);
                                    dialog.setCanceledOnTouchOutside(false);
                                    okBtn.setOnClickListener(v1 -> dialog.dismiss());
                                    dialog.show();
                                }
                            });
                        }
                        if (balanceFloat > 0) {
                            payBtn.setVisibility(View.VISIBLE);
                            payBtn.setText("WITHDRAW");
                            payBtn.setOnClickListener(v -> {
                                if (kycStatus) {
                                    dialog.setContentView(R.layout.custom_alert_dialog);
                                    dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                                    TextView alertDialogTitleTv = dialog.findViewById(R.id.alertDialogTitleTv);
                                    alertDialogTitleTv.setText("Withdraw Request");
                                    TextView alertDialogDescriptionTv = dialog.findViewById(R.id.alertDialogDescriptionTv);
                                    alertDialogDescriptionTv.setText("Do you want to transfer money in your bank account ?");
                                    AppCompatButton alertDialogNegativeBtn = dialog.findViewById(R.id.alertDialogNegativeBtn);
                                    alertDialogNegativeBtn.setText("NO");
                                    AppCompatButton alertDialogPositiveBtn = dialog.findViewById(R.id.alertDialogPositiveBtn);
                                    alertDialogPositiveBtn.setText("YES");
                                    dialog.setCanceledOnTouchOutside(false);
                                    alertDialogNegativeBtn.setOnClickListener(v1 -> dialog.dismiss());
                                    alertDialogPositiveBtn.setOnClickListener(v12 -> {
                                        dialog.dismiss();
                                        getInfo(balanceFloat);
                                    });
                                }
                                else {
                                    dialog.setContentView(R.layout.custom_alert_dialog);
                                    dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                                    TextView alertDialogTitleTv = dialog.findViewById(R.id.alertDialogTitleTv);
                                    alertDialogTitleTv.setText("Do KYC Now ?");
                                    TextView alertDialogDescriptionTv = dialog.findViewById(R.id.alertDialogDescriptionTv);
                                    alertDialogDescriptionTv.setText("KYC has to be done to withdraw Money");
                                    AppCompatButton alertDialogNegativeBtn = dialog.findViewById(R.id.alertDialogNegativeBtn);
                                    alertDialogNegativeBtn.setText("NO");
                                    AppCompatButton alertDialogPositiveBtn = dialog.findViewById(R.id.alertDialogPositiveBtn);
                                    alertDialogPositiveBtn.setText("YES");
                                    dialog.setCanceledOnTouchOutside(false);
                                    alertDialogNegativeBtn.setOnClickListener(v1 -> dialog.dismiss());
                                    alertDialogPositiveBtn.setOnClickListener(v12 -> {
                                        Intent kycIntent = new Intent(HistoryActivity.this, KycActivity.class);
                                        startActivity(kycIntent);
                                        dialog.dismiss();
                                    });
                                }
                                dialog.show();
                            });
                        }
                    }
                }
                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {}
            });
        }

        getUserHistoryIds();
    }

    private void showToast(String toast_message) {
        LayoutInflater inflater = getLayoutInflater();
        View layout = inflater.inflate(R.layout.custom_toast_layout, findViewById(R.id.toast_layout_root));

        TextView text = layout.findViewById(R.id.text);
        text.setText(toast_message);

        Toast toast = new Toast(getApplicationContext());
        toast.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
        toast.setDuration(Toast.LENGTH_LONG);
        toast.setView(layout);
        toast.show();
    }

    private void getInfo(float balanceFloat) {

        DatabaseReference driverDatabase = FirebaseDatabase.getInstance().getReference("RideUsers").child("Drivers").child(userId).child("KYC");
        driverDatabase.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists() && snapshot.getChildrenCount() > 0) {
                    Map<String, Object> map = (Map<String, Object>) snapshot.getValue();

                    String accountNumber = (String) map.get("account number");
                    String ifsc = (String) map.get("ifsc");
                    String recipientName = (String) map.get("recipient name");

                    createRequest(accountNumber, ifsc, recipientName, balanceFloat);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }

    private void createRequest(String accountNumber, String ifsc, String recipientName, float balanceFloat) {
        DatabaseReference reference = FirebaseDatabase.getInstance().getReference("Withdraw Requests");

        Map saveDetailsMap = new HashMap();
        saveDetailsMap.put("account number", accountNumber);
        saveDetailsMap.put("balance", "" + balanceFloat);
        saveDetailsMap.put("ifsc", ifsc);
        saveDetailsMap.put("recipient name", recipientName);

        reference.child(userId).updateChildren(saveDetailsMap).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                DatabaseReference reference1 = FirebaseDatabase.getInstance().getReference("RideUsers");

                Map balanceMap = new HashMap();
                balanceMap.put("balance", "0");

                reference1.child("Drivers").child(userId).updateChildren(balanceMap).addOnCompleteListener(task1 -> {
                    if (task1.isSuccessful()) {
                        showToast("We will credit money in your bank account soon");
                    }
                });
            }
        });
    }

    private void getUserHistoryIds() {
        DatabaseReference userHistoryDatabase = FirebaseDatabase.getInstance().getReference().child("RideUsers").child(customerOrDriver).child(userId).child("History");
        userHistoryDatabase.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    for (DataSnapshot history : snapshot.getChildren()) {
                        FetchRideInformation(history.getKey());
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }

    private void FetchRideInformation(String rideKey) {
        DatabaseReference historyDatabase = FirebaseDatabase.getInstance().getReference().child("History").child(rideKey);
        historyDatabase.addListenerForSingleValueEvent(new ValueEventListener() {
            @SuppressLint("NotifyDataSetChanged")
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String rideId = snapshot.getKey();
                    long timestamp = 0L;
                    for (DataSnapshot child : snapshot.getChildren()) {
                        if (Objects.equals(child.getKey(), "timestamp")) {
                            timestamp = Long.parseLong(Objects.requireNonNull(child.getValue()).toString());
                        }
                    }
                    ModelHistory obj = new ModelHistory(rideId, getDate(timestamp));
                    resultsHistory.add(obj);
                    mHistoryAdapter.notifyDataSetChanged();

                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }

    private String getDate(Long timestamp) {

        Calendar cal = Calendar.getInstance(Locale.getDefault());
        cal.setTimeInMillis(timestamp * 1000);
        return android.text.format.DateFormat.format("dd/MM/yyyy\n\nhh:mm aa", cal).toString();
    }

    private ArrayList<ModelHistory> getDataSetHistory() {
        return resultsHistory;
    }

    @Override
    public void onPaymentSuccess(String s) {
        DatabaseReference reference = FirebaseDatabase.getInstance().getReference("RideUsers");
        reference.child("Drivers").child(userId).child("balance").setValue("0");
        progressDialog.dismiss();
        dialog.setContentView(R.layout.payment_result_dialog);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        ImageView resultIv = dialog.findViewById(R.id.resultIv);
        Picasso.get().load(R.drawable.successful).into(resultIv);
        TextView resultTv = dialog.findViewById(R.id.resultTv);
        resultTv.setText("Payment Successful");
        Button okBtn = dialog.findViewById(R.id.okBtn);
        dialog.setCanceledOnTouchOutside(false);
        okBtn.setOnClickListener(v -> {
            dialog.dismiss();
            payBtn.setVisibility(View.GONE);
        });
        dialog.show();
    }

    @Override
    public void onPaymentError(int i, String s) {
        progressDialog.dismiss();
        dialog.setContentView(R.layout.payment_result_dialog);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        ImageView resultIv = dialog.findViewById(R.id.resultIv);
        Picasso.get().load(R.drawable.failed).into(resultIv);
        TextView resultTv = dialog.findViewById(R.id.resultTv);
        resultTv.setText("Payment Failed");
        Button okBtn = dialog.findViewById(R.id.okBtn);
        dialog.setCanceledOnTouchOutside(false);
        okBtn.setOnClickListener(v1 -> dialog.dismiss());
        dialog.show();
    }
}