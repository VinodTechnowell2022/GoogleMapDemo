package com.tw.googlemapdemo

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.content.IntentSender
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.LocationSettingsStatusCodes
import com.google.android.gms.location.Priority
import com.google.android.gms.location.SettingsClient
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.DexterError
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import java.text.DateFormat
import java.util.Date

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private var map: GoogleMap? = null

    private val TAG:String = "MainActivity"
    private var longitude:String? = ""
    private  var latitude:String? = ""

    // location last updated time
    private var mLastUpdateTime: String? = null
    // boolean flag to toggle the ui
    var mRequestingLocationUpdates: Boolean=false

    // location updates interval - 100sec
    private val UPDATE_INTERVAL_IN_MILLISECONDS: Long = 100000

    // fastest updates interval - 100 sec
    // location updates will be received if another app is requesting the locations
    // than your app can handle
    private val FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS: Long = 100000

    private val REQUEST_CHECK_SETTINGS = 100

    private var mFusedLocationClient: FusedLocationProviderClient? = null
    private lateinit var mSettingsClient: SettingsClient
    private lateinit var mLocationRequest: LocationRequest
    private lateinit var mLocationSettingsRequest: LocationSettingsRequest
    private lateinit var mLocationCallback: LocationCallback
    private var mCurrentLocation: Location? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map_fragment) as? SupportMapFragment
        mapFragment?.getMapAsync(this)

        init()
        startLocationButtonClick()

        findViewById<FloatingActionButton>(R.id.fabLayer).setOnClickListener {
            changeMapLayerView()
        }
    }


    private fun changeMapLayerView() {

        val options = arrayOf<CharSequence>( resources.getString(R.string._hybrid), resources.getString(R.string._terrain), resources.getString(R.string._satellite))
        val builder: AlertDialog.Builder = AlertDialog.Builder(this@MainActivity)
        builder.setTitle(resources.getString(R.string.change_map_layer))
        builder.setCancelable(true)
        builder.setItems(options) { dialog, item ->
            if (options[item] == resources.getString(R.string._hybrid)) {
                dialog.dismiss()
                if (mCurrentLocation!=null)
                    map?.mapType = GoogleMap.MAP_TYPE_HYBRID
            } else if (options[item] == resources.getString(R.string._terrain)) {
                dialog.dismiss()
                if (mCurrentLocation!=null)
                    map?.mapType = GoogleMap.MAP_TYPE_TERRAIN
            } else if (options[item] == resources.getString(R.string._satellite)) {
                dialog.dismiss()
                if (mCurrentLocation!=null)
                    map?.mapType = GoogleMap.MAP_TYPE_SATELLITE
            }
        }
        builder.show()
    }

    private fun init() {
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this@MainActivity)
        mSettingsClient = LocationServices.getSettingsClient(this@MainActivity)
        mLocationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                // location is received
                locationResult.lastLocation
                mCurrentLocation = locationResult.lastLocation
                mLastUpdateTime = DateFormat.getTimeInstance().format(Date())
                updateLocationUI()
            }
        }
        mRequestingLocationUpdates = false
        mLocationRequest =   LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_IN_MILLISECONDS).apply {
            setMinUpdateDistanceMeters(1F)
            setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
            setWaitForAccurateLocation(true)
            setMaxUpdates(Integer.MAX_VALUE)
        }.build()

        val builder = LocationSettingsRequest.Builder()
        builder.addLocationRequest(mLocationRequest)
        mLocationSettingsRequest = builder.build()
    }

    private fun startLocationButtonClick() {
        // Requesting ACCESS_FINE_LOCATION using Dexter library
        Dexter.withContext(this)
            .withPermissions( Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION )
            .withListener(object : MultiplePermissionsListener {
                override fun onPermissionsChecked(multiplePermissionsReport: MultiplePermissionsReport) {
                    if (multiplePermissionsReport.areAllPermissionsGranted()) {
                        Log.e(TAG, "permissions Granted")

                        mRequestingLocationUpdates = true
                        startLocationUpdates()
                    }else if (mRequestingLocationUpdates==false){
                        openLocationSettingScreen()
                    }else{
                        if (multiplePermissionsReport.isAnyPermissionPermanentlyDenied)  {
                            Log.e(TAG, "permissions Denied")
                            showSettingsDialogAll()
                        }
                    }
                }

                override fun onPermissionRationaleShouldBeShown(list: List<PermissionRequest>, permissionToken: PermissionToken) {
                    permissionToken.continuePermissionRequest()
                }
            }).withErrorListener { dexterError: DexterError ->
                Log.e(TAG, "permissions" + "dexterError :" + dexterError.name)
            }
            .onSameThread()
            .check()
    }

    fun openLocationSettingScreen() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(resources.getString(R.string.location_seems_off) )
        builder.setMessage(resources.getString(R.string.location_mandatory))
        builder.setPositiveButton(resources.getString(R.string.go_to_locations)) { dialog, _ ->
            dialog.cancel()
            openLocationSettings()
        }
        builder.show()
    }

    private fun openLocationSettings() {
        val intent = Intent()
        intent.action = android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }

    fun showSettingsDialogAll() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(resources.getString(R.string.needs_permissions))
        builder.setMessage(resources.getString(R.string.location_mandatory))
        builder.setPositiveButton(resources.getString(R.string.goto_settings)) { dialog, _ ->
            dialog.cancel()
            openSettings()
        }
        builder.show()
    }

    private fun openSettings() {
        val intent = Intent()
        intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        val uri = Uri.fromParts("package", "com.tw.googlemapdemo", null)
        intent.data = uri
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        mSettingsClient.checkLocationSettings(mLocationSettingsRequest)
            .addOnSuccessListener(this) {
                Log.e(TAG, "All location settings are satisfied.")
                //   Toast.makeText(getApplicationContext(), "Started location updates!", Toast.LENGTH_SHORT).show();
                mFusedLocationClient!!.requestLocationUpdates(mLocationRequest,
                    mLocationCallback, Looper.myLooper()!!)
                updateLocationUI()
            }
            .addOnFailureListener(this) { e ->
                when ((e as ApiException).statusCode) {
                    LocationSettingsStatusCodes.RESOLUTION_REQUIRED -> {
                        Log.e(TAG, "Location settings are not satisfied. Attempting to upgrade " + "location settings ")
                        try {
                            // Show the dialog by calling startResolutionForResult(), and check the
                            // result in onActivityResult().
                            val rae = e as ResolvableApiException
                            rae.startResolutionForResult(this@MainActivity, REQUEST_CHECK_SETTINGS)
                        } catch (sie: IntentSender.SendIntentException) {
                            Log.e(TAG, "PendingIntent unable to execute request.")
                        }
                    }
                    LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE -> {
                        val errorMessage = "Location settings are inadequate, and cannot be " + "fixed here. Fix in Settings."
                        Log.e(TAG, errorMessage)
                    }
                }
            }
    }

    private fun stopLocationUpdates() {
        mRequestingLocationUpdates = false
        // Removing location updates
        mFusedLocationClient
            ?.removeLocationUpdates(mLocationCallback)
            ?.addOnCompleteListener(this) { _ -> }
    }


    private fun updateLocationUI() {
        if (mCurrentLocation != null) {
            latitude = mCurrentLocation!!.latitude.toString()
            longitude = mCurrentLocation!!.longitude.toString()
            Log.e(TAG, "latitude -- "+ latitude!!)
            Log.e(TAG, "longitude --"+ longitude!!)

            val latLng = LatLng(latitude!!.toDouble(), longitude!!.toDouble())

            map!!.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .icon(BitmapDescriptorFactory.fromResource( R.drawable.red_home))
                    .title("My Location")
            )

            //move camera focus to specific location on google map
            this.map!!.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))

        } else {
            Log.e("latitude->", latitude + "")
            Log.e("longitude->", longitude + "")
        }
    }

    override fun onPause() {
        super.onPause()
        if (mRequestingLocationUpdates) {
            // pausing location updates
            stopLocationUpdates()
        }
    }

    override fun onMapReady(map: GoogleMap) {
        this.map = map
        map.mapType = GoogleMap.MAP_TYPE_NORMAL

        map.isBuildingsEnabled =true
        map.isIndoorEnabled =true
        map.uiSettings.isMyLocationButtonEnabled=true
        map.uiSettings.isZoomGesturesEnabled=true
        map.uiSettings.isZoomControlsEnabled=true
        map.uiSettings.isScrollGesturesEnabledDuringRotateOrZoom=true
        map.uiSettings.isCompassEnabled = true
        map.isTrafficEnabled =true
        map.uiSettings.isIndoorLevelPickerEnabled=true
        map.uiSettings.isMapToolbarEnabled=false
        map.setMaxZoomPreference(18.0f)
    }

}