package com.example.googlemapdire;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.renderscript.RenderScript;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import com.androidnetworking.AndroidNetworking;
import com.androidnetworking.error.ANError;
import com.androidnetworking.interfaces.JSONArrayRequestListener;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
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
import com.google.gson.JsonObject;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import static android.app.NotificationChannel.DEFAULT_CHANNEL_ID;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener{

    private GoogleMap mMap;
    GoogleApiClient mGoogleApiClient;
    Location mLastLocation;
    ArrayList<Marker> mMarkers = new ArrayList<Marker>();
    LocationRequest mLocationRequest;
    private Timer timer;

    Bitmap markerIcon;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkLocationPermission();
        }

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        // register this onMapReady for get the map instance later
        mapFragment.getMapAsync(this);

        // bitmap for parking icon P
        BitmapDrawable bitmapdraw = (BitmapDrawable)getResources().getDrawable(R.drawable.icon);
        markerIcon = Bitmap.createScaledBitmap(bitmapdraw.getBitmap(), 120, 120, false);

        // initialize for rest api calling at the begnining
        AndroidNetworking.initialize(getApplicationContext());
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        //Initialize Google Play Services
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                System.out.println("permission not granted");
                return;
            }
        }

        buildGoogleApiClient();
        mMap.setMyLocationEnabled(true);

        mMap.getUiSettings().setCompassEnabled(false);
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setMyLocationButtonEnabled(true);

        startTimer();
    }

    public void startTimer() {
        timer = new Timer();
        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                // do work on a UI thread, but update on main thread
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        drawMarkers();
                    }
                });
            }
        };
        // 10 sec timer to get location from web-service
        timer.scheduleAtFixedRate(timerTask, 0, 20000);
    }

    public void drawMarkers()
    {
        // get the webservice host
        String host  = getString(R.string.host);
        System.out.println("drawMarkers: " + host);
        // rest get location
        AndroidNetworking.get("http://" + host + "/locations")
        .build()
        .getAsJSONArray(new JSONArrayRequestListener() {
            @Override
            public void onResponse(JSONArray response) {
                //remove previous markers
                for (int i = 0; i < mMarkers.size(); ++i) {
                    mMarkers.get(i).remove();
                }
                System.out.println("------ delete previous markres");
                mMarkers.clear();
                System.out.println("------ drawMarkers: " + response.length());
                for(int i = 0; i < response.length(); ++i) {

                    try {
                        JSONObject location = response.getJSONObject(i);
                        double lng = location.getDouble("lon");
                        double lat = location.getDouble("lat");
                        String lname = location.getString("lname");
                        int bc = location.getInt("bc");
                        int slots = location.getInt("slots");
                        // donest show if no available locations
                        if (slots - bc == 0) continue;

                        System.out.println("------ drawMarkers: adding marker:"
                                + i + "[lng:" + lng + " lat:" + lat + " lname:"+ lname +" bc:"+bc +"/" + slots +"]");

                        LatLng latLng = new LatLng(lat, lng);
                        MarkerOptions markerOptions = new MarkerOptions();
                        markerOptions.position(latLng);
                        markerOptions.title(lname + ": " + (slots -  bc) + " slots");// show available locations
                        markerOptions.icon(BitmapDescriptorFactory.fromBitmap(markerIcon));
                        mMarkers.add(mMap.addMarker(markerOptions));

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
            @Override
            public void onError(ANError error) {
                System.out.println("--------error: drawMarkers: " + error);
            }
        });
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
    public void onConnected(Bundle bundle) {
        // configure accuracy level for this application
        mLocationRequest = new LocationRequest();
        // Set the desired interval for active location updates, in milliseconds.
        mLocationRequest.setInterval(1000);
        mLocationRequest.setFastestInterval(1000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
        }
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        System.out.println("## onConnectionFailed----- ");
    }

    @Override
    public void onConnectionSuspended(int i) {
        System.out.println("## onConnectionSuspended----- ");
    }

    // the fucntion called on own-location changed
    @Override
    public void onLocationChanged(Location location) {
        System.out.println("##current location ----- " + location);
        // move map camera to current location
        LatLng currentLocation = new LatLng(location.getLatitude(), location.getLongitude());
        mMap.moveCamera(CameraUpdateFactory.newLatLng(currentLocation));
        mMap.animateCamera(CameraUpdateFactory.zoomTo(13));

        //stop location updates
        // just for focus on first time.then we dont need onLocationChanged to be called
        if (mGoogleApiClient != null) {
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
        }
    }

    public static final int MY_PERMISSIONS_REQUEST_LOCATION = 99;
    // check whether this program has the access to googlemap loactions
    public boolean checkLocationPermission(){
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            // Asking user if explanation is needed
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {

                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.

                //Prompt the user once explanation has been shown
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        MY_PERMISSIONS_REQUEST_LOCATION);
            } else {
                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        MY_PERMISSIONS_REQUEST_LOCATION);
            }
            return false;
        } else {
            return true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted. Do the
                    // contacts-related task you need to do.
                    if (ContextCompat.checkSelfPermission(this,
                            Manifest.permission.ACCESS_FINE_LOCATION)
                            == PackageManager.PERMISSION_GRANTED) {

                        if (mGoogleApiClient == null) {
                            buildGoogleApiClient();
                        }
                        mMap.setMyLocationEnabled(true);
                    }
                } else {
                    // Permission denied, Disable the functionality that depends on this permission.
                    Toast.makeText(this, "permission denied", Toast.LENGTH_LONG).show();
                }
                return;
            }
            // other 'case' lines to check for other permissions this app might request.
            // You can add here other case statements according to your requirement.
        }
    }
}