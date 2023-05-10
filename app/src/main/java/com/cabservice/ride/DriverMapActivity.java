package com.cabservice.ride;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import com.directions.route.AbstractRouting;
import com.directions.route.Route;
import com.directions.route.RouteException;
import com.directions.route.Routing;
import com.directions.route.RoutingListener;
import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class DriverMapActivity extends FragmentActivity implements OnMapReadyCallback, RoutingListener {

    private static final int[] COLORS = new int[]{R.color.primary_dark_material_light};
    Location mLastLocation;
    LocationRequest mLocationRequest;
    FirebaseAuth firebaseAuth;
    Marker pickupMarker;
    private GoogleMap mMap;
    private FusedLocationProviderClient mFusedLocationClient;
    private Button mRideCancel, mRideStatus;
    private int status = 0;
    private float rideDistance = 0;
    private String customerId = "", destination, service;
    final LocationCallback mLocationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(LocationResult locationResult) {
            for (Location location : locationResult.getLocations()) {
                if (getApplicationContext() != null) {

                    if (!customerId.equals("") && mLastLocation != null && location != null) {
                        rideDistance += mLastLocation.distanceTo(location) / 1000;
                    }
                    mLastLocation = location;

                    LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
                    mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
                    mMap.animateCamera(CameraUpdateFactory.zoomTo(16));

                    String user_id = Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getUid();
                    DatabaseReference refAvailable = FirebaseDatabase.getInstance().getReference("Available Drivers");
                    DatabaseReference refWorking = FirebaseDatabase.getInstance().getReference("Working Drivers");
                    GeoFire geoFireAvailable = new GeoFire(refAvailable);
                    GeoFire geoFireWorking = new GeoFire(refWorking);

                    if ("".equals(customerId)) {
                        geoFireWorking.removeLocation(user_id);
                        geoFireAvailable.setLocation(user_id, new GeoLocation(location.getLatitude(), location.getLongitude()));
                    } else {
                        geoFireAvailable.removeLocation(user_id);
                        geoFireWorking.setLocation(user_id, new GeoLocation(location.getLatitude(), location.getLongitude()));
                    }
                }
            }
        }
    };
    private String name, phone, imageUrl, currentUserId;
    private LatLng destinationLatLng, pickupLatLng;
    private LinearLayoutCompat mCustomerInfo;
    private ImageView mCustomerProfileImage;
    private SwitchCompat mWorkingSwitch;
    private TextView mCustomerName, mCustomerPhone, mCustomerDestination;
    private DatabaseReference assignedCustomerPickupLocationRef;
    private ValueEventListener assignedCustomerPickupLocationRefListener;
    private List<Polyline> polylines;

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_map);

        startService(new Intent(DriverMapActivity.this, OnDriverActivityKilled.class));

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        polylines = new ArrayList<>();

        MapFragment mapFragment = (MapFragment) getFragmentManager().findFragmentById(R.id.map);
        assert mapFragment != null;
        mapFragment.getMapAsync(DriverMapActivity.this);

        firebaseAuth = FirebaseAuth.getInstance();

        mCustomerInfo = findViewById(R.id.customerInfo);

        mCustomerProfileImage = findViewById(R.id.customerProfileImage);

        mCustomerName = findViewById(R.id.customerName);
        mCustomerPhone = findViewById(R.id.customerPhone);
        mCustomerDestination = findViewById(R.id.customerDestination);
        Button rideLogout = findViewById(R.id.rideLogout);
        Button driverHistoryBtn = findViewById(R.id.history);
        Button rideSettings = findViewById(R.id.rideSettings);
        mRideCancel = findViewById(R.id.rideCancel);
        mRideStatus = findViewById(R.id.rideStatus);

        currentUserId = Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getUid();

        mWorkingSwitch = findViewById(R.id.workingSwitch);
        mWorkingSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                connectDriver();
                rideLogout.setVisibility(View.GONE);
                driverHistoryBtn.setVisibility(View.GONE);
                rideSettings.setVisibility(View.GONE);

            } else {
                disconnectDriver();
                rideLogout.setVisibility(View.VISIBLE);
                driverHistoryBtn.setVisibility(View.VISIBLE);
                rideSettings.setVisibility(View.VISIBLE);
            }
        });

        checkUserCarAndService();

        mRideCancel.setOnClickListener(v -> endRide());

        mRideStatus.setOnClickListener(v -> {
            switch (status) {
                case 1:
                    status = 2;
                    erasePolylines();
                    if (destinationLatLng.latitude != 0.0 && destinationLatLng.longitude != 0.0) {
                        getRouteToMarker(destinationLatLng);
                    }
                    mRideCancel.setVisibility(View.GONE);
                    mRideStatus.setText("Drive Completed");
                    break;
                case 2:
                    recordRide();
                    endRide();
                    mWorkingSwitch.setChecked(false);
                    disconnectDriver();
                    DatabaseReference refWorkingDriver = FirebaseDatabase.getInstance().getReference("Working Drivers");
                    refWorkingDriver.child(currentUserId).removeValue();
                    rideLogout.setVisibility(View.VISIBLE);
                    driverHistoryBtn.setVisibility(View.VISIBLE);
                    rideSettings.setVisibility(View.VISIBLE);
                    break;
            }
        });

        rideLogout.setOnClickListener(v -> {
            disconnectDriver();
            SharedPreferences sharedPreferences = getSharedPreferences("role", MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.remove("myRole");
            editor.clear();
            editor.apply();
            FirebaseAuth.getInstance().signOut();
            Intent exitIntent = new Intent(DriverMapActivity.this, MainActivity.class);
            exitIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(exitIntent);
            finish();
        });

        driverHistoryBtn.setOnClickListener(v -> {
            Intent historyIntent = new Intent(DriverMapActivity.this, HistoryActivity.class);
            historyIntent.putExtra("customerOrDriver", "Drivers");
            startActivity(historyIntent);
        });

        rideSettings.setOnClickListener(v -> {
            Intent settingsIntent = new Intent(DriverMapActivity.this, DriverSettingsActivity.class);
            startActivity(settingsIntent);
        });

        getAssignedCustomer();
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

    private void checkUserCarAndService() {

        String currentUserID = Objects.requireNonNull(firebaseAuth.getCurrentUser()).getUid();

        DatabaseReference reference = FirebaseDatabase.getInstance().getReference("RideUsers");
        reference.child("Drivers").child(currentUserID).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {

                if (!((dataSnapshot.child("car").exists()) || (dataSnapshot.child("image").exists()) || (dataSnapshot.child("name").exists()) || (dataSnapshot.child("service").exists()))) {
                    Intent settingsIntent = new Intent(DriverMapActivity.this, DriverSettingsActivity.class);
                    settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(settingsIntent);
                    finish();
                }
                if (!(dataSnapshot.child("balance").exists())) {
                    Map balanceMap = new HashMap();
                    balanceMap.put("balance", "0");
                    reference.child("Drivers").child(currentUserID).updateChildren(balanceMap);
                }
                if (!(dataSnapshot.child("kyc").exists())) {
                    Map kycMap = new HashMap();
                    kycMap.put("kyc", false);
                    reference.child("Drivers").child(currentUserID).updateChildren(kycMap);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }

    private void getAssignedCustomer() {

        DatabaseReference assignedCustomerRef = FirebaseDatabase.getInstance().getReference().child("RideUsers").child("Drivers").child(currentUserId).child("Customer Request").child("customerRideId");
        assignedCustomerRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    status = 1;

                    customerId = Objects.requireNonNull(snapshot.getValue()).toString();
                    getAssignedCustomerPickupLocation();
                    getAssignedCustomerDestinationAndService();
                    getAssignedCustomerInfo();
                } else {
                    endRide();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }

    private void getAssignedCustomerPickupLocation() {

        assignedCustomerPickupLocationRef = FirebaseDatabase.getInstance().getReference().child("Customer Request").child(customerId).child("l");
        assignedCustomerPickupLocationRefListener = assignedCustomerPickupLocationRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists() && !customerId.equals("")) {
                    List<Object> map = (List<Object>) snapshot.getValue();
                    double locationLat = 0;
                    double locationLng = 0;
                    assert map != null;
                    if (map.get(0) != null) {
                        locationLat = Double.parseDouble(map.get(0).toString());
                    }
                    if (map.get(1) != null) {
                        locationLng = Double.parseDouble(map.get(1).toString());
                    }
                    pickupLatLng = new LatLng(locationLat, locationLng);
                    pickupMarker = mMap.addMarker(new MarkerOptions().position(pickupLatLng).title("Pickup Location").icon(BitmapDescriptorFactory.fromResource(R.mipmap.ic_pickup)));
                    getRouteToMarker(pickupLatLng);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }

    private void getRouteToMarker(LatLng pickupLatLng) {

        if (pickupLatLng != null && mLastLocation != null) {

            Routing routing = new Routing.Builder()
                    .key("AIzaSyAV-qBxYYacJSxNRrpetI5XUr0A3HDPiBM")
                    .travelMode(AbstractRouting.TravelMode.DRIVING)
                    .withListener(this)
                    .alternativeRoutes(false)
                    .waypoints(new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude()), pickupLatLng)
                    .build();
            routing.execute();
        }
    }

    private void getAssignedCustomerDestinationAndService() {
        String driverId = Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getUid();
        DatabaseReference assignedCustomerRef = FirebaseDatabase.getInstance().getReference().child("RideUsers").child("Drivers").child(driverId).child("Customer Request");
        assignedCustomerRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {

                    Map<String, Object> map = (Map<String, Object>) dataSnapshot.getValue();
                    assert map != null;
                    if (map.get("destination") != null) {
                        destination = Objects.requireNonNull(map.get("destination")).toString();
                        mCustomerDestination.setText("Destination : " + destination);
                    }

                    if (map.get("service") != null) {
                        service = Objects.requireNonNull(map.get("service")).toString();
                    }

                    double destinationLat;
                    double destinationLng;
                    if ((map.get("destinationLat") != null) && map.get("destinationLng") != null) {

                        destinationLat = Double.parseDouble(Objects.requireNonNull(map.get("destinationLat")).toString());
                        destinationLng = Double.parseDouble(Objects.requireNonNull(map.get("destinationLng")).toString());
                        destinationLatLng = new LatLng(destinationLat, destinationLng);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
            }
        });
    }

    private void getAssignedCustomerInfo() {

        mCustomerInfo.setVisibility(View.VISIBLE);
        DatabaseReference mCustomerDatabase = FirebaseDatabase.getInstance().getReference().child("RideUsers").child("Customers").child(customerId);
        mCustomerDatabase.addListenerForSingleValueEvent(new ValueEventListener() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists() && dataSnapshot.getChildrenCount() > 0) {
                    Map<String, Object> map = (Map<String, Object>) dataSnapshot.getValue();

                    assert map != null;
                    if (map.get("name") != null) {
                        name = Objects.requireNonNull(map.get("name")).toString();
                        mCustomerName.setText("Name : " + name);
                    }
                    if (map.get("phone") != null) {
                        phone = Objects.requireNonNull(map.get("phone")).toString();
                        mCustomerPhone.setText("Phone : " + phone);
                    }
                    if (map.get("image") != null) {
                        imageUrl = Objects.requireNonNull(map.get("image")).toString();
                        try {
                            Picasso.get().load(imageUrl).into(mCustomerProfileImage);
                        } catch (Exception e) {
                            Picasso.get().load(R.mipmap.ic_default_user).into(mCustomerProfileImage);
                        }
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
            }
        });
    }

    @SuppressLint("SetTextI18n")
    private void endRide() {

        name = "";
        phone = "";
        imageUrl = "";

        erasePolylines();
        mRideStatus.setText("Picked Customer");

        String userId = Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getUid();
        DatabaseReference driverRef = FirebaseDatabase.getInstance().getReference().child("RideUsers").child("Drivers").child(userId).child("Customer Request");
        driverRef.removeValue();

        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Customer Request");
        GeoFire geoFire = new GeoFire(ref);
        geoFire.removeLocation(customerId);
        customerId = "";
        rideDistance = 0;

        if (pickupMarker != null) {
            pickupMarker.remove();
        }
        if (assignedCustomerPickupLocationRefListener != null) {
            assignedCustomerPickupLocationRef.removeEventListener(assignedCustomerPickupLocationRefListener);
        }
        mCustomerInfo.setVisibility(View.GONE);
        mCustomerName.setText("");
        mCustomerPhone.setText("");
        mCustomerDestination.setText("Destination: --");
        mCustomerProfileImage.setImageResource(R.mipmap.ic_default_user);
    }

    private void recordRide() {

        name = "";
        phone = "";
        imageUrl = "";

        String userId = Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getUid();
        DatabaseReference driverRef = FirebaseDatabase.getInstance().getReference().child("RideUsers").child("Drivers").child(userId).child("History");
        DatabaseReference customerRef = FirebaseDatabase.getInstance().getReference().child("RideUsers").child("Customers").child(customerId).child("History");
        DatabaseReference historyRef = FirebaseDatabase.getInstance().getReference().child("History");
        String requestId = historyRef.push().getKey();
        assert requestId != null;
        driverRef.child(requestId).setValue(true);
        customerRef.child(requestId).setValue(true);

        HashMap map = new HashMap();
        map.put("customer", customerId);
        map.put("destination", destination);
        map.put("distance", rideDistance);
        map.put("driver", userId);
        map.put("location/from/lat", pickupLatLng.latitude);
        map.put("location/from/lng", pickupLatLng.longitude);
        map.put("location/to/lat", destinationLatLng.latitude);
        map.put("location/to/lng", destinationLatLng.longitude);
        map.put("payment", "unpaid");
        map.put("rating", 0);
        map.put("service", service);
        map.put("timestamp", getCurrentTimestamp());
        historyRef.child(requestId).updateChildren(map);
    }

    private Long getCurrentTimestamp() {
        return System.currentTimeMillis() / 1000;
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(1000);
        mLocationRequest.setFastestInterval(1000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

        } else {
            checkLocationPermission();
        }
    }

    private void checkLocationPermission() {

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                new AlertDialog.Builder(this)
                        .setTitle("give permission")
                        .setMessage("give permission message")
                        .setPositiveButton("OK", (dialogInterface, i) -> ActivityCompat.requestPermissions(DriverMapActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1))
                        .create()
                        .show();
            } else {
                ActivityCompat.requestPermissions(DriverMapActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == 1) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, Looper.myLooper());
                    mMap.setMyLocationEnabled(true);
                }
            } else {
                showToast("Please provide the permission");
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void connectDriver() {
        checkLocationPermission();
        mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, Looper.myLooper());
        mMap.setMyLocationEnabled(true);
    }

    private void disconnectDriver() {
        if (mFusedLocationClient != null) {
            mFusedLocationClient.removeLocationUpdates(mLocationCallback);
        }
        String user_id = Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getUid();
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Available Drivers");

        GeoFire geoFire = new GeoFire(ref);
        geoFire.removeLocation(user_id);
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

    }

    private void erasePolylines() {
        for (Polyline line : polylines) {
            line.remove();
        }
        polylines.clear();
    }

    @Override
    protected void onStart() {

        DatabaseReference rootRef = FirebaseDatabase.getInstance().getReference("Available Drivers");
        rootRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.hasChild(Objects.requireNonNull(firebaseAuth.getUid()))) {
                    connectDriver();
                    mWorkingSwitch.setChecked(true);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
        super.onStart();
    }
}