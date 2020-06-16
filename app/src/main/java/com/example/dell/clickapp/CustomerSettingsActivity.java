package com.example.dell.clickapp;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;


import com.bumptech.glide.Glide;
import com.bumptech.glide.Registry;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.module.AppGlideModule;
import com.bumptech.glide.request.RequestOptions;
import com.firebase.ui.storage.images.FirebaseImageLoader;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.OnProgressListener;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.squareup.picasso.Picasso;


import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.prefs.PreferenceChangeListener;

public class CustomerSettingsActivity extends AppCompatActivity {

    private static String mName,mPhoneNumber;
    private static DatabaseReference mCustomerDatabase;
    private static String userId;
    private ImageView profilePhoto;
    private Uri dpUri;
    private final int RESULT_GALLERY = 1;
    private final int RESULT_CROP = 2;
    private String picturePath;
    private Uri downloadUri;
    private String profileImageUrl;
    private StorageReference filePath;
    private ProgressDialog progressDialog;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer_settings);

        profilePhoto = (ImageView)findViewById(R.id.profilePhoto);

        userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        mCustomerDatabase = FirebaseDatabase.getInstance().getReference().child("Users").child("Customers").child(userId);

        filePath= FirebaseStorage.getInstance().getReference().child("profile_photos").child(userId);

        getUserInfo();

        profilePhoto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_PICK);
                intent.setType("image/*");
                intent.putExtra("crop","true");
                intent.putExtra("aspectX",1);
                intent.putExtra("aspectY",1);
                intent.putExtra("outputX",80);
                intent.putExtra("outputY",80);

                try {
                    intent.putExtra("return-data", true);
                    startActivityForResult(intent, RESULT_GALLERY);
                }
                catch (ActivityNotFoundException anfe){ }
            }
        });

    }


    /**
     * Helper method that updates the user info in the respective views when the user of the app
     * enters the CustomerSettingsActivity
     */
    private void getUserInfo() {
        mCustomerDatabase.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists() && dataSnapshot.getChildrenCount()>0){
                    Map<String,Object> map = (Map<String, Object>) dataSnapshot.getValue();
                    if (map.get("profilePhotoUrl") != null){
                        profileImageUrl = map.get("profilePhotoUrl").toString();
                        GlideUrl glideUrl = new GlideUrl(profileImageUrl);
                        // We use the Glide library here to load the image url as an image into the ImageView
                        Glide.with(getApplication()).load(glideUrl).apply(new RequestOptions().skipMemoryCache(true).placeholder(R.mipmap.ic_default_dp).diskCacheStrategy(DiskCacheStrategy.DATA)).into(profilePhoto);

                        // Picasso.get().load(profileImageUrl).resize(50,50).centerCrop().into(profilePhoto);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
            }
        });
    }



    /**
     * Hepler method for storing the profile photo into the Firebase storage
     */
    private void saveProfilePhoto() {
        // Initialize a bitmap
        Bitmap bitmap = null;

        try {
            // This line takes the image uri/file path on the device and converts it to a bitmap
            bitmap = MediaStore.Images.Media.getBitmap(getApplication().getContentResolver(),dpUri);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Converting the bitmap to an array of bytes which can be stored in the Firebase storage
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // The bitmap file should be compressed because Firebase offers limited storage for free
        bitmap.compress(Bitmap.CompressFormat.JPEG,20,baos);
        byte[] data = baos.toByteArray();
        // Upload the array of bytes to the Firebase storage
        final UploadTask uploadTask = filePath.putBytes(data);

        // After the image has been successfully been uploaded to the Firebase storage, we can get a
        // download URL which will be stored in the Firebase database
        uploadTask.addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                Task<Uri> downloadUrl = taskSnapshot.getMetadata().getReference().getDownloadUrl();
                Map newImage = new HashMap();
                newImage.put("profilePhotoUrl",downloadUrl.toString());
                mCustomerDatabase.updateChildren(newImage);

                Toast.makeText(CustomerSettingsActivity.this,R.string.profile_photo_updated,Toast.LENGTH_SHORT).show();
                getUserInfo();
            }
        });

        uploadTask.addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Toast.makeText(CustomerSettingsActivity.this,R.string.failure_uploading_dp,Toast.LENGTH_SHORT).show();
            }
        });

    }

    /**
     * This callback method is automatically called when a photo has been picked from the gallery using the intent
     * @param requestCode
     * @param resultCode
     * @param data
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == RESULT_GALLERY && resultCode == Activity.RESULT_OK){
            if (data != null) {
                final Uri imageUri = data.getData();
                dpUri = imageUri;

                profilePhoto.setImageURI(dpUri);
                profilePhoto.setScaleType(ImageView.ScaleType.CENTER_CROP);

                if (dpUri != null) {
                    saveProfilePhoto();
                }
            }

        }

        if (resultCode == Activity.RESULT_CANCELED){
            Toast.makeText(this,"Unable to fetch image",Toast.LENGTH_SHORT).show();
        }
    }


    // Nested class for the preferences fragment
    public static class CustomerPreferenceFragment extends PreferenceFragment implements Preference.OnPreferenceChangeListener{
        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.activity_settings_customer);


            userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
            mCustomerDatabase = FirebaseDatabase.getInstance().getReference().child("Users").child("Customers").child(userId);


            // Calling the helper method bindPreferenceSummaryToValue that calls the onPreferenceChange
            // method
            Preference customerName = findPreference(getString(R.string.settings_name_key));
            bindPreferenceSummaryToValue(customerName);

            Preference phoneNumber = findPreference(getString(R.string.settings_phone_number_key));
            bindPreferenceSummaryToValue(phoneNumber);
        }


        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {
            saveUserInformation(preference,value);

            String stringValue = value.toString();
            preference.setSummary(stringValue);
            return true;
        }

        /**
         * Helper method to bind the value of the preference as summary of the preference
         * @param preference
         */
        private void bindPreferenceSummaryToValue(Preference preference) {
            preference.setOnPreferenceChangeListener(this);
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(preference.getContext());
            String preferenceString = preferences.getString(preference.getKey(),"");
            onPreferenceChange(preference,preferenceString);
        }


        private void saveUserInformation(Preference preference,Object value) {

            if (preference == findPreference(getString(R.string.settings_name_key))) {
                mName = value.toString();
            }

            if (preference == findPreference(getString(R.string.settings_phone_number_key))) {
                mPhoneNumber = value.toString();
            }

            Map userInfo = new HashMap();
            if (mName != null) {
                userInfo.put("name", mName);
            }
            if (mPhoneNumber != null){
                userInfo.put("phone",mPhoneNumber);
            }
            mCustomerDatabase.updateChildren(userInfo);

        }


    }

}