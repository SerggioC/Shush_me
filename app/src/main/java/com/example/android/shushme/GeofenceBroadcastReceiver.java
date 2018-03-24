package com.example.android.shushme;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;

import timber.log.Timber;

/**
 * Created by Sergio on 24/03/2018.
 */

public class GeofenceBroadcastReceiver extends BroadcastReceiver {

    private static final String CHANNEL_ID = "normal";

    /**
     * This method is called when the BroadcastReceiver is receiving an Intent
     * broadcast.  During this time you can use the other methods on
     * BroadcastReceiver to view/modify the current result values.  This method
     * is always called within the main thread of its process, unless you
     * explicitly asked for it to be scheduled on a different thread using
     * {@link Context.registerReceiver(BroadcastReceiver, * IntentFilter , String, Handler)}. When it runs on the main
     * thread you should
     * never perform long-running operations in it (there is a timeout of
     * 10 seconds that the system allows before considering the receiver to
     * be blocked and a candidate to be killed). You cannot launch a popup dialog
     * in your implementation of onReceive().
     * <p>
     * <p><b>If this BroadcastReceiver was launched through a &lt;receiver&gt; tag,
     * then the object is no longer alive after returning from this
     * function.</b> This means you should not perform any operations that
     * return a result to you asynchronously. If you need to perform any follow up
     * background work, schedule a {@link JobService} with
     * {@link JobScheduler}.
     * <p>
     * If you wish to interact with a service that is already running and previously
     * bound using {@link Context#bindService(Intent, ServiceConnection, int) bindService()},
     * you can use {@link #peekService}.
     * <p>
     * <p>The Intent filters used in {@link Context#registerReceiver}
     * and in application manifests are <em>not</em> guaranteed to be exclusive. They
     * are hints to the operating system about how to find suitable recipients. It is
     * possible for senders to force delivery to specific recipients, bypassing filter
     * resolution.  For this reason, {@link #onReceive(Context, Intent) onReceive()}
     * implementations should respond only to known actions, ignoring any unexpected
     * Intents that they may receive.
     *
     * @param context The Context in which the receiver is running.
     * @param intent  The Intent being received.
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        GeofencingEvent geofencingEvent = GeofencingEvent.fromIntent(intent);

        Timber.d("Sergio>", this + " onReceive received geofence broadcast " +
                "geofence transition geofencingEvent= " + geofencingEvent.getGeofenceTransition());


        int geoFenceTransition = geofencingEvent.getGeofenceTransition();

        switch (geoFenceTransition) {
            case Geofence.GEOFENCE_TRANSITION_ENTER: {
                setPhoneRingerMode(context, AudioManager.RINGER_MODE_SILENT, geoFenceTransition);
                break;
            }
            case Geofence.GEOFENCE_TRANSITION_EXIT: {
                setPhoneRingerMode(context, AudioManager.RINGER_MODE_NORMAL, geoFenceTransition);
                break;
            }
            default: {
                Timber.e("Wrong geofence transition received!");
                return;
            }
        }

    }

    private void setPhoneRingerMode(Context context, int ringMode, int geoFenceTransition) {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && notificationManager.isNotificationPolicyAccessGranted())) {
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            audioManager.setMode(ringMode);
            createNotification(context, geoFenceTransition);
        }
    }


    private void createNotification(Context context, int transitionType) {

        // Create an explicit content Intent that starts the main Activity.
        Intent notificationIntent = new Intent(context, MainActivity.class);

        // Construct a task stack.
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);

        // Add the main Activity to the task stack as the parent.
        stackBuilder.addParentStack(MainActivity.class);

        // Push the content Intent onto the stack.
        stackBuilder.addNextIntent(notificationIntent);

        // Get a PendingIntent containing the entire back stack.
        PendingIntent notificationPendingIntent =
                stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);

        // Get a notification builder
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID);

        // Check the transition type to display the relevant icon image
        if (transitionType == Geofence.GEOFENCE_TRANSITION_ENTER) {
            builder.setSmallIcon(R.drawable.ic_volume_off_white_24dp)
                    .setLargeIcon(BitmapFactory.decodeResource(context.getResources(),
                            R.drawable.ic_volume_off_white_24dp))
                    .setContentTitle(context.getString(R.string.silent_mode_activated));
        } else if (transitionType == Geofence.GEOFENCE_TRANSITION_EXIT) {
            builder.setSmallIcon(R.drawable.ic_volume_up_white_24dp)
                    .setLargeIcon(BitmapFactory.decodeResource(context.getResources(),
                            R.drawable.ic_volume_up_white_24dp))
                    .setContentTitle(context.getString(R.string.back_to_normal));
        }

        // Continue building the notification
        builder.setContentText(context.getString(R.string.touch_to_relaunch));
        builder.setContentIntent(notificationPendingIntent);

        // Dismiss notification once the user touches it.
        builder.setAutoCancel(true);

        // Get an instance of the Notification manager
        NotificationManager mNotificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        // Issue the notification
        mNotificationManager.notify(0, builder.build());

    }

}
