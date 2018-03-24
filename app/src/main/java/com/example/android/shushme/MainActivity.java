package com.example.android.shushme;

/*
* Copyright (C) 2017 The Android Open Source Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*  	http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

import android.Manifest;
import android.app.NotificationManager;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.Toast;

import com.example.android.shushme.provider.PlaceContract;
import com.example.android.shushme.util.TimberImplementation;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.PlaceBuffer;
import com.google.android.gms.location.places.Places;
import com.google.android.gms.location.places.ui.PlacePicker;

import java.util.ArrayList;
import java.util.List;

import timber.log.Timber;

public class MainActivity extends AppCompatActivity implements
        ConnectionCallbacks,
        OnConnectionFailedListener {

    // Constants
    private static final int FINE_LOCATION_REQUEST_CODE = 111;
    private static final int PLACE_PICKER_REQUEST = 1;

    // Member variables
    private PlaceListAdapter mAdapter;
    private RecyclerView mRecyclerView;
    private View mainLayout;
    private GoogleApiClient googleApiClient;
    private boolean fencesEnabled;
    private Geofencing geofencing;
    private CheckBox ringerPermission;

    /**
     * Called when the activity is starting
     *
     * @param savedInstanceState The Bundle that contains the data supplied in onSaveInstanceState
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TimberImplementation.init();

        // Set up the recycler view
        mRecyclerView = findViewById(R.id.places_list_recycler_view);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mAdapter = new PlaceListAdapter(this, null);
        mRecyclerView.setAdapter(mAdapter);

        mainLayout = findViewById(R.id.main_layout);

        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        fencesEnabled = preferences.getBoolean(getString(R.string.pref_enable_fences_key), false);

        final Switch geofenceSwitch = findViewById(R.id.enable_switch);
        geofenceSwitch.setChecked(fencesEnabled);
        geofenceSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                preferences.edit().putBoolean(getString(R.string.pref_enable_fences_key), isChecked).apply();
                if (isChecked) {
                    geofencing.registerAllGeofences();
                } else {
                    geofencing.unregisterAllGeofences();
                }
            }
        });

        ringerPermission = findViewById(R.id.ringer_permission_checkbox);
        ringerPermission.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
                startActivity(intent);
            }
        });


        // Build up the LocationServices API client
        // Uses the addApi method to request the LocationServices API
        // Also uses enableAutoManage to automatically when to connect/suspend the client
        googleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .addApi(Places.GEO_DATA_API)
                .enableAutoManage(this, this)
                .build();

        geofencing = new Geofencing(this, googleApiClient);


    }

    /***
     * Called when the Google API Client is successfully connected
     *
     * @param connectionHint Bundle of data provided to clients by Google Play services
     */
    @Override
    public void onConnected(@Nullable Bundle connectionHint) {
        Timber.i("API Client Connection Successful!");

        refreshPlacesData();
    }

    private void refreshPlacesData() {
        Cursor cursor = getContentResolver().query(PlaceContract.PlaceEntry.CONTENT_URI, null, null, null, null);
        if (cursor == null || cursor.getCount() == 0) return;

        List<String> placesIdList = new ArrayList<>(cursor.getCount());
        while (cursor.moveToNext()) {
            placesIdList.add(cursor.getString(cursor.getColumnIndex(PlaceContract.PlaceEntry.COLUMN_PLACE_ID)));
        }

        PendingResult<PlaceBuffer> placesResult = Places.GeoDataApi.getPlaceById(googleApiClient, placesIdList.toArray(new String[placesIdList.size()]));
        placesResult.setResultCallback(new ResultCallback<PlaceBuffer>() {
            @Override
            public void onResult(@NonNull PlaceBuffer places) {
                mAdapter.swapData(places);
                geofencing.updateGeofenceList(places);
                if (fencesEnabled) geofencing.registerAllGeofences();
            }
        });

    }

    /***
     * Called when the Google API Client is suspended
     *
     * @param cause cause The reason for the disconnection. Defined by constants CAUSE_*.
     */
    @Override
    public void onConnectionSuspended(int cause) {
        Timber.i("API Client Connection Suspended!");
    }

    /***
     * Called when the Google API Client failed to connect to Google Play Services
     *
     * @param result A ConnectionResult that can be used for resolving the error
     */
    @Override
    public void onConnectionFailed(@NonNull ConnectionResult result) {
        Timber.e("API Client Connection Failed!");
    }


    /***
     * Called when the Place Picker Activity returns back with a selected place (or after canceling)
     *
     * @param requestCode The request code passed when calling startActivityForResult
     * @param resultCode  The result code specified by the second activity
     * @param data        The Intent that carries the result data.
     */
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PLACE_PICKER_REQUEST && resultCode == RESULT_OK) {
            Place place = PlacePicker.getPlace(this, data);
            if (place == null) {
                Timber.i("No place selected");
                return;
            }

            // Extract the place information from the API
            String placeName = place.getName().toString();
            String placeAddress = place.getAddress().toString();
            String placeID = place.getId();

            // Insert a new place into DB
            ContentValues contentValues = new ContentValues();
            contentValues.put(PlaceContract.PlaceEntry.COLUMN_PLACE_ID, placeID);
            getContentResolver().insert(PlaceContract.PlaceEntry.CONTENT_URI, contentValues);

            refreshPlacesData();
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // Initialize location permissions checkbox
        CheckBox locationPermissions = findViewById(R.id.location_permission_checkbox);
        if (ActivityCompat.checkSelfPermission(MainActivity.this,
                android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            locationPermissions.setChecked(false);
        } else {
            locationPermissions.setChecked(true);
            locationPermissions.setEnabled(false);
        }

        locationPermissions.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestLocationPermission();
            }
        });

        Button addNewLocation = findViewById(R.id.add_new_location);
        addNewLocation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onAddPlaceButtonClicked();
            }
        });

        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !notificationManager.isNotificationPolicyAccessGranted()) {
            ringerPermission.setChecked(false);
        } else {
            ringerPermission.setChecked(true);
            ringerPermission.setEnabled(false);
        }
    }

    public void onAddPlaceButtonClicked() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestLocationPermission();
            return;
        }
        try {
            // Start a new Activity for the Place Picker API, this will trigger {@code #onActivityResult}
            // when a place is selected or with the user cancels.
            PlacePicker.IntentBuilder builder = new PlacePicker.IntentBuilder();
            Intent intent = builder.build(this);
            startActivityForResult(intent, PLACE_PICKER_REQUEST);
        } catch (GooglePlayServicesRepairableException e) {
            Timber.w(String.format("GooglePlayServices Not Available [%s]", e.getMessage()));
            getPlayServices();

        } catch (GooglePlayServicesNotAvailableException e) {
            Timber.e(String.format("GooglePlayServices Not Available [%s]", e.getMessage()));
            Snackbar.make(mainLayout, R.string.play_services_unavailable,
                    Snackbar.LENGTH_INDEFINITE)
                    .show();
        } catch (Exception e) {
            Timber.e(String.format("PlacePicker Exception: %s", e.getMessage()));
        }
    }

    private void requestLocationPermission() {

        // Permission has not been granted and must be requested.
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
            // Provide an additional rationale to the user if the permission was not granted
            // and the user would benefit from additional context for the use of the permission.
            // Display a SnackBar with a button to request the missing permission.

            Snackbar.make(mainLayout, R.string.access_required,
                    Snackbar.LENGTH_INDEFINITE).setAction(android.R.string.ok, new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    // Request the permission
                    ActivityCompat.requestPermissions(MainActivity.this,
                            new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                            FINE_LOCATION_REQUEST_CODE);
                }
            }).show();

        } else {
            Snackbar.make(mainLayout, R.string.access_unavailable, Snackbar.LENGTH_LONG).show();
            // Request the permission. The result will be received in onRequestPermissionResult().
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, FINE_LOCATION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == FINE_LOCATION_REQUEST_CODE) {
            if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Snackbar.make(mainLayout, R.string.permission_granted,
                        Snackbar.LENGTH_LONG)
                        .show();
                //startMethodRequiringPermission();
            } else {
                Snackbar.make(mainLayout, R.string.permission_denied,
                        Snackbar.LENGTH_LONG)
                        .show();
            }
        }
    }

    /**
     * Check the device to make sure it has the Google Play Services APK. If
     * it doesn't, display a dialog that allows users to download the APK from
     * the Google Play Store or enable it in the device's system settings.
     */
    private void getPlayServices() {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = apiAvailability.isGooglePlayServicesAvailable(this);
        if (resultCode != ConnectionResult.SUCCESS) {
            if (apiAvailability.isUserResolvableError(resultCode)) {

                apiAvailability.showErrorDialogFragment(MainActivity.this, resultCode, resultCode, new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        Toast.makeText(MainActivity.this, R.string.install_gms, Toast.LENGTH_SHORT).show();
                    }
                });

            } else {
                // Play Services not available
                Timber.e("This device is not supported because it does not have Google Play Services installed.");
                Snackbar.make(mainLayout, R.string.play_services_unavailable,
                        Snackbar.LENGTH_INDEFINITE)
                        .show();
            }
        }
    }


}
