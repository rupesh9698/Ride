package com.cabservice.ride;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQuery;
import com.firebase.geofire.GeoQueryEventListener;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Status;
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
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.widget.AutocompleteSupportFragment;
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.squareup.picasso.Picasso;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class CustomerMapActivity extends FragmentActivity implements OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, com.google.android.gms.location.LocationListener {

    GoogleApiClient mGoogleApiClient;
    Location mLastLocation;
    LocationRequest mLocationRequest;
    GeoQuery geoQuery;
    final List<Marker> markers = new ArrayList<>();
    boolean getDriversAroundStarted = false;
    private GoogleMap mMap;
    final LocationCallback mLocationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(LocationResult locationResult) {
            for (Location location : locationResult.getLocations()) {
                if (getApplicationContext() != null) {
                    mLastLocation = location;
                    if (!getDriversAroundStarted)
                        getDriversAround();
                }
            }
        }
    };

    private FusedLocationProviderClient mFusedLocationClient;
    private FirebaseAuth firebaseAuth;
    private DatabaseReference UserRef;
    private Button requestBtn;
    private LatLng pickupLocation, destinationLatLng;
    private Boolean requestBol = false;
    private Marker pickupMarker;
    private String destination = "", requestService;
    private LinearLayoutCompat mDriverInfo, mImageGroup;
    private ImageView mDriverProfileImage;
    private TextView mDriverName, mDriverPhone, mDriverCar;
    private RadioGroup mRadioGroup;
    private RatingBar mRatingBar;
    private int radius = 1;
    private Boolean driverFound = false;
    private String driverFoundId;
    private DatabaseReference driveHasEndedRef;
    private ValueEventListener driveHasEndedRefListener;
    private Marker mDriverMarker;
    private DatabaseReference driverLocationRef;
    private ValueEventListener driverLocationRefListener;

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer_map);

        startService(new Intent(CustomerMapActivity.this, OnDriverActivityKilled.class));

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        MapFragment mapFragment = (MapFragment) getFragmentManager().findFragmentById(R.id.map);
        assert mapFragment != null;
        mapFragment.getMapAsync(this);

        firebaseAuth = FirebaseAuth.getInstance();

        mDriverInfo = findViewById(R.id.driverInfo);
        mImageGroup = findViewById(R.id.imageGroup);
        mDriverProfileImage = findViewById(R.id.driverProfileImage);
        mDriverName = findViewById(R.id.driverName);
        mDriverPhone = findViewById(R.id.driverPhone);
        mDriverCar = findViewById(R.id.driverCar);
        mRadioGroup = findViewById(R.id.radioGroup);
        mRadioGroup.check(R.id.UberX);
        mRatingBar = findViewById(R.id.ratingBar);
        requestBtn = findViewById(R.id.requestBtn);
        Button rideLogoutBtn = findViewById(R.id.rideLogoutBtn);
        Button customerHistoryBtn = findViewById(R.id.customerHistoryBtn);
        Button customerSettingsBtn = findViewById(R.id.customerSettingsBtn);

        checkUserName();

        destinationLatLng = new LatLng(0.0, 0.0);

        rideLogoutBtn.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            Intent exitIntent = new Intent(CustomerMapActivity.this, MainActivity.class);
            exitIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(exitIntent);
            finish();
        });

        customerHistoryBtn.setOnClickListener(v -> {
            Intent historyIntent = new Intent(CustomerMapActivity.this, HistoryActivity.class);
            historyIntent.putExtra("customerOrDriver", "Customers");
            startActivity(historyIntent);
        });

        customerSettingsBtn.setOnClickListener(v -> {

            final String user_id = Objects.requireNonNull(firebaseAuth.getCurrentUser()).getUid();

            UserRef = FirebaseDatabase.getInstance().getReference().child("RideUsers").child("Customers").child(user_id);
            DatabaseReference ref = FirebaseDatabase.getInstance().getReference().child("Users");
            Query query = ref.orderByChild("uid").equalTo(user_id);
            query.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    for (DataSnapshot ds : snapshot.getChildren()) {
                        String name = "" + ds.child("name").getValue();
                        String phone = "" + ds.child("phone").getValue();
                        String image = "" + ds.child("image").getValue();

                        HashMap hashMap = new HashMap();
                        hashMap.put("uid", user_id);
                        hashMap.put("name", name);
                        hashMap.put("phone", phone);
                        hashMap.put("image", image);

                        UserRef.updateChildren(hashMap).addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                Intent settingsIntent = new Intent(CustomerMapActivity.this, CustomerSettingsActivity.class);
                                startActivity(settingsIntent);
                            } else {
                                String message = Objects.requireNonNull(task.getException()).getMessage();
                                showToast("" + message);
                            }
                        }).addOnFailureListener(e -> showToast("" + e.getMessage()));
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {

                }
            });
        });

        requestBtn.setOnClickListener(v -> {

            if (requestBol) {
                endRide();
            } else if (destination.length() == 0) {
                showToast("Please Enter Destination");
            } else {

                int selectId = mRadioGroup.getCheckedRadioButtonId();

                final RadioButton radioButton = findViewById(selectId);

                if (radioButton.getText() == null) {
                    return;
                }

                requestService = radioButton.getText().toString();

                requestBol = true;
                String userId = Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getUid();

                DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Customer Request");
                GeoFire geoFire = new GeoFire(ref);
                geoFire.setLocation(userId, new GeoLocation(mLastLocation.getLatitude(), mLastLocation.getLongitude()));

                pickupLocation = new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude());
                pickupMarker = mMap.addMarker(new MarkerOptions().position(pickupLocation).title("Pickup Here").icon(BitmapDescriptorFactory.fromResource(R.mipmap.ic_pickup)));

                requestBtn.setText("Getting Your Driver...!!");

                getClosestDriver();
            }
        });

        AutocompleteSupportFragment autocompleteFragment = (AutocompleteSupportFragment) getSupportFragmentManager().findFragmentById(R.id.autocomplete_fragment);

        assert autocompleteFragment != null;
        autocompleteFragment.requireView().setBackgroundColor(Color.WHITE);

        autocompleteFragment.setPlaceFields(Arrays.asList(Place.Field.LAT_LNG, Place.Field.ID, Place.Field.NAME));

        if (!Places.isInitialized()) {
            Places.initialize(getApplicationContext(), getString(R.string.api_key));
        }

        autocompleteFragment.setOnPlaceSelectedListener(new PlaceSelectionListener() {
            @Override
            public void onPlaceSelected(@NonNull Place place) {
                destination = place.getName();
                destinationLatLng = place.getLatLng();
            }

            @Override
            public void onError(@NonNull Status status) {

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

    private void checkUserName() {

        String currentUserID = Objects.requireNonNull(firebaseAuth.getCurrentUser()).getUid();

        DatabaseReference reference = FirebaseDatabase.getInstance().getReference("RideUsers");
        reference.child("Customers").child(currentUserID).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {

                if (!((dataSnapshot.child("name").exists()) || (dataSnapshot.child("image").exists()))) {

                    Intent settingsIntent = new Intent(CustomerMapActivity.this, CustomerSettingsActivity.class);
                    settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(settingsIntent);
                    finish();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }

    private void getClosestDriver() {
        DatabaseReference driverLocation = FirebaseDatabase.getInstance().getReference().child("Available Drivers");

        GeoFire geoFire = new GeoFire(driverLocation);
        geoQuery = geoFire.queryAtLocation(new GeoLocation(pickupLocation.latitude, pickupLocation.longitude), radius);
        geoQuery.removeAllListeners();

        geoQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
            @Override
            public void onKeyEntered(String key, GeoLocation location) {
                if (!driverFound && requestBol) {
                    DatabaseReference mCustomerDatabase = FirebaseDatabase.getInstance().getReference().child("RideUsers").child("Drivers").child(key);
                    mCustomerDatabase.addListenerForSingleValueEvent(new ValueEventListener() {
                        @SuppressLint("SetTextI18n")
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            if (snapshot.exists() && snapshot.getChildrenCount() > 0) {
                                Map<String, Object> driverMap = (Map<String, Object>) snapshot.getValue();
                                if (driverFound) {
                                    return;
                                }

                                assert driverMap != null;
                                if (Objects.equals(driverMap.get("service"), requestService)) {
                                    driverFound = true;
                                    driverFoundId = snapshot.getKey();

                                    DatabaseReference driverRef = FirebaseDatabase.getInstance().getReference().child("RideUsers").child("Drivers").child(driverFoundId).child("Customer Request");
                                    String customerId = Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getUid();
                                    HashMap map = new HashMap();
                                    map.put("customerRideId", customerId);
                                    map.put("destination", destination);
                                    map.put("destinationLat", destinationLatLng.latitude);
                                    map.put("destinationLng", destinationLatLng.longitude);
                                    map.put("service", requestService);

                                    driverRef.updateChildren(map);

                                    getDriverLocation();
                                    getDriverInfo();
                                    getHasRideEnded();
                                    requestBtn.setText("Looking for Driver Location....");
                                }
                            }

                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {

                        }
                    });
                }
            }

            @Override
            public void onKeyExited(String key) {

            }

            @Override
            public void onKeyMoved(String key, GeoLocation location) {

            }

            @Override
            public void onGeoQueryReady() {
                if (!driverFound) {
                    radius++;
                    getClosestDriver();
                }
            }

            @Override
            public void onGeoQueryError(DatabaseError error) {

            }
        });
    }

    private void getDriverInfo() {

        mRadioGroup.setVisibility(View.GONE);
        mImageGroup.setVisibility(View.GONE);
        mDriverInfo.setVisibility(View.VISIBLE);
        DatabaseReference mDriverDatabase = FirebaseDatabase.getInstance().getReference().child("RideUsers").child("Drivers").child(driverFoundId);
        mDriverDatabase.addListenerForSingleValueEvent(new ValueEventListener() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists() && dataSnapshot.getChildrenCount() > 0) {

                    Map<String, Object> map = (Map<String, Object>) dataSnapshot.getValue();

                    assert map != null;
                    if (map.get("name") != null) {
                        String name = Objects.requireNonNull(map.get("name")).toString();
                        mDriverName.setText("Name : " + name);
                    }
                    if (map.get("phone") != null) {
                        String phone = Objects.requireNonNull(map.get("phone")).toString();
                        mDriverPhone.setText("Phone : " + phone);
                    }
                    if (map.get("car") != null) {
                        String car = Objects.requireNonNull(map.get("car")).toString();
                        mDriverCar.setText("Car : " + car);
                    }
                    if (map.get("image") != null) {
                        String imageUrl = Objects.requireNonNull(map.get("image")).toString();
                        try {
                            Picasso.get().load(imageUrl).into(mDriverProfileImage);
                        } catch (Exception e) {
                            Picasso.get().load(R.mipmap.ic_default_user).into(mDriverProfileImage);
                        }
                    }
                    int ratingSum = 0;
                    float ratingsTotal = 0;
                    float ratingsAvg;
                    for (DataSnapshot child : dataSnapshot.child("rating").getChildren()) {
                        ratingSum = ratingSum + Integer.parseInt(Objects.requireNonNull(child.getValue()).toString());
                        ratingsTotal++;
                    }
                    if (ratingsTotal != 0) {
                        ratingsAvg = ratingSum / ratingsTotal;
                        mRatingBar.setRating(ratingsAvg);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
            }
        });
    }

    private void getHasRideEnded() {
        driveHasEndedRef = FirebaseDatabase.getInstance().getReference().child("RideUsers").child("Drivers").child(driverFoundId).child("Customer Request").child("customerRideId");
        driveHasEndedRefListener = driveHasEndedRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {

                } else {
                    endRide();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
            }
        });
    }

    @SuppressLint("SetTextI18n")
    private void endRide() {
        requestBol = false;
        geoQuery.removeAllListeners();
        destination = "";

        if (driverLocationRef != null) {
            driverLocationRef.removeEventListener(driverLocationRefListener);
        }
        if (driveHasEndedRef != null) {
            driveHasEndedRef.removeEventListener(driveHasEndedRefListener);
        }

        if (driverFoundId != null) {
            DatabaseReference driverRef = FirebaseDatabase.getInstance().getReference().child("RideUsers").child("Drivers").child(driverFoundId).child("Customer Request");
            driverRef.removeValue();
            driverFoundId = null;
        }
        driverFound = false;
        radius = 1;
        String userId = Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getUid();

        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Customer Request");
        GeoFire geoFire = new GeoFire(ref);
        geoFire.removeLocation(userId);

        if (pickupMarker != null) {
            pickupMarker.remove();
        }
        if (mDriverMarker != null) {
            mDriverMarker.remove();
        }
        requestBtn.setText("Call Ride");

        mDriverInfo.setVisibility(View.GONE);
        mImageGroup.setVisibility(View.VISIBLE);
        mRadioGroup.setVisibility(View.VISIBLE);
        mDriverName.setText("");
        mDriverPhone.setText("");
        mDriverCar.setText("");
        mDriverProfileImage.setImageResource(R.mipmap.ic_default_user);
    }

    private void getDriverLocation() {
        driverLocationRef = FirebaseDatabase.getInstance().getReference().child("Working Drivers").child(driverFoundId).child("l");
        driverLocationRefListener = driverLocationRef.addValueEventListener(new ValueEventListener() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists() && requestBol) {
                    List<Object> map = (List<Object>) snapshot.getValue();
                    double locationLat = 0;
                    double locationLng = 0;
                    requestBtn.setText("Driver Found");
                    assert map != null;
                    if (map.get(0) != null) {
                        locationLat = Double.parseDouble(map.get(0).toString());
                    }
                    if (map.get(1) != null) {
                        locationLng = Double.parseDouble(map.get(1).toString());
                    }
                    LatLng driverLatLng = new LatLng(locationLat, locationLng);
                    if (mDriverMarker != null) {
                        mDriverMarker.remove();
                    }
                    Location loc1 = new Location("");
                    loc1.setLatitude(pickupLocation.latitude);
                    loc1.setLongitude(pickupLocation.longitude);

                    Location loc2 = new Location("");
                    loc2.setLatitude(driverLatLng.latitude);
                    loc2.setLongitude(driverLatLng.longitude);

                    float distance = loc1.distanceTo(loc2) / 1000;

                    DecimalFormat df = new DecimalFormat();
                    df.setMaximumFractionDigits(2);
                    requestBtn.setText("Driver Found : " + df.format(distance) + " KM");

                    mDriverMarker = mMap.addMarker(new MarkerOptions().position(driverLatLng).title("Your Driver Location").icon(BitmapDescriptorFactory.fromResource(R.mipmap.ic_car)));

                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(1000);
        mLocationRequest.setFastestInterval(1000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(getApplicationContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, 300);
        }
        buildGoogleApiClient();
        mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, Looper.myLooper());
        mMap.setMyLocationEnabled(true);
    }

    protected synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        mGoogleApiClient.connect();
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(1000);
        mLocationRequest.setFastestInterval(1000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(getApplicationContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, 300);
        }

        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    @Override
    public void onLocationChanged(Location location) {
        mLastLocation = location;

        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
        mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
        mMap.animateCamera(CameraUpdateFactory.zoomTo(16));
    }

    private void getDriversAround() {
        getDriversAroundStarted = true;
        DatabaseReference driverLocation = FirebaseDatabase.getInstance().getReference().child("Available Drivers");

        GeoFire geoFire = new GeoFire(driverLocation);
        GeoQuery geoQuery = geoFire.queryAtLocation(new GeoLocation(mLastLocation.getLatitude(), mLastLocation.getLongitude()), 30000);

        geoQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
            @Override
            public void onKeyEntered(String key, GeoLocation location) {

                for (Marker markerIt : markers) {
                    if (Objects.equals(markerIt.getTag(), key))
                        return;
                }

                LatLng driverLocation = new LatLng(location.latitude, location.longitude);

                Marker mDriverMarker = mMap.addMarker(new MarkerOptions().position(driverLocation).title(key).icon(BitmapDescriptorFactory.fromResource(R.mipmap.ic_car)));
                mDriverMarker.setTag(key);
                markers.add(mDriverMarker);

            }

            @Override
            public void onKeyExited(String key) {
                for (Marker markerIt : markers) {
                    if (Objects.equals(markerIt.getTag(), key)) {
                        markerIt.remove();
                        markers.remove(markerIt);
                        return;
                    }
                }
            }

            @Override
            public void onKeyMoved(String key, GeoLocation location) {
                for (Marker markerIt : markers) {
                    if (Objects.equals(markerIt.getTag(), key)) {
                        markerIt.setPosition(new LatLng(location.latitude, location.longitude));
                    }
                }
            }

            @Override
            public void onGeoQueryReady() {
            }

            @Override
            public void onGeoQueryError(DatabaseError error) {

            }
        });
    }
}