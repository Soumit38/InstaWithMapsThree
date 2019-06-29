package com.soumit.instaclone.maps;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.ImageLoaderConfiguration;
import com.nostra13.universalimageloader.core.assist.ImageSize;
import com.nostra13.universalimageloader.core.listener.SimpleImageLoadingListener;
import com.soumit.instaclone.R;
import com.soumit.instaclone.models.Photo;
import com.soumit.instaclone.utils.FirebaseMethods;
import com.soumit.instaclone.utils.ImageManager;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import de.hdodenhof.circleimageview.CircleImageView;

public class MapActivity extends AppCompatActivity implements
        OnMapReadyCallback, GoogleMap.OnMarkerClickListener, GoogleMap.OnMarkerDragListener {

    //firebase
    private FirebaseAuth mAuth;
    private FirebaseAuth.AuthStateListener mAuthListener;
    private FirebaseDatabase mFirebaseDatabase;
    private DatabaseReference myRef;
    private FirebaseMethods mFirebaseMethods;

    @Override
    public void onMapReady(GoogleMap googleMap) {
        Toast.makeText(this, "Map is Ready", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "onMapReady: map is ready");
        mMap = googleMap;

        if (mLocationPermissionsGranted) {
            getDeviceLocation();

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            //testing clear
            mMap.setMyLocationEnabled(true);
            mMap.getUiSettings().setMyLocationButtonEnabled(false);

            init();
        }
    }

    private static final String TAG = "MapActivity";
    private static final String FINE_LOCATION = Manifest.permission.ACCESS_FINE_LOCATION;
    private static final String COURSE_LOCATION = Manifest.permission.ACCESS_COARSE_LOCATION;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1234;
    private static final float DEFAULT_ZOOM = 16f;
//    private static final LatLngBounds LAT_LNG_BOUNDS = new LatLngBounds(
//            new LatLng(-40, -168), new LatLng(71, 136));

    //widgets
    private EditText mSearchText;
    private ImageView ic_magnify;
    private ImageView mGps;
    // Create global configuration and initialize ImageLoader with this config
    ImageLoader imageLoader = ImageLoader.getInstance();

    //vars
    private Boolean mLocationPermissionsGranted = false;
    private GoogleMap mMap;
    private FusedLocationProviderClient mFusedLocationProviderClient;
    private Bitmap capturedScreen;



    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);
        mSearchText = (EditText) findViewById(R.id.input_search);
        ic_magnify = (ImageView) findViewById(R.id.ic_magnify);
        mGps = (ImageView) findViewById(R.id.ic_gps);

        setupFirebaseAuth();

        getLocationPermission();
//        init();

    }


    private void init(){
        Log.d(TAG, "init: initializing");

        mSearchText.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View view, int keyCode, KeyEvent event) {
                if (event.getAction() != KeyEvent.ACTION_DOWN)
                    return false;
                if(keyCode == KeyEvent.KEYCODE_ENTER ){
                    geoLocate();
                    return true;
                }
                return false;
            }
        });

        ic_magnify.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG, "onClick: ic_magnify clicked !");
                geoLocate();
            }
        });

        mGps.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG, "onClick: mGps icon clicked!...");
                mSearchText.setText("");
                getDeviceLocation();
            }
        });

    }

    private void geoLocate(){
        Log.d(TAG, "geoLocate: geolocating");
        String searchString = mSearchText.getText().toString().trim();
        Geocoder geocoder = new Geocoder(MapActivity.this);
        List<Address> list = new ArrayList<>();
        try {
            list = geocoder.getFromLocationName(searchString, 1);
        } catch (IOException e) {
            Log.e(TAG, "geoLocate: IOException: " + e.getMessage() );
        }

        mMap.clear();

        if(list.size() > 0){
            Address address = list.get(0);
            Log.d(TAG, "geoLocate: found a location: " + address.toString());
//            Toast.makeText(this, address.toString(), Toast.LENGTH_SHORT).show();
            final LatLng centreLatLng = new LatLng(address.getLatitude(), address.getLongitude());
            myRef = FirebaseDatabase.getInstance().getReference();
            Query query = myRef.child("photos");
            query.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    for(DataSnapshot singleSnapshot : dataSnapshot.getChildren()){
                        final Photo photo = new Photo();
                        Map<String, Object> objectMap = (HashMap<String, Object>) singleSnapshot.getValue();
                        photo.setCaption(objectMap.get(getString(R.string.field_caption)).toString());
                        photo.setTags(objectMap.get(getString(R.string.field_tags)).toString());
                        photo.setPhoto_id(objectMap.get(getString(R.string.field_photo_id)).toString());
                        photo.setUser_id(objectMap.get(getString(R.string.field_user_id)).toString());
                        photo.setDate_created(objectMap.get(getString(R.string.field_date_created)).toString());
                        photo.setImage_path(objectMap.get(getString(R.string.field_image_path)).toString());
                        photo.setLocation_name(objectMap.get("location_name").toString());
                        photo.setLattitude((Double) objectMap.get("lattitude"));
                        photo.setLongitude((Double) objectMap.get("longitude"));

                        if(isAroundMe(centreLatLng, new LatLng(photo.getLattitude(), photo.getLongitude()), 10000)){
                            //creating markers
                            ImageSize targetSize = new ImageSize(90, 90); // result Bitmap will be fit to this size
                            imageLoader.loadImage(photo.getImage_path(), targetSize, new SimpleImageLoadingListener() {
                                @Override
                                public void onLoadingComplete(String imageUri, View view, Bitmap loadedImage) {
                                    // Do whatever you want with Bitmap
//                            drawCanvas(loadedImage, new LatLng(photo.getLattitude(), photo.getLongitude()));
//                            Bitmap tempConvBm = createCustomBitmapMarker(MapActivity.this, loadedImage, "test name");
                                    loadedImage = addWhiteBorder(loadedImage, 5);
                                    createMarker(photo.getLattitude(), photo.getLongitude(), photo.getLocation_name(), loadedImage);
                                }
                            });
                        }else {
                            Log.d(TAG, "onDataChange: isAroundMe --> false");
                        }

                        Log.d(TAG, "onDataChange: location_name -->" + photo.getLocation_name());
                        Log.d(TAG, "onDataChange: lattitude : " + photo.getLattitude() + "  longitude : " + photo.getLongitude());
                    }

                }

                @Override
                public void onCancelled(DatabaseError databaseError) {

                }
            });

            moveCamera(centreLatLng, DEFAULT_ZOOM, address.getAddressLine(0));
        }

    }

    private void getDeviceLocation(){
        Log.d(TAG, "getDeviceLocation: getting the devices current location");
        final String title = "";
        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

        try{
            if(mLocationPermissionsGranted){

                final Task location = mFusedLocationProviderClient.getLastLocation();
                location.addOnCompleteListener(new OnCompleteListener() {
                    @Override
                    public void onComplete(@NonNull Task task) {
                        if(task.isSuccessful()){
                            Log.d(TAG, "onComplete: found location!");
                            Location currentLocation = (Location) task.getResult();
                            //-----------------------------testing-------------------------------
                           /*
                            LatLng testlatLng = new LatLng(23.7495024,90.3637228);
                            Location.distanceBetween(
                                    currentLocation.getLatitude(), currentLocation.getLongitude(),
                                    testlatLng.latitude, testlatLng.longitude, results);
                            float distanceInMeters = results[0];
                            boolean isWithin10km = distanceInMeters < 10000;
                            if(isWithin10km){
                                MarkerOptions options = new MarkerOptions()
                                        .position(testlatLng)
                                        .title("Testing...");
                                mMap.addMarker(options);
                            }
                           */
                            //----------------------------------------------------------
                            mMap.clear();
                            LatLng currentLatLng = new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude() );

                            Geocoder gcd = new Geocoder(MapActivity.this, Locale.getDefault());
                            List<Address> addresses = null;
                            try {
                                addresses = gcd.getFromLocation(currentLatLng.latitude, currentLatLng.longitude, 1);
                            } catch (IOException e) {
                                Log.d(TAG, "onComplete: IOException " + e.getMessage() );
                            }
                            if (addresses.size() > 0) {
                                Log.d(TAG, "onComplete: gcd result ======>>>>>> " + addresses.get(0).getAddressLine(0) );
                                moveCamera( currentLatLng,  DEFAULT_ZOOM, addresses.get(0).getAddressLine(0));
                            }
                            else {
                                moveCamera( currentLatLng,  DEFAULT_ZOOM, "My location");
                            }
//                            moveCamera( currentLatLng,  DEFAULT_ZOOM, addresses.get(0).getAddressLine(0));

                            //will run the query here


                        }else{
                            Log.d(TAG, "onComplete: current location is null");
                            Toast.makeText(MapActivity.this, "unable to get current location", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        }catch (SecurityException e){
            Log.e(TAG, "getDeviceLocation: SecurityException: " + e.getMessage() );
        }
    }

    private boolean isAroundMe(LatLng centreLatLng, LatLng targetLatLng, int range){
        float[] results = new float[1];
        Location.distanceBetween(
                centreLatLng.latitude, centreLatLng.longitude,
                targetLatLng.latitude, targetLatLng.longitude, results);
        float distanceInMeters = results[0];
//        boolean isWithin10km = distanceInMeters < 10000;
        if(distanceInMeters<range)
            return true;

        return false;
    }

    private void moveCamera(final LatLng latLng, float zoom, String title){
        Log.d(TAG, "moveCamera: moving the camera to: lat: " + latLng.latitude + ", lng: " + latLng.longitude );
//        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, zoom));

        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, zoom));
        //dropping pin
//        if(!title.equals("My location")) {
        MarkerOptions options = new MarkerOptions()
                .position(latLng)
                .title(title)
                .draggable(true);
        mMap.addMarker(options);
        mMap.setOnMarkerClickListener(this);
        mMap.setOnMarkerDragListener(this);
//        }

        // query to show stored loc info
        myRef = FirebaseDatabase.getInstance().getReference();
        Query query = myRef.child("photos");

        query.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                for(DataSnapshot singleSnapshot : dataSnapshot.getChildren()){
                    final Photo photo = new Photo();
                    Map<String, Object> objectMap = (HashMap<String, Object>) singleSnapshot.getValue();
                    photo.setCaption(objectMap.get(getString(R.string.field_caption)).toString());
                    photo.setTags(objectMap.get(getString(R.string.field_tags)).toString());
                    photo.setPhoto_id(objectMap.get(getString(R.string.field_photo_id)).toString());
                    photo.setUser_id(objectMap.get(getString(R.string.field_user_id)).toString());
                    photo.setDate_created(objectMap.get(getString(R.string.field_date_created)).toString());
                    photo.setImage_path(objectMap.get(getString(R.string.field_image_path)).toString());
                    photo.setLocation_name(objectMap.get("location_name").toString());
                    photo.setLattitude((Double) objectMap.get("lattitude"));
                    photo.setLongitude((Double) objectMap.get("longitude"));

                    if(isAroundMe(latLng, new LatLng(photo.getLattitude(), photo.getLongitude()), 10000)){
                        //creating markers
                        ImageSize targetSize = new ImageSize(90, 90); // result Bitmap will be fit to this size
                        imageLoader.loadImage(photo.getImage_path(), targetSize, new SimpleImageLoadingListener() {
                            @Override
                            public void onLoadingComplete(String imageUri, View view, Bitmap loadedImage) {
                                // Do whatever you want with Bitmap
//                            drawCanvas(loadedImage, new LatLng(photo.getLattitude(), photo.getLongitude()));
//                            Bitmap tempConvBm = createCustomBitmapMarker(MapActivity.this, loadedImage, "test name");
                                loadedImage = addWhiteBorder(loadedImage, 5);
                                createMarker(photo.getLattitude(), photo.getLongitude(), photo.getLocation_name(), loadedImage);
                            }
                        });
                    }else {
                        Log.d(TAG, "onDataChange: isAroundMe --> false");
                    }

                    Log.d(TAG, "onDataChange: location_name -->" + photo.getLocation_name());
                    Log.d(TAG, "onDataChange: lattitude : " + photo.getLattitude() + "  longitude : " + photo.getLongitude());
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });

    }

    /**
     *  method to add a white border around bitmap image
     * @param bmp
     * @param borderSize
     * @return
     */
    private Bitmap addWhiteBorder(Bitmap bmp, int borderSize) {
        Bitmap bmpWithBorder = Bitmap.createBitmap(bmp.getWidth() + borderSize * 2,
                bmp.getHeight() + borderSize * 2, bmp.getConfig());
        Canvas canvas = new Canvas(bmpWithBorder);
        canvas.drawColor(Color.WHITE);
        canvas.drawBitmap(bmp, borderSize, borderSize, null);
        return bmpWithBorder;
    }

    private void initMap(){
        Log.d(TAG, "initMap: initializing map");
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);

        mapFragment.getMapAsync(MapActivity.this);
    }

    private void getLocationPermission(){
        Log.d(TAG, "getLocationPermission: getting location permissions");
        String[] permissions = {Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION};

        if(ContextCompat.checkSelfPermission(this.getApplicationContext(),
                FINE_LOCATION) == PackageManager.PERMISSION_GRANTED){
            if(ContextCompat.checkSelfPermission(this.getApplicationContext(),
                    COURSE_LOCATION) == PackageManager.PERMISSION_GRANTED){
                mLocationPermissionsGranted = true;
                initMap();
            }else{
                ActivityCompat.requestPermissions(this,
                        permissions,
                        LOCATION_PERMISSION_REQUEST_CODE);
            }
        }else{
            ActivityCompat.requestPermissions(this,
                    permissions,
                    LOCATION_PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        Log.d(TAG, "onRequestPermissionsResult: called.");
        mLocationPermissionsGranted = false;

        switch(requestCode){
            case LOCATION_PERMISSION_REQUEST_CODE:{
                if(grantResults.length > 0){
                    for(int i = 0; i < grantResults.length; i++){
                        if(grantResults[i] != PackageManager.PERMISSION_GRANTED){
                            mLocationPermissionsGranted = false;
                            Log.d(TAG, "onRequestPermissionsResult: permission failed");
                            return;
                        }
                    }
                    Log.d(TAG, "onRequestPermissionsResult: permission granted");
                    mLocationPermissionsGranted = true;
                    //initialize our map
                    initMap();
                }
            }
        }
    }

    /**
     *  alertdialogue to confirm location pick
     * @param marker
     */
    public void showAlertDialogue(final Marker marker){
        DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which){
                    case DialogInterface.BUTTON_POSITIVE:
                        //Yes button clicked
                        Log.d(TAG, "onClick: +++");
                        Intent returnIntent = new Intent();
                        returnIntent.putExtra("picked_loc_name", marker.getTitle());
                        returnIntent.putExtra("picked_point" , marker.getPosition());
                        setResult(Activity.RESULT_OK,returnIntent);
                        finish();
                        break;

                    case DialogInterface.BUTTON_NEGATIVE:
                        //No button clicked
                        break;
                }
            }
        };
        AlertDialog.Builder builder = new AlertDialog.Builder(MapActivity.this);
        builder.setMessage("Do you want to pick " + marker.getTitle() + " as location ?")
                .setPositiveButton("Confirm", dialogClickListener)
                .setNegativeButton("Cancel", dialogClickListener)
                .show();
    }

    @Override
    public boolean onMarkerClick(Marker marker) {
        Log.d(TAG, "onMarkerClick:  " + marker.getTitle()
                + marker.getPosition().latitude + marker.getPosition().longitude);
        showAlertDialogue(marker);
        return true;
    }


    /**
     * Method to create markers
     * @param latitude
     * @param longitude
     * @param title
     * @param downloadedBitmap
     * @return
     */
    private Marker createMarker(double latitude, double longitude, String title, Bitmap downloadedBitmap) {

        return mMap.addMarker(new MarkerOptions()
                .position(new LatLng(latitude, longitude))
                .anchor(0.5f, 0.5f)
                .title(title)
                .icon(BitmapDescriptorFactory.fromBitmap(downloadedBitmap))
        );
    }

     /*
     ------------------------------------ Firebase ---------------------------------------------
     */

    /**
     * Setup the firebase auth object
     */
    private void setupFirebaseAuth(){
        Log.d(TAG, "setupFirebaseAuth: setting up firebase auth.");
        mAuth = FirebaseAuth.getInstance();
        mFirebaseDatabase = FirebaseDatabase.getInstance();
        myRef = mFirebaseDatabase.getReference();

        mAuthListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser user = firebaseAuth.getCurrentUser();
                if (user != null) {
                    // User is signed in
                    Log.d(TAG, "onAuthStateChanged:signed_in:" + user.getUid());
                } else {
                    // User is signed out
                    Log.d(TAG, "onAuthStateChanged:signed_out");
                }
                // ...
            }
        };


        myRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {

            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }


    @Override
    public void onStart() {
        super.onStart();
        mAuth.addAuthStateListener(mAuthListener);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mAuthListener != null) {
            mAuth.removeAuthStateListener(mAuthListener);
        }
    }

    @Override
    public void onMarkerDragStart(Marker arg0) {
        Log.d(TAG, "onMarkerDragStart..."+arg0.getPosition().latitude+"..."+arg0.getPosition().longitude);
    }

    @Override
    public void onMarkerDragEnd(Marker arg0) {
        Log.d(TAG, "onMarkerDragEnd..."+arg0.getPosition().latitude+"..."+arg0.getPosition().longitude);

        String locationAddress = fetchLocationName(arg0.getPosition());
        arg0.setTitle(locationAddress);
        mSearchText.setText(locationAddress.trim());
        mMap.animateCamera(CameraUpdateFactory.newLatLng(arg0.getPosition()));
    }

    @Override
    public void onMarkerDrag(Marker arg0) {
        Log.i(TAG, "onMarkerDrag...");
    }


    private String fetchLocationName(LatLng currentLatLng){
        String strAddress = "";
        Geocoder gcd = new Geocoder(MapActivity.this, Locale.getDefault());
        List<Address> addresses = null;
        try {
            addresses = gcd.getFromLocation(currentLatLng.latitude, currentLatLng.longitude, 1);
        } catch (IOException e) {
            Log.d(TAG, "onComplete: IOException " + e.getMessage() );
        }
        if (addresses.size() > 0) {
            Log.d(TAG, "onComplete: gcd result ======>>>>>> " + addresses.get(0).getAddressLine(0) );
            strAddress = addresses.get(0).getAddressLine(0);
        }

        return strAddress;
    }

    /**
     * reduces the size of the image
     * @param image
     * @param maxSize
     * @return
     */
    public Bitmap getResizedBitmap(Bitmap image, int maxSize) {
        int width = image.getWidth();
        int height = image.getHeight();

        float bitmapRatio = (float)width / (float) height;
        if (bitmapRatio > 1) {
            width = maxSize;
            height = (int) (width / bitmapRatio);
        } else {
            height = maxSize;
            width = (int) (height * bitmapRatio);
        }
        return Bitmap.createScaledBitmap(image, width, height, true);
    }


    /**
     * custom marker using circleimageview and bla bla bla....
     * @param context
     * @param bitmapResource
     * @param _name
     * @return
     */
    public  Bitmap createCustomBitmapMarker(Context context, Bitmap bitmapResource, String _name) {

        View marker = ((LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE))
                .inflate(R.layout.custom_marker_layout, null);

        CircleImageView markerImage = (CircleImageView) marker.findViewById(R.id.user_dp);
        markerImage.setImageBitmap(bitmapResource);
        TextView txt_name = (TextView)marker.findViewById(R.id.name);
        txt_name.setText(_name);

        DisplayMetrics displayMetrics = new DisplayMetrics();
        ((Activity) context).getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        marker.setLayoutParams(new ViewGroup.LayoutParams(52, ViewGroup.LayoutParams.WRAP_CONTENT));
        marker.measure(displayMetrics.widthPixels, displayMetrics.heightPixels);
        marker.layout(0, 0, displayMetrics.widthPixels, displayMetrics.heightPixels);
        marker.buildDrawingCache();
        Bitmap bitmap = Bitmap.createBitmap(marker.getMeasuredWidth(), marker.getMeasuredHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        marker.draw(canvas);

        return bitmap;
    }

    /**
     *  Method to draw canvas
     * @param bitmap
     * @param latLng
     */
    private void drawCanvas(Bitmap bitmap, LatLng latLng){
        Bitmap.Config conf = Bitmap.Config.ARGB_8888;
        Bitmap bmp = Bitmap.createBitmap(80, 80, conf);
        Canvas canvas1 = new Canvas(bmp);

        // paint defines the text color, stroke width and size
        Paint color = new Paint();
        color.setTextSize(35);
        color.setColor(Color.BLACK);

        // modify canvas
        canvas1.drawBitmap(bitmap, 0,0, color);
        canvas1.drawText("User Name!", 30, 40, color);

        // add marker to Map
        mMap.addMarker(new MarkerOptions()
                .position(latLng)
                .icon(BitmapDescriptorFactory.fromBitmap(bmp))
                // Specifies the anchor to be at a particular point in the marker image.
                .anchor(0.5f, 1));
    }


}