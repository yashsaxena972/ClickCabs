package com.example.dell.clickapp;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.NavigationView;
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
import android.widget.TextView;

import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
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

import java.util.List;
import java.util.Map;

public class DriverMapActivity extends FragmentActivity
        implements OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, com.google.android.gms.location.LocationListener {

    private GoogleMap mMap;
    private DrawerLayout mDrawerLayout;
    private Marker mPickupLocationMarker;
    private TextView driverStateTextView;
    private String userId;

    private DatabaseReference assignedCustomerPickupLocationRef;
    private ValueEventListener assignedCustomerPickupLocationRefListener;

    GoogleApiClient mGoogleApiClient;
    Location mLastLocation;
    LocationRequest mLocationRequest;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_map);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.

        userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        driverStateTextView = (TextView)findViewById(R.id.driver_state_text_view);


        mDrawerLayout = findViewById(R.id.drawer_layout);
        NavigationView navigationView = findViewById(R.id.nav_view);
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
                    case R.id.logout_driver:
                        FirebaseAuth.getInstance().signOut();
                        Intent intent = new Intent(DriverMapActivity.this,MainActivity.class);
                        startActivity(intent);
                        finish();
                        break;
                }

                return true;
            }
        });


        getAssignedCustomer();
    }

    private String customerId = "";
    /**
     * Helper method that extracts the value of the customerId that was assigned to the driver by the
     * CustomerMapActivity
     */
    private void getAssignedCustomer() {
        userId = FirebaseAuth.getInstance().getCurrentUser().getUid();   // userId refers to driverId in this activity
        final DatabaseReference assignedCustomerRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers")
                .child(userId).child("customerRideId");
        assignedCustomerRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {

                if(dataSnapshot.exists()){
                    customerId = dataSnapshot.getValue().toString();
                    driverStateTextView.setTextSize(15);
                    driverStateTextView.setText(R.string.getting_customer_location);
                    // Now we need to extract the location of the customer from the customerId we just obtained
                    getAssignedCustomerPickupLocation();
                }
                else{
                    customerId = "";
                    if(mPickupLocationMarker != null) {
                        mPickupLocationMarker.remove();
                    }
                    if(assignedCustomerPickupLocationRef != null) {
                        assignedCustomerPickupLocationRef.removeEventListener(assignedCustomerPickupLocationRefListener);
                    }
                }
             }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }

    /**
     * Helper method to get the pickup location of the customer after having acquired the customerId
     */
    private void  getAssignedCustomerPickupLocation(){
        assignedCustomerPickupLocationRef = FirebaseDatabase.getInstance().getReference()
                .child("customerRequest").child(customerId).child("l");  // Instead of searching the customerId in the Id assigned to the driver, we can also directly search it in the customerRequest
        assignedCustomerPickupLocationRefListener = assignedCustomerPickupLocationRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                // For complete information on this method, see the equivalent method getDriverLocation() in CustomerMapActivity
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
                    LatLng customerLatLng = new LatLng(locationLat,locationLng);
                    if(mPickupLocationMarker != null){
                        mPickupLocationMarker.remove();
                    }
                    mPickupLocationMarker = mMap.addMarker(new MarkerOptions().position(customerLatLng).title("Pickup location")
                            .icon(BitmapDescriptorFactory.fromResource(R.mipmap.ic_pickup)));
                    driverStateTextView.setTextSize(25);
                    driverStateTextView.setText(R.string.driver_working);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

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
       if (getApplicationContext()!= null) {
           mLastLocation = location;

           // Get the latitude and longitude of the marker at each location update
           LatLng latlng = new LatLng(location.getLatitude(), location.getLongitude());
           // To keep the camera moving with the marker
           mMap.moveCamera(CameraUpdateFactory.newLatLng(latlng));
           // Zoom values range from 1 to 21
           mMap.animateCamera(CameraUpdateFactory.zoomTo(18));

           // We need to save the location of all drivers whose DriverMapActivity is opened in realtime
           // so that we can determine the nearest available driver. We accomplish this using GeoFire
           userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

           DatabaseReference refAvailable = FirebaseDatabase.getInstance().getReference("driversAvailable");
           DatabaseReference refWorking = FirebaseDatabase.getInstance().getReference("driversWorking");
           GeoFire geoFireAvailable = new GeoFire(refAvailable);
           GeoFire geoFireWorking = new GeoFire(refWorking);

           // After each time the location changes, we need to check if a customerId has been assigned to the driver or not
           // If the driver was assigned a customer, we need to remove him from driversAvailable and put him in
           // driversWorking
            switch (customerId) {
                // When no customerId has been assigned
                case "":
                    driverStateTextView.setTextSize(25);
                    driverStateTextView.setText(R.string.driver_idle);
                    geoFireWorking.removeLocation(userId);
                    geoFireAvailable.setLocation(userId, new GeoLocation(mLastLocation.getLatitude(), mLastLocation.getLongitude()));
                    break;

                // When a customerId has been assigned
                default:
                    geoFireAvailable.removeLocation(userId);
                    geoFireWorking.setLocation(userId, new GeoLocation(mLastLocation.getLatitude(), mLastLocation.getLongitude()));
                    break;
            }
       }

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

        LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);


        DatabaseReference refAvailable = FirebaseDatabase.getInstance().getReference("driversAvailable");
        DatabaseReference refWorking = FirebaseDatabase.getInstance().getReference("driversWorking");
        GeoFire geoFireAvailable = new GeoFire(refAvailable);
        GeoFire geoFireWorking = new GeoFire(refWorking);
        geoFireAvailable.removeLocation(userId);
        geoFireWorking.removeLocation(userId);

        if(mPickupLocationMarker != null) {
            mPickupLocationMarker.remove();
        }
    }
}
