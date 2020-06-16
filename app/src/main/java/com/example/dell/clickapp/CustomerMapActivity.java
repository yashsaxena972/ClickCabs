package com.example.dell.clickapp;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationListener;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.support.design.widget.FloatingActionButton;
import android.os.Bundle;
import android.Manifest;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.DrawerLayout;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.Registry;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.module.AppGlideModule;
import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQuery;
import com.firebase.geofire.GeoQueryEventListener;
import com.firebase.ui.storage.images.FirebaseImageLoader;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;

public class CustomerMapActivity extends FragmentActivity
        implements OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, com.google.android.gms.location.LocationListener {

    private GoogleMap mMap;
    private LatLng pickupLocation;
    private String userId;
    private Marker mPickupLocationMarker;
    private TextView customerStateTextView;
    private Button cancelButton;

    private DrawerLayout mDrawerLayout;
    private CoordinatorLayout mCoordinatorLayout;

    private Marker mDriverMarker;
    private DatabaseReference driverLocationRef;
    private ValueEventListener driverLocationRefListener;

    // Counter for updating the text on the request button when the location has been determined
    private int mCounter =0;

    GoogleApiClient mGoogleApiClient;
    Location mLastLocation;
    LocationRequest mLocationRequest;
    GeoQuery geoQuery;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer_map);

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.

        userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        customerStateTextView = (TextView) findViewById(R.id.customer_state_text_view);
        cancelButton = (Button)findViewById(R.id.cancel_button);


        customerStateTextView.setTextSize(15);
        customerStateTextView.setText(R.string.getting_location);

        mDrawerLayout = findViewById(R.id.customer_drawer_layout);
        NavigationView navigationView = findViewById(R.id.customer_nav_view);
        navigationView.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem menuItem) {
                // set item as selected to persist highlight
                menuItem.setChecked(true);
                // close drawer when item is tapped
                mDrawerLayout.closeDrawers();

                // Add code here to update the UI based on the item selected
                // For example, swap UI fragments here
                switch (menuItem.getItemId()){
                    case R.id.logout_customer:
                        FirebaseAuth.getInstance().signOut();
                        Intent logoutIntent = new Intent(CustomerMapActivity.this,MainActivity.class);
                        startActivity(logoutIntent);
                        finish();
                        break;

                    case R.id.settings_customer:
                        Intent settingsIntent = new Intent(CustomerMapActivity.this,CustomerSettingsActivity.class);
                        startActivity(settingsIntent);
                        // We don't want to finish this activity because we want to open the settings activity on top of this one
                        break;
                }

                return true;
            }
        });

        // Action when the Ride Now button has been clicked
        customerStateTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                cancelButton.setVisibility(View.VISIBLE);
                userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

                DatabaseReference ref = FirebaseDatabase.getInstance().getReference("customerRequest");
                GeoFire geoFire = new GeoFire(ref);
                geoFire.setLocation(userId, new GeoLocation(mLastLocation.getLatitude(),mLastLocation.getLongitude()));

                pickupLocation = new LatLng(mLastLocation.getLatitude(),mLastLocation.getLongitude());
                if(mPickupLocationMarker != null) {
                    mPickupLocationMarker = mMap.addMarker(new MarkerOptions().position(pickupLocation).title("You're here")
                            .icon(BitmapDescriptorFactory.fromResource(R.mipmap.ic_pickup)));
                }

                customerStateTextView.setTextSize(15);
                customerStateTextView.setText(R.string.getting_ride);

                getNearestDriver();
            }
        });

        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                geoQuery.removeAllListeners();
                driverLocationRef.removeEventListener(driverLocationRefListener);


                DatabaseReference ref = FirebaseDatabase.getInstance().getReference("customerRequest");
                GeoFire geoFire = new GeoFire(ref);
                geoFire.removeLocation(userId);

                // We also need to remove the customerRideId which had been assigned to a driver
                if(driverFound == true) {
                    DatabaseReference refCustomerRideId = FirebaseDatabase.getInstance().getReference().child("Users")
                            .child("Drivers").child(driverFoundId);
                    refCustomerRideId.setValue(true);
                    driverFound = false;
                    driverFoundId = null;

                }
                radius = 1;
                if(mDriverMarker != null){
                    mDriverMarker.remove();
                }
                if(mPickupLocationMarker != null){
                    mPickupLocationMarker.remove();
                }

                cancelButton.setVisibility(View.GONE);
                customerStateTextView.setTextSize(25);
                customerStateTextView.setText(R.string.request);

            }
        });
    }

    private int radius =1;
    private boolean driverFound = false;
    private String driverFoundId;

    /**
     * This is a recursive method because if no driver is found in one iteration of this method, then the radius is incremented
     * by 1 and the method is called again with the new value of radius
     */
    private void getNearestDriver(){
        final DatabaseReference driverLocation = FirebaseDatabase.getInstance().getReference().child("driversAvailable");
        GeoFire geoFire = new GeoFire(driverLocation);

        // This query takes in the pickupLocation as the center and a radius and returns keys from the
        // child "driversAvailable"(as specified above) that are within that circle of locations
        geoQuery = geoFire.queryAtLocation(new GeoLocation(pickupLocation.latitude,pickupLocation.longitude),radius);

        // Remove the listener for the previous iteration of the recursive method getNearestDriver
        geoQuery.removeAllListeners();

        geoQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
            // This method is called when the location of the key matches the query criteria, ie,
            // a driver has been found in the circle. In this case, we need to set the value of DriverFound as true
            // which is the stopping condition of this recursive function.
            @Override
            public void onKeyEntered(String key, GeoLocation location) {
                if(!driverFound){
                    driverFound = true;
                    driverFoundId = key;

                    // When a driver has been found, we need to assign the customer to that driver
                    DatabaseReference driverRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers")
                            .child(driverFoundId);
                    userId = FirebaseAuth.getInstance().getCurrentUser().getUid();  // In this activity, userId is the customer id
                    HashMap map = new HashMap();
                    map.put("customerRideId",userId);
                    driverRef.updateChildren(map);

                    // After this, we need to show the position of the driver to the customer which should
                    // update each second
                    customerStateTextView.setTextSize(15);
                    customerStateTextView.setText(R.string.getting_driver_location);
                    getDriverLocation();

                }
            }

            @Override
            public void onKeyExited(String key) {

            }

            @Override
            public void onKeyMoved(String key, GeoLocation location) {

            }

            // This method is called to return the results of the query
            @Override
            public void onGeoQueryReady() {
                // If no driver was found, then increment the radius and search again
                if(!driverFound){
                    radius++;
                    getNearestDriver();
                }
            }

            @Override
            public void onGeoQueryError(DatabaseError error) {

            }
        });
    }


    /**
     * This method is to get the driver location and display its marker on the customer's screen, ie,
     * on CustomerMapActivity
     */
    private void getDriverLocation(){
        driverLocationRef = FirebaseDatabase.getInstance().getReference().child("driversWorking")
                .child(driverFoundId).child("l");   // Because GeoFire stores location under the key "l"
        driverLocationRefListener = driverLocationRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                // This is called whenever the location of the driver is updated, ie each second in this case
                if(dataSnapshot.exists()){
                    List<Object> map = (List<Object>) dataSnapshot.getValue();   // Since the value of the dataSnapshot is 2 keys (latitude and longitude), we need a list of objects to hold it
                    double locationLat = 0;
                    double locationLng = 0;

                    if(map.get(0) != null){ // get(0) because GeoFire stores latitude under the key "0"
                        locationLat = Double.parseDouble(map.get(0).toString());
                    }
                    if(map.get(1) != null){ // get(1) because GeoFire stores longitude under the key "1"
                        locationLng = Double.parseDouble(map.get(1).toString());
                    }
                    LatLng driverLatLng = new LatLng(locationLat,locationLng);

                    // Remove the previous marker each time the location is updated
                    if(mDriverMarker != null){
                        mDriverMarker.remove();
                    }

                    Location loc1 = new Location("");
                    loc1.setLatitude(pickupLocation.latitude);
                    loc1.setLongitude(pickupLocation.longitude);

                    Location loc2 = new Location("");
                    loc2.setLatitude(driverLatLng.latitude);
                    loc2.setLongitude(driverLatLng.longitude);

                    int distance = (int) loc1.distanceTo(loc2);
                    customerStateTextView.setTextSize(15);

                    if(distance<100){
                        customerStateTextView.setText("Driver has arrived at your location");
                    }
                    else{
                        customerStateTextView.setText("Driver found " + String.valueOf(distance) + "m to your location");
                    }

                    mDriverMarker = mMap.addMarker(new MarkerOptions().position(driverLatLng).title("Your driver")
                            .icon(BitmapDescriptorFactory.fromResource(R.mipmap.ic_cab)));
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                // Nothing required to be done here
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

        // After the map is ready, we need to enable the location(gps of the device), for which we must first check if the app
        // has the required permissions
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        buildGoogleApiClient();
        mMap.setMyLocationEnabled(true);
    }

    // Helper method for building the Google API
    private synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        mGoogleApiClient.connect();
    }

    // This method is called each time the location is updated, ie, each second in this case
    @Override
    public void onLocationChanged(Location location) {
        if(mCounter==0){
            customerStateTextView.setTextSize(25);
            customerStateTextView.setText(R.string.request);
        }
        mLastLocation = location;

        // Get the latitude and longitude of the marker at each location update
        LatLng latlng = new LatLng(location.getLatitude(),location.getLongitude());
        // To keep the camera moving with the marker
        mMap.moveCamera(CameraUpdateFactory.newLatLng(latlng));
        // Zoom values range from 1 to 21
        mMap.animateCamera(CameraUpdateFactory.zoomTo(18));

        mCounter++;
    }


    @Override
    public void onConnected(@Nullable Bundle bundle) {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(1000);
        mLocationRequest.setFastestInterval(1000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    /**
     * We need to remove the driver id from the Firebase database as soon as the driver leaves
     * the DriverMapActivity on his device because it means that he is unavailable for driving
     */
    @Override
    protected void onStop() {
        super.onStop();

        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("customerRequest");
        GeoFire geoFire = new GeoFire(ref);
        geoFire.removeLocation(userId);

        // We also need to remove the customerRideId which had been assigned to a driver
        if(driverFound == true) {
            DatabaseReference refCustomerRideId = FirebaseDatabase.getInstance().getReference().child("Users")
                    .child("Drivers").child(driverFoundId);
            refCustomerRideId.setValue(true);
            driverFound = false;
            driverFoundId = null;
            radius = 1;
        }
        if(mDriverMarker != null) {
            mDriverMarker.remove();
        }
        if(mPickupLocationMarker != null) {
            mPickupLocationMarker.remove();
        }

    }
}
