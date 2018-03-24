package com.example.android.shushme;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.PlaceBuffer;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Created by Sergio on 24/03/2018.
 * Geofencing setup
 */

public class Geofencing {
    private static final int FENCE_RADIUS = 10; //meters
    private Context context;
    private GoogleApiClient googleApiClient;
    private PendingIntent geofencePendingIntent;
    private List<Geofence> geofenceList;

    public Geofencing(Context context, GoogleApiClient googleApiClient) {
        this.context = context;
        this.googleApiClient = googleApiClient;
        this.geofencePendingIntent = null;
        this.geofenceList = new ArrayList<>();
    }


    public void registerAllGeofences() {
        if (googleApiClient == null || !googleApiClient.isConnected() ||
                geofenceList == null || geofenceList.size() == 0) {
            return;
        }

        /// already called checkSelfPermission...
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }

        LocationServices
                .getGeofencingClient(context)
                .addGeofences(getGeofencingRequest(), getGeofencePendingIntent())
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Log.d("Sergio>", this + " onSuccess ");
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.e("Sergio>", " onFailure:\nexception e= " + e);
                    }
                })
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        Log.i("Sergio>", this + " onComplete\ntask= " + task);
                    }
                });

    }

    public void unregisterAllGeofences() {
        if (googleApiClient == null || !googleApiClient.isConnected()) {
            return;
        }

        LocationServices
                .getGeofencingClient(context)
                .removeGeofences(getGeofencePendingIntent())
        .addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                Log.d("Sergio>", this + " onSuccess removed all geofences");
            }
        })
        .addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Log.e("Sergio>", " onFailure: removing geofences error= " + e);
            }
        })
        .addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                Log.i("Sergio>", this + " onComplete removing geofences");
            }
        });
    }


    public void updateGeofenceList(PlaceBuffer placeBuffer) {
        if (placeBuffer == null || placeBuffer.getCount() == 0) return;

        geofenceList = new ArrayList<>(placeBuffer.getCount());
        for (Place place : placeBuffer) {
            String placeID = place.getId();
            double latitude = place.getLatLng().latitude;
            double longitude = place.getLatLng().longitude;

            Geofence geofence = new Geofence.Builder()
                    .setRequestId(placeID)
                    .setCircularRegion(latitude, longitude, FENCE_RADIUS)
                    .setExpirationDuration(TimeUnit.DAYS.toMillis(30))
                    .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER | Geofence.GEOFENCE_TRANSITION_EXIT)
                    .build();

            geofenceList.add(geofence);
        }

    }

    private GeofencingRequest getGeofencingRequest() {
        GeofencingRequest.Builder builder = new GeofencingRequest.Builder()
                .addGeofences(geofenceList)
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER);
        return builder.build();
    }

    private PendingIntent getGeofencePendingIntent() {
        if (geofencePendingIntent != null) {
            return geofencePendingIntent;
        }
        Intent intent = new Intent(context, GeofenceBroadcastReceiver.class);
        geofencePendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        return geofencePendingIntent;
    }

}



















