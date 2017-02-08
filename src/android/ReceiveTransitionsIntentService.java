package com.cowbell.cordova.geofence;

import android.app.IntentService;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;

import java.util.ArrayList;
import java.util.List;

import java.util.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.sql.Timestamp;
import java.util.Calendar;

public class ReceiveTransitionsIntentService extends IntentService {
    protected static final String GeofenceTransitionIntent = "com.cowbell.cordova.geofence.TRANSITION";
    protected BeepHelper beepHelper;
    protected GeoNotificationNotifier notifier;
    protected GeoNotificationStore store;

    /**
     * Sets an identifier for the service
     */
    public ReceiveTransitionsIntentService() {
        super("ReceiveTransitionsIntentService");
        beepHelper = new BeepHelper();
        store = new GeoNotificationStore(this);
        Logger.setLogger(new Logger(GeofencePlugin.TAG, this, false));
    }

    /**
     * Add days to date object
     */
    public static Date addDays(Date date, int days)
    {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.add(Calendar.DATE, days); // Minus number would decrement the days
        return cal.getTime();
    }

    /**
     * Handles incoming intents
     *
     * @param intent
     *            The Intent sent by Location Services. This Intent is provided
     *            to Location Services (inside a PendingIntent) when you call
     *            addGeofences()
     */
    @Override
    protected void onHandleIntent(Intent intent) {
        Logger logger = Logger.getLogger();
        logger.log(Log.DEBUG, "ReceiveTransitionsIntentService - onHandleIntent");
        Intent broadcastIntent = new Intent(GeofenceTransitionIntent);
        notifier = new GeoNotificationNotifier(
                (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE),
                this
        );

        // First check for errors
        GeofencingEvent geofencingEvent = GeofencingEvent.fromIntent(intent);
        if (geofencingEvent.hasError()) {
            // Get the error code with a static method
            int errorCode = geofencingEvent.getErrorCode();
            String error = "Location Services error: " + Integer.toString(errorCode);
            // Log the error
            logger.log(Log.ERROR, error);
            broadcastIntent.putExtra("error", error);
        } else {
            // Get the type of transition (entry or exit)
            int transitionType = geofencingEvent.getGeofenceTransition();
            if ((transitionType == Geofence.GEOFENCE_TRANSITION_ENTER)
                    || (transitionType == Geofence.GEOFENCE_TRANSITION_EXIT)) {
                logger.log(Log.DEBUG, "Geofence transition detected");
                List<Geofence> triggerList = geofencingEvent.getTriggeringGeofences();
                List<GeoNotification> geoNotifications = new ArrayList<GeoNotification>();
                for (Geofence fence : triggerList) {
                    String fenceId = fence.getRequestId();
                    GeoNotification geoNotification = store
                            .getGeoNotification(fenceId);

                    if (geoNotification != null) {
                        if (geoNotification.notification != null) {
                            // Check if now its between event time
                            if(geoNotification.notification.byDate) {
                                if(geoNotification.notification.since != "" && geoNotification.notification.until != "" && geoNotification.notification.time != "") {
                                    DateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                                    Timestamp timestamp = new Timestamp(System.currentTimeMillis());
                                    Date today = null;
                                    Date geofenceSinceDate = null;
                                    Date geofenceUntilDate = null;
                                    Date futureDate = null;
                                    try {
                                        today = sdf.parse(sdf.format(timestamp));
                                        futureDate = this.addDays(today, geoNotification.notification.preTimeRange);
                                        if(geoNotification.notification.time != "")
                                            geofenceSinceDate = sdf.parse(geoNotification.notification.since + " " + geoNotification.notification.time);
                                        else
                                            geofenceSinceDate = sdf.parse(geoNotification.notification.since + " 00:00");
                                        geofenceUntilDate = sdf.parse(geoNotification.notification.until + " 23:59");
                                    }
                                    catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                    if(today.before(geofenceSinceDate) && geofenceSinceDate.before(futureDate) && geofenceUntilDate.after(today)) {
                                        notifier.notify(geoNotification.notification);
                                    }
                                }
                            } else {
                                notifier.notify(geoNotification.notification);
                            }
                        }
                        geoNotification.transitionType = transitionType;
                        geoNotifications.add(geoNotification);
                    }
                }

                if (geoNotifications.size() > 0) {
                    broadcastIntent.putExtra("transitionData", Gson.get().toJson(geoNotifications));
                    GeofencePlugin.onTransitionReceived(geoNotifications);
                }
            } else {
                String error = "Geofence transition error: " + transitionType;
                logger.log(Log.ERROR, error);
                broadcastIntent.putExtra("error", error);
            }
        }
        sendBroadcast(broadcastIntent);
    }
}
