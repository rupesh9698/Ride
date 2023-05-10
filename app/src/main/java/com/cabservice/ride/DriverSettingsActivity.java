package com.cabservice.ride;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.squareup.picasso.Picasso;
import com.yalantis.ucrop.UCrop;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class DriverSettingsActivity extends AppCompatActivity {

    private static final int CAMERA_REQUEST_CODE = 100;
    private static final int STORAGE_REQUEST_CODE = 200;
    private static final int IMAGE_PICK_CAMERA_CODE = 300;
    private static final int IMAGE_PICK_GALLERY_CODE = 400;
    private TextView mPhoneField;
    private EditText mNameField, mCarField;
    private ImageView mProfileImage;
    private FirebaseDatabase firebaseDatabase;
    private String mName = "", mCar = "", image = "", mService, userID;
    private RadioGroup mRadioGroup;
    private ProgressDialog progressDialog;
    private String[] cameraPermission;
    private String[] storagePermission;
    private Uri image_uri = null;
    private Uri imageUriResultCrop = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_settings);

        mNameField = findViewById(R.id.driverName);
        mPhoneField = findViewById(R.id.driverPhone);
        mCarField = findViewById(R.id.driverCar);
        mProfileImage = findViewById(R.id.driverProfileImage);
        mRadioGroup = findViewById(R.id.radioGroup);
        mRadioGroup.check(R.id.UberX);

        Button mBack = findViewById(R.id.driverSettingsBack);
        Button mSave = findViewById(R.id.driverSettingsSave);
        Button mKyc = findViewById(R.id.driverKycBtn);

        cameraPermission = new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE};
        storagePermission = new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE};

        FirebaseAuth mDriverAuth = FirebaseAuth.getInstance();
        FirebaseUser user = mDriverAuth.getCurrentUser();
        userID = Objects.requireNonNull(mDriverAuth.getCurrentUser()).getUid();
        firebaseDatabase = FirebaseDatabase.getInstance();

        mProfileImage.setOnClickListener(v -> showImagePickDialog());

        assert user != null;
        if (user.getUid().equals(userID)) {

            DatabaseReference mDriverDatabase = firebaseDatabase.getReference("RideUsers").child("Drivers").child(userID);
            mDriverDatabase.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists() && snapshot.getChildrenCount() > 0) {
                        Map<String, Object> map = (Map<String, Object>) snapshot.getValue();
                        assert map != null;
                        if (map.get("name") != null) {
                            String name = Objects.requireNonNull(map.get("name")).toString();
                            mNameField.setText(name);
                        }

                        if (map.get("phone") != null) {
                            String phone = Objects.requireNonNull(map.get("phone")).toString();
                            mPhoneField.setText(phone);
                        }

                        if (map.get("car") != null) {
                            String car = Objects.requireNonNull(map.get("car")).toString();
                            mCarField.setText(car);
                        }

                        if (map.get("service") != null) {
                            String service = Objects.requireNonNull(map.get("service")).toString();
                            switch (service) {
                                case "Ride Mini":
                                    mRadioGroup.check(R.id.UberX);
                                    break;
                                case "Ride - X":
                                    mRadioGroup.check(R.id.UberBlack);
                                    break;
                                case "Ride - Prime":
                                    mRadioGroup.check(R.id.UberXl);
                                    break;
                            }
                        }

                        if (map.get("image") != null) {
                            String imageUrl = Objects.requireNonNull(map.get("image")).toString();
                            try {
                                Picasso.get().load(imageUrl).into(mProfileImage);
                            } catch (Exception e) {
                                Picasso.get().load(R.mipmap.ic_default_user).into(mProfileImage);
                            }
                        }
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {

                }
            });
        }

        DatabaseReference reference = FirebaseDatabase.getInstance().getReference("RideUsers");
        reference.child("Drivers").child(userID).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {

                if (dataSnapshot.child("image").exists()) {
                    image = Objects.requireNonNull(dataSnapshot.child("image").getValue()).toString();
                }

            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });

        mBack.setOnClickListener(v -> {
            Intent mapIntent = new Intent(DriverSettingsActivity.this, DriverMapActivity.class);
            startActivity(mapIntent);
        });

        mKyc.setOnClickListener(v -> {
            Intent kycIntent = new Intent(DriverSettingsActivity.this, KycActivity.class);
            startActivity(kycIntent);
        });

        mSave.setOnClickListener(v -> {

            mCar = mCarField.getText().toString();
            mName = mNameField.getText().toString();

            int selectId = mRadioGroup.getCheckedRadioButtonId();
            final RadioButton radioButton = findViewById(selectId);
            if (radioButton.getText() == null) {
                return;
            }
            mService = radioButton.getText().toString();

            if (mName.length() == 0) {
                showToast("Enter Name");
            } else if (mCar.length() == 0) {
                showToast("Enter Car Name");
            } else {
                progressDialog = new ProgressDialog(DriverSettingsActivity.this);
                progressDialog.setMessage("Saving");
                progressDialog.setCanceledOnTouchOutside(false);
                progressDialog.show();

                if (imageUriResultCrop == null) {

                    DatabaseReference mDriverDatabase = firebaseDatabase.getReference("RideUsers").child("Drivers").child(userID);
                    Map userInfo = new HashMap();
                    userInfo.put("car", mCar);
                    userInfo.put("image", image);
                    userInfo.put("name", mName);
                    userInfo.put("service", mService);
                    mDriverDatabase.updateChildren(userInfo).addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {

                            DatabaseReference dbRef = firebaseDatabase.getReference("Users").child(userID);
                            Map userMap = new HashMap();
                            userMap.put("name", mName);
                            userMap.put("image", image);
                            dbRef.updateChildren(userMap).addOnCompleteListener(task12 -> {

                                if (task12.isSuccessful()) {
                                    Intent saveIntent = new Intent(DriverSettingsActivity.this, DriverMapActivity.class);
                                    startActivity(saveIntent);
                                    finish();
                                    showToast("Saved Successfully");
                                } else {
                                    String message = Objects.requireNonNull(task12.getException()).getMessage();
                                    showToast("" + message);
                                }
                                progressDialog.dismiss();
                            });
                        } else {
                            String message = Objects.requireNonNull(task.getException()).getMessage();
                            progressDialog.dismiss();
                            showToast("" + message);
                        }
                    }).addOnFailureListener(e -> {
                        progressDialog.dismiss();
                        showToast("" + e.getMessage());
                    });
                } else {

                    String fileNameAndPath = "Driver_Pictures/" + "image" + userID;
                    StorageReference storageReference = FirebaseStorage.getInstance().getReference(fileNameAndPath);
                    storageReference.putFile(imageUriResultCrop)
                            .addOnSuccessListener(taskSnapshot -> {
                                Task<Uri> p_uriTask = taskSnapshot.getStorage().getDownloadUrl();
                                while (!p_uriTask.isSuccessful()) ;
                                Uri p_downloadUri = p_uriTask.getResult();
                                if (p_uriTask.isSuccessful()) {

                                    Map<String, Object> map = new HashMap<>();
                                    map.put("image", "" + p_downloadUri);
                                    map.put("name", "" + mName);

                                    DatabaseReference ref1 = FirebaseDatabase.getInstance().getReference("Users");
                                    ref1.child(userID).updateChildren(map).addOnSuccessListener(aVoid -> {

                                        Map<String, Object> map2 = new HashMap<>();
                                        map2.put("car", "" + mCar);
                                        map2.put("image", "" + p_downloadUri);
                                        map2.put("name", "" + mName);
                                        map2.put("service", "" + mService);

                                        DatabaseReference ref2 = FirebaseDatabase.getInstance().getReference("RideUsers");
                                        ref2.child("Drivers").child(userID).updateChildren(map2).addOnCompleteListener(task -> {
                                            Intent saveIntent = new Intent(DriverSettingsActivity.this, DriverMapActivity.class);
                                            startActivity(saveIntent);
                                            finish();
                                            progressDialog.dismiss();
                                            showToast("Saved Successfully");
                                        });
                                    }).addOnFailureListener(e -> {
                                        progressDialog.dismiss();
                                        showToast("" + e.getMessage());
                                    });
                                }
                            }).addOnFailureListener(e -> {
                        progressDialog.dismiss();
                        showToast("" + e.getMessage());
                    });
                }
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

    private void showImagePickDialog() {

        String[] options = {"Camera", "Gallery"};

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Pick Image")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        if (!checkCameraPermission()) {
                            requestCameraPermission();
                        } else {
                            pickFromCamera();
                        }
                    }
                    if (which == 1) {
                        if (!checkStoragePermission()) {
                            requestStoragePermission();
                        } else {
                            pickFromGallery();
                        }
                    }
                }).show();
    }

    private void pickFromGallery() {

        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        startActivityForResult(intent, IMAGE_PICK_GALLERY_CODE);
    }

    private void pickFromCamera() {

        ContentValues cv = new ContentValues();
        cv.put(MediaStore.Images.Media.TITLE, "Group Image Icon Title");
        cv.put(MediaStore.Images.Media.DESCRIPTION, "Group Image Icon Description");
        image_uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);

        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, image_uri);
        startActivityForResult(intent, IMAGE_PICK_CAMERA_CODE);
    }

    private boolean checkStoragePermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == (PackageManager.PERMISSION_GRANTED);
    }

    private void requestStoragePermission() {
        ActivityCompat.requestPermissions(this, storagePermission, STORAGE_REQUEST_CODE);
    }

    private boolean checkCameraPermission() {
        boolean result = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == (PackageManager.PERMISSION_GRANTED);
        boolean result1 = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == (PackageManager.PERMISSION_GRANTED);
        return result && result1;
    }

    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this, cameraPermission, CAMERA_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case CAMERA_REQUEST_CODE: {
                if (grantResults.length > 0) {
                    boolean cameraAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                    boolean storageAccepted = grantResults[1] == PackageManager.PERMISSION_GRANTED;
                    if (cameraAccepted && storageAccepted) {
                        pickFromCamera();
                    } else {
                        showToast("Permissions Required");
                    }
                }
            }
            break;
            case STORAGE_REQUEST_CODE: {
                if (grantResults.length > 0) {
                    boolean storageAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                    if (storageAccepted) {
                        pickFromGallery();
                    } else {
                        showToast("Permission Required");
                    }
                }
            }
            break;
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {

        if (resultCode == RESULT_OK) {

            if (requestCode == IMAGE_PICK_GALLERY_CODE) {

                image_uri = data.getData();
                if (image_uri != null) {
                    startCrop(image_uri);
                }
            }
            else if (requestCode == IMAGE_PICK_CAMERA_CODE) {
                startCrop(image_uri);
            }
            else if (requestCode == UCrop.REQUEST_CROP) {
                imageUriResultCrop = UCrop.getOutput(data);
                if (imageUriResultCrop != null) {
                    mProfileImage.setImageURI(imageUriResultCrop);
                }
            }
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void startCrop(Uri image_uri) {
        String destinationFileName = "SampleCrop";
        destinationFileName += ".jpg";
        UCrop uCrop = UCrop.of(image_uri, Uri.fromFile(new File(getCacheDir(), destinationFileName)));
        uCrop.withAspectRatio(1, 1);
        uCrop.withMaxResultSize(512, 512);
        uCrop.withOptions(getCropOptions());
        uCrop.start(DriverSettingsActivity.this);
    }

    private UCrop.Options getCropOptions() {
        UCrop.Options options = new UCrop.Options();
        options.setCompressionQuality(100);
        options.setCompressionFormat(Bitmap.CompressFormat.PNG);
        options.setHideBottomControls(false);
        options.setFreeStyleCropEnabled(true);
        options.setStatusBarColor(getResources().getColor(R.color.colorPrimaryDark));
        options.setToolbarColor(getResources().getColor(R.color.colorPrimary));
        options.setToolbarTitle("Display Picture");
        return options;
    }
}