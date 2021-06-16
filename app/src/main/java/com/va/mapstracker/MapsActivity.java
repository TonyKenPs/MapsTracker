package com.va.mapstracker;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.api.GoogleApi;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.LocationSource;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.model.PlaceLikelihood;
import com.google.android.libraries.places.api.net.FindCurrentPlaceRequest;
import com.google.android.libraries.places.api.net.FindCurrentPlaceResponse;
import com.google.android.libraries.places.api.net.PlacesClient;


import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.abs;
import static java.lang.Math.cos;
import static java.lang.Math.sin;
import static java.lang.Math.sqrt;

public class MapsActivity extends FragmentActivity implements LocationSource.OnLocationChangedListener,ActivityCompat.OnRequestPermissionsResultCallback, GoogleMap.OnMyLocationButtonClickListener, GoogleMap.OnMyLocationClickListener, OnMapReadyCallback {

    private GoogleMap mMap;//主地图
    private GoogleApi mGoogleApi;//谷歌API
    private static final int PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1;
    private boolean locationPermissionGranted;
    private FusedLocationProviderClient fusedLocationProviderClient;
    private Location lastKnownLocation;
    private PlacesClient placesClient;
    private Location location;
    private final LatLng defaultLocation = new LatLng(23.272899390544435, 113.20945318456941);
    private static final int DEFAULT_ZOOM = 15;
    private LocationManager locationManager;
    private LocationListener locationListener;
    private LocationServices locationServices;
    private LatLng lastPnt = null;
    private final List<LatLng> pointList = new ArrayList<>();
    private LatLng latLng;
    private ArrayList<LatLng> traceOfMe;
    private TextView txtOutput;
    private LatLng gps;

    private static void checkPermission(Context context, String permission){
        int perm = context.checkCallingOrSelfPermission(permission);
        String[] permissions = { permission };
        if(perm != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions((Activity)context, permissions,1);
        }
    }

    private void getLocationPermission() {
        /*
         * Request location permission, so that we can get the location of the
         * device. The result of the permission request is handled by a callback,
         * onRequestPermissionsResult.
         */
        if (ContextCompat.checkSelfPermission(this.getApplicationContext(),
                android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            locationPermissionGranted = true;
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        locationPermissionGranted = false;
        switch (requestCode) {
            case PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "权限获取成功", Toast.LENGTH_SHORT).show();
                    locationPermissionGranted = true;
                }
                else {
                    // 权限被用户拒绝了关闭界面。
                    System.exit(0);
                }
            }
        }
        updateLocationUI();
    }
    private void getDeviceLocation() {
        /*
         * Get the best and most recent location of the device, which may be null in rare
         * cases when a location is not available.
         */
        try {
            if (locationPermissionGranted) {
                Task locationResult = fusedLocationProviderClient.getLastLocation();
                locationResult.addOnCompleteListener(this, new OnCompleteListener() {
                    @Override
                    public void onComplete(@NonNull Task task) {
                        if (task.isSuccessful()) {
                            // Set the map's camera position to the current location of the device.
                            lastKnownLocation = (Location) task.getResult();
                            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                                    new LatLng(lastKnownLocation.getLatitude(),
                                            lastKnownLocation.getLongitude()), DEFAULT_ZOOM));
                        } else {
                            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, DEFAULT_ZOOM));
                            mMap.getUiSettings().setMyLocationButtonEnabled(false);
                        }
                    }
                });
            }
        } catch(SecurityException e)  {
            Log.e("Exception: %s", e.getMessage());
        }
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        checkPermission(this,"android.permission.ACCESS_FINE_LOCATION");
        checkPermission(this,"android.permission.ACCESS_COARSE_LOCATION");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        locationManager = (LocationManager)getSystemService(LOCATION_SERVICE);
        // Construct a FusedLocationProviderClient.
        Places.initialize(getApplicationContext(), getString(R.string.google_maps_key));
        placesClient = Places.createClient(this);
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
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
    public void onMapReady(GoogleMap map) {
        mMap = map;
        // TODO: Before enabling the My Location layer, you must request
        // location permission from the user. This sample does not include
        // a request for location permission.
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            map.setMyLocationEnabled(true);
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        mMap.setMyLocationEnabled(true);
        mMap.setOnMyLocationButtonClickListener(this);
        mMap.setOnMyLocationClickListener(this);
        mMap.setIndoorEnabled(true);
        mMap.setTrafficEnabled(true);
        UiSettings uiSettings = mMap.getUiSettings();
        uiSettings.setZoomControlsEnabled(true);
        uiSettings.setCompassEnabled(true);
        uiSettings.setAllGesturesEnabled(true);
        uiSettings.setMapToolbarEnabled(true);
        getLocationPermission();
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, DEFAULT_ZOOM));
        final String provider = LocationManager.GPS_PROVIDER;       //定义定位方法
        locationManager = (LocationManager)getSystemService(LOCATION_SERVICE); //获取系统服务
        //定义回调函数
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                latLng = new LatLng(location.getLatitude(),location.getLongitude());
                //判断是否为空坐标，若为空则不画线
                if(latLng!=null){
                    trackToMe(latLng);
                }
                showLocation(latLng);
            }
            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {
                //在提供定位功能的硬件的状态改变时调用
                latLng = new LatLng(location.getLatitude(),location.getLongitude());
                if(latLng!=null){
                    trackToMe(latLng);
                }
            }
            @Override
            public void onProviderEnabled(String provider) {
                //在用户启用具有定位功能的硬件时被调用
                latLng = new LatLng(location.getLatitude(),location.getLongitude());
                if(latLng!=null){
                    trackToMe(latLng);
                }
            }
            @Override
            public void onProviderDisabled(String provider) {
                //在用户禁用具有定位功能的硬件时调用
                latLng = new LatLng(location.getLatitude(),location.getLongitude());
                if(latLng!=null){
                    trackToMe(latLng);
                }
            }
        };
        //2秒，变更最小距离为10m时刷新位置信息
        locationManager.requestLocationUpdates(provider,2000,10,locationListener);

    }

    /**地图偏移量修正（测试用）*/
    public LatLng tranformlat(double x,double y){
        x = location.getLatitude();
        y = location.getLongitude();
        double a = 6378245.0;
        double ee = 0.00669342162296594323;
        double dx = x-35.0;//Lat
        double dy = y-105.0;//Lng
        //tranLat
        double tLat = -100.0 + 2.0 * dx + 3.0 * dy + 0.2 * dy * dy + 0.1 * dx * dy + 0.2 * sqrt(abs(dx));
        tLat = tLat + (20.0 * sin(6.0 * dx * Math.PI) + 20.0 * sin(2.0 * dx * Math.PI)) * 2.0 / 3.0;
        tLat = tLat + (20.0 * sin(dy * Math.PI) + 40.0 * sin(dy / 3.0 * Math.PI)) * 2.0 / 3.0;
        tLat = tLat + (160.0 * sin(dy / 12.0 * Math.PI) + 320 * sin(dy * Math.PI / 30.0)) * 2.0 / 3.0;
        //tranLng
        double tLng = 300.0 + dx + 2.0 * dy + 0.1 * dx * dx + 0.1 * dx * dy + 0.1 * sqrt(abs(dx));
        tLng = tLng + (20.0 * sin(6.0 * x * Math.PI) + 20.0 * sin(2.0 * x * Math.PI))* 2.0 / 3.0;
        tLng += (20.0 * sin(dx * Math.PI) + 40.0 * sin(dx / 3.0 * Math.PI)) * 2.0 / 3.0;
        tLng += (150.0 * sin(dx / 12.0 * Math.PI) + 300.0 * sin(dx / 30.0 * Math.PI)) * 2.0 / 3.0;

        double rLat = x / 180.0 * Math.PI;
        double magic = sin(rLat);
        magic = 1 - ee * magic * magic;
        double sqrtmagic = sqrt(magic);
        dx = (dx *180.0)/((a*(1-ee))/(magic*sqrtmagic)*Math.PI);
        dy = (dy *180.0)/(a/sqrtmagic*cos(rLat)*Math.PI);
        x = x + dx;
        y = y + dy;
        return tranformlat(x,y);
    }

    @Override
    public void  onLocationChanged(Location location){
        LatLng latLng = new LatLng(location.getLatitude(),location.getLongitude());
        trackToMe(latLng);
        showLocation(latLng);
    }

    public void showLocation(LatLng latLng){
        LatLng now = latLng;
        CameraPosition cameraPosition = CameraPosition.builder()
                .target(now)
                .zoom(30)
                .build();
        mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
    }

    @Override
    public void onMyLocationClick(@NonNull Location location) {
        Toast.makeText(this, "当前位置:\n" + location, Toast.LENGTH_LONG).show();
    }

    @Override
    public boolean onMyLocationButtonClick() {
        Toast.makeText(this, "正在移动到个人位置", Toast.LENGTH_SHORT).show();
        // Return false so that we don't consume the event and the default behavior still occurs
        // (the camera animates to the user's current position).
        return false;
    }
    private void updateLocationUI() {
        if (mMap == null) {
            return;
        }
        try {
            if (locationPermissionGranted) {
                mMap.setMyLocationEnabled(true);
                mMap.getUiSettings().setMyLocationButtonEnabled(true);
            } else {
                mMap.setMyLocationEnabled(false);
                mMap.getUiSettings().setMyLocationButtonEnabled(false);
                lastKnownLocation = null;
                getLocationPermission();
            }
        } catch (SecurityException e)  {
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 移除监听器
        locationManager.removeUpdates(locationListener);
    }
    private void trackToMe(LatLng latLng) {
        lastPnt = latLng;
        if (lastPnt != null) {
            if (traceOfMe == null) {
                traceOfMe = new ArrayList<LatLng>();
                traceOfMe.add(lastPnt);
            }
            traceOfMe.add(lastPnt);
            PolylineOptions polylineOpt = new PolylineOptions();
            for (LatLng lastPnt : traceOfMe) {
                polylineOpt.add(lastPnt);
            }
            polylineOpt.color(Color.RED);

            Polyline line = mMap.addPolyline(polylineOpt);
            line.setWidth(10);
        }
    }


    public static double pi = 3.1415926535897932384626;
    public static double a = 6378245.0;
    public static double ee = 0.00669342162296594323;

    /**
     * 84 to 火星坐标系 (GCJ-02) World Geodetic System ==> Mars Geodetic System
     * @param lat
     * @param lon
     */
    public static LatLng gps84_To_Gcj02(double lat, double lon) {
        if (outOfChina(lat, lon)) {
            return null;
        }
        double dLat = transformLat(lon - 105.0, lat - 35.0);
        double dLon = transformLon(lon - 105.0, lat - 35.0);
        double radLat = lat / 180.0 * pi;
        double magic = Math.sin(radLat);
        magic = 1 - ee * magic * magic;
        double sqrtMagic = Math.sqrt(magic);
        dLat = (dLat * 180.0) / ((a * (1 - ee)) / (magic * sqrtMagic) * pi);
        dLon = (dLon * 180.0) / (a / sqrtMagic * Math.cos(radLat) * pi);
        double mgLat = lat + dLat;
        double mgLon = lon + dLon;
        return new LatLng(mgLat, mgLon);
    }

    /**
     * is or not outOfChina
     * @param lat
     * @param lon
     * @return
     */
    public static boolean outOfChina(double lat, double lon) {
        if (lon < 72.004 || lon > 137.8347)
            return true;
        if (lat < 0.8293 || lat > 55.8271)
            return true;
        return false;
    }

    public static GPS transform(double lat, double lon) {
        if (outOfChina(lat, lon)) {
            return new GPS(lat, lon);
        }
        double dLat = transformLat(lon - 105.0, lat - 35.0);
        double dLon = transformLon(lon - 105.0, lat - 35.0);
        double radLat = lat / 180.0 * pi;
        double magic = Math.sin(radLat);
        magic = 1 - ee * magic * magic;
        double sqrtMagic = Math.sqrt(magic);
        dLat = (dLat * 180.0) / ((a * (1 - ee)) / (magic * sqrtMagic) * pi);
        dLon = (dLon * 180.0) / (a / sqrtMagic * Math.cos(radLat) * pi);
        double mgLat = lat + dLat;
        double mgLon = lon + dLon;
        return new GPS(mgLat, mgLon);
    }

    public static double transformLat(double x, double y) {
        double ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y
                + 0.2 * Math.sqrt(Math.abs(x));
        ret += (20.0 * Math.sin(6.0 * x * pi) + 20.0 * Math.sin(2.0 * x * pi)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(y * pi) + 40.0 * Math.sin(y / 3.0 * pi)) * 2.0 / 3.0;
        ret += (160.0 * Math.sin(y / 12.0 * pi) + 320 * Math.sin(y * pi / 30.0)) * 2.0 / 3.0;
        return ret;
    }


    public static double transformLon(double x, double y) {
        double ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1
                * Math.sqrt(Math.abs(x));
        ret += (20.0 * Math.sin(6.0 * x * pi) + 20.0 * Math.sin(2.0 * x * pi)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(x * pi) + 40.0 * Math.sin(x / 3.0 * pi)) * 2.0 / 3.0;
        ret += (150.0 * Math.sin(x / 12.0 * pi) + 300.0 * Math.sin(x / 30.0
                * pi)) * 2.0 / 3.0;
        return ret;
    }
}