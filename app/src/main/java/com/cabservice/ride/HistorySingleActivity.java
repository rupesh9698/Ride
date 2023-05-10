package com.cabservice.ride;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;

import com.directions.route.AbstractRouting;
import com.directions.route.Route;
import com.directions.route.RouteException;
import com.directions.route.Routing;
import com.directions.route.RoutingListener;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
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

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@SuppressLint("SetTextI18n")
public class HistorySingleActivity extends AppCompatActivity implements OnMapReadyCallback, RoutingListener, PaymentResultListener {

    private static final int[] COLORS = new int[]{R.color.primary_dark_material_light};
    private String amountToDriver, myRole, rideId, currentUserId, customerId, driverId, distance, service;
    private String otherUserName, otherUserPhone, otherUserUid, otherUserImage, otherUserBalence;
    private TextView userName, userPhone, rideDistance, rideService, rideCost, rideDate;
    private ProgressDialog progressDialog;
    private Dialog dialog;
    private Button payBtn;
    private TextView rideDestination;
    private ImageView userImage;
    private RatingBar mRatingBar;
    private DatabaseReference historyRideInfoDb;
    private LatLng pickupLatLng, destinationLatLng;
    private GoogleMap mMap;
    private Double ridePrice;
    private Float distanceNumber;
    private List<Polyline> polylines;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history_single);

        polylines = new ArrayList<>();
        rideId = getIntent().getExtras().getString("rideId");

        SupportMapFragment mMapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        assert mMapFragment != null;
        mMapFragment.getMapAsync(this);

        userName = findViewById(R.id.userName);
        userPhone = findViewById(R.id.userPhone);
        rideDestination = findViewById(R.id.rideDestination);
        rideDistance = findViewById(R.id.rideDistance);
        rideService = findViewById(R.id.rideService);
        rideCost = findViewById(R.id.rideCost);
        rideDate = findViewById(R.id.rideDate);
        userImage = findViewById(R.id.userImage);
        mRatingBar = findViewById(R.id.ratingBar);
        payBtn = findViewById(R.id.payBtn);
        dialog = new Dialog(HistorySingleActivity.this);
        progressDialog = new ProgressDialog(HistorySingleActivity.this);

        currentUserId = Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getUid();
        historyRideInfoDb = FirebaseDatabase.getInstance().getReference().child("History").child(rideId);

        getRideInformation();
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

    private void getRideInformation() {
        historyRideInfoDb.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    for (DataSnapshot child : snapshot.getChildren()) {

                        DecimalFormat df = new DecimalFormat();
                        df.setMaximumFractionDigits(2);

                        if (Objects.equals(child.getKey(), "customer")) {
                            customerId = Objects.requireNonNull(child.getValue()).toString();
                            if (!customerId.equals(currentUserId)) {
                                getUserInformation("Customers", customerId);
                                myRole = "Drivers";
                            }
                        }
                        if (Objects.equals(child.getKey(), "driver")) {
                            driverId = Objects.requireNonNull(child.getValue()).toString();
                            if (!driverId.equals(currentUserId)) {
                                getUserInformation("Drivers", driverId);
                                displayCustomerRelatedObjects();
                                myRole = "Customers";
                            }
                        }
                        if (child.getKey().equals("timestamp")) {
                            rideDate.setText(getDate(Long.valueOf(Objects.requireNonNull(child.getValue()).toString())));
                        }
                        if (child.getKey().equals("distance")) {
                            distance = Objects.requireNonNull(child.getValue()).toString();
                            distanceNumber = Float.parseFloat(distance);
                            rideDistance.setText("Distance : " + df.format(distanceNumber) + " KM");
                        }
                        if (child.getKey().equals("service")) {
                            service = Objects.requireNonNull(child.getValue()).toString();
                            rideService.setText("Service : " + service);
                            switch (service) {
                                case "Ride Mini":
                                    ridePrice = Double.parseDouble(df.format(distanceNumber)) * 12;
                                    rideCost.setText("Cost : Rs " + df.format(ridePrice) + "/-");
                                    if (ridePrice == 0.0) {
                                        payBtn.setVisibility(View.GONE);
                                    }
                                    break;
                                case "Ride - X":
                                    ridePrice = Double.parseDouble(df.format(distanceNumber)) * 15;
                                    rideCost.setText("Cost : Rs " + df.format(ridePrice) + "/-");
                                    if (ridePrice == 0.0) {
                                        payBtn.setVisibility(View.GONE);
                                    }
                                    break;
                                case "Ride - Prime":
                                    ridePrice = Double.parseDouble(df.format(distanceNumber)) * 20;
                                    rideCost.setText("Cost : Rs " + df.format(ridePrice) + "/-");
                                    if (ridePrice == 0.0) {
                                        payBtn.setVisibility(View.GONE);
                                    }
                                    break;
                            }
                        }
                        if (child.getKey().equals("payment")) {
                            String paymentStatus = Objects.requireNonNull(child.getValue()).toString();
                            if (paymentStatus.equals("unpaid")) {
                                if (myRole.equals("Customers")) {
                                    payBtn.setVisibility(View.VISIBLE);
                                    payBtn.setText("PAY ONLINE");
                                    payBtn.setOnClickListener(v -> {
                                        progressDialog = new ProgressDialog(HistorySingleActivity.this);
                                        progressDialog.setMessage("Processing");
                                        progressDialog.setCanceledOnTouchOutside(false);
                                        progressDialog.show();
                                        String phone = Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getPhoneNumber();
                                        int amount = Math.round(Float.parseFloat(String.valueOf(ridePrice)) * 100);
                                        amountToDriver = df.format(ridePrice * 95 / 100);
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
                                            checkout.open(HistorySingleActivity.this, object);
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
                                else {
                                    payBtn.setVisibility(View.VISIBLE);
                                    payBtn.setText("PAID IN CASH");
                                    payBtn.setOnClickListener(v -> {

                                        dialog.setContentView(R.layout.custom_alert_dialog);
                                        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                                        TextView alertDialogTitleTv = dialog.findViewById(R.id.alertDialogTitleTv);
                                        alertDialogTitleTv.setText("PAID IN CASH");
                                        TextView alertDialogDescriptionTv = dialog.findViewById(R.id.alertDialogDescriptionTv);
                                        alertDialogDescriptionTv.setText("Collected cash from customer ?");
                                        AppCompatButton alertDialogNegativeBtn = dialog.findViewById(R.id.alertDialogNegativeBtn);
                                        alertDialogNegativeBtn.setText("NO");
                                        AppCompatButton alertDialogPositiveBtn = dialog.findViewById(R.id.alertDialogPositiveBtn);
                                        alertDialogPositiveBtn.setText("YES");
                                        dialog.setCanceledOnTouchOutside(false);
                                        alertDialogNegativeBtn.setOnClickListener(v1 -> dialog.dismiss());
                                        alertDialogPositiveBtn.setOnClickListener(v12 -> cashCollected());
                                        dialog.show();
                                    });
                                }
                            }
                        }
                        if (child.getKey().equals("rating")) {
                            mRatingBar.setRating(Integer.parseInt(Objects.requireNonNull(child.getValue()).toString()));
                        }
                        if (child.getKey().equals("destination")) {
                            rideDestination.setText("Destination : " + Objects.requireNonNull(child.getValue()).toString());
                        }
                        if (child.getKey().equals("location")) {
                            pickupLatLng = new LatLng(Double.parseDouble(Objects.requireNonNull(child.child("from").child("lat").getValue()).toString()), Double.parseDouble(Objects.requireNonNull(child.child("from").child("lng").getValue()).toString()));
                            destinationLatLng = new LatLng(Double.parseDouble(Objects.requireNonNull(child.child("to").child("lat").getValue()).toString()), Double.parseDouble(Objects.requireNonNull(child.child("to").child("lng").getValue()).toString()));
                            if (!destinationLatLng.equals(new LatLng(0, 0))) {
                                getRouteToMarker();
                            }
                        }
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }

    private void cashCollected() {
        progressDialog = new ProgressDialog(HistorySingleActivity.this);
        progressDialog.setMessage("Processing");
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.show();
        DatabaseReference reference = FirebaseDatabase.getInstance().getReference("RideUsers");
        reference.child("Drivers").child(currentUserId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                String balance = Objects.requireNonNull(dataSnapshot.child("balance").getValue()).toString();
                DecimalFormat df = new DecimalFormat();
                df.setMaximumFractionDigits(2);
                amountToDriver = df.format(ridePrice * (-5) / 100);
                String newBalance = df.format(Double.parseDouble(balance) + Double.parseDouble(amountToDriver));
                reference.child("Drivers").child(currentUserId).child("balance").setValue(newBalance);
                historyRideInfoDb.child("payment").setValue("paid");
                progressDialog.dismiss();
                dialog.setContentView(R.layout.payment_result_dialog);
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                ImageView resultIv = dialog.findViewById(R.id.resultIv);
                Picasso.get().load(R.drawable.successful).into(resultIv);
                TextView resultTv = dialog.findViewById(R.id.resultTv);
                resultTv.setText("Balance Updated");
                Button okBtn = dialog.findViewById(R.id.okBtn);
                dialog.setCanceledOnTouchOutside(false);
                okBtn.setOnClickListener(v1 -> {
                    dialog.dismiss();
                    payBtn.setVisibility(View.GONE);
                });
                dialog.show();
            }
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                progressDialog.dismiss();
                dialog.setContentView(R.layout.payment_result_dialog);
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                ImageView resultIv = dialog.findViewById(R.id.resultIv);
                Picasso.get().load(R.drawable.failed).into(resultIv);
                TextView resultTv = dialog.findViewById(R.id.resultTv);
                resultTv.setText("Failed");
                Button okBtn = dialog.findViewById(R.id.okBtn);
                dialog.setCanceledOnTouchOutside(false);
                okBtn.setOnClickListener(v1 -> dialog.dismiss());
                dialog.show();
            }
        });
    }

    private void displayCustomerRelatedObjects() {
        mRatingBar.setVisibility(View.VISIBLE);
        mRatingBar.setOnRatingBarChangeListener((ratingBar, rating, fromUser) -> {
            historyRideInfoDb.child("rating").setValue(rating);
            DatabaseReference mDriverRatingDb = FirebaseDatabase.getInstance().getReference().child("RideUsers").child("Drivers").child(driverId).child("rating");
            mDriverRatingDb.child(rideId).setValue(rating);
        });
    }

    private void getUserInformation(String otherUserDriverOrCustomer, String otherUserId) {
        DatabaseReference mOtherUserDb = FirebaseDatabase.getInstance().getReference().child("RideUsers").child(otherUserDriverOrCustomer).child(otherUserId);
        mOtherUserDb.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    Map<String, Object> map = (Map<String, Object>) snapshot.getValue();
                    assert map != null;
                    if (map.get("name") != null) {
                        otherUserName = Objects.requireNonNull(map.get("name")).toString();
                        userName.setText(otherUserName);
                    }
                    if (map.get("balance") != null) {
                        otherUserBalence = Objects.requireNonNull(map.get("balance")).toString();
                    }
                    if (map.get("phone") != null) {
                        otherUserPhone = Objects.requireNonNull(map.get("phone")).toString();
                        userPhone.setText(otherUserPhone);
                    }
                    if (map.get("uid") != null) {
                        otherUserUid = Objects.requireNonNull(map.get("uid")).toString();
                    }
                    if (map.get("image") != null) {
                        otherUserImage = Objects.requireNonNull(map.get("image")).toString();
                        try {
                            Picasso.get().load(otherUserImage).into(userImage);
                        } catch (Exception e) {
                            Picasso.get().load(R.mipmap.ic_default_user).into(userImage);
                        }
                    }
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
        return android.text.format.DateFormat.format("dd/MM/yyyy hh:mm aa", cal).toString();
    }

    private void getRouteToMarker() {
        Routing routing = new Routing.Builder()
                .key("AIzaSyAV-qBxYYacJSxNRrpetI5XUr0A3HDPiBM")
                .travelMode(AbstractRouting.TravelMode.DRIVING)
                .withListener(this)
                .alternativeRoutes(false)
                .waypoints(pickupLatLng, destinationLatLng)
                .build();
        routing.execute();
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
    }

    @Override
    public void onRoutingFailure(RouteException e) {
        if (e != null) {
            showToast("Error: " + e.getMessage());
        } else {
            showToast("Something went wrong, Try again");
        }
    }

    @Override
    public void onRoutingStart() {

    }

    @Override
    public void onRoutingSuccess(ArrayList<Route> route, int shortestRouteIndex) {

        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        builder.include(pickupLatLng);
        builder.include(destinationLatLng);
        LatLngBounds bounds = builder.build();

        int width = getResources().getDisplayMetrics().widthPixels;
        int padding = (int) (width * 0.2);
        CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds, padding);
        mMap.animateCamera(cameraUpdate);

        mMap.addMarker(new MarkerOptions().position(pickupLatLng).title("pickup location").icon(BitmapDescriptorFactory.fromResource(R.mipmap.ic_pickup)));
        mMap.addMarker(new MarkerOptions().position(destinationLatLng).title("destination"));

        if (polylines.size() > 0) {
            for (Polyline poly : polylines) {
                poly.remove();
            }
        }

        polylines = new ArrayList<>();
        for (int i = 0; i < route.size(); i++) {

            int colorIndex = i % COLORS.length;
            PolylineOptions polyOptions = new PolylineOptions();
            polyOptions.color(getResources().getColor(COLORS[colorIndex]));
            polyOptions.width(10 + i * 3);
            polyOptions.addAll(route.get(i).getPoints());
            Polyline polyline = mMap.addPolyline(polyOptions);
            polylines.add(polyline);
        }
    }

    @Override
    public void onRoutingCancelled() {
        progressDialog.dismiss();
        dialog.setContentView(R.layout.payment_result_dialog);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        ImageView resultIv = dialog.findViewById(R.id.resultIv);
        Picasso.get().load(R.drawable.failed).into(resultIv);
        TextView resultTv = dialog.findViewById(R.id.resultTv);
        resultTv.setText("Payment Cancelled");
        Button okBtn = dialog.findViewById(R.id.okBtn);
        dialog.setCanceledOnTouchOutside(false);
        okBtn.setOnClickListener(v1 -> dialog.dismiss());
        dialog.show();
    }

    @Override
    public void onPaymentSuccess(String s) {
        historyRideInfoDb.child("payment").setValue("paid");
        DecimalFormat df = new DecimalFormat();
        df.setMaximumFractionDigits(2);
        String newBalance = df.format(Double.parseDouble(otherUserBalence) + Double.parseDouble(amountToDriver));
        DatabaseReference reference = FirebaseDatabase.getInstance().getReference("RideUsers");
        reference.child("Drivers").child(otherUserUid).child("balance").setValue(newBalance);
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