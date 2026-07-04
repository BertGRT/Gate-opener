package com.portail.gate;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.IBinder;
import android.os.Looper;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

public class LocationService extends Service {

    private FusedLocationProviderClient client;
    private LocationCallback callback;
    private boolean wasInsideOpen = false;
    private boolean wasInsideClose = false;
    private boolean firstFix = true;

    @Override
    public void onCreate() {
        super.onCreate();
        startForegroundNotif();
        client = LocationServices.getFusedLocationProviderClient(this);
        Journal.add(this, "Service de surveillance demarre");
    }

    private void startForegroundNotif() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel c = new NotificationChannel("portail_svc", "Portail surveillance", NotificationManager.IMPORTANCE_LOW);
        nm.createNotificationChannel(c);
        Notification n = new Notification.Builder(this, "portail_svc")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle("Portail")
                .setContentText("Surveillance arrivee/depart active")
                .setOngoing(true)
                .build();
        startForeground(42, n);
    }

    // (Re)demarre la demande de localisation avec l'intervalle courant
    private void startUpdates() {
        if (client == null) return;
        if (callback != null) {
            client.removeLocationUpdates(callback);
        }

        int intervalSec;
        try {
            intervalSec = Integer.parseInt(getSharedPreferences("cfg", MODE_PRIVATE).getString("interval", "15"));
        } catch (Exception e) {
            intervalSec = 15;
        }
        if (intervalSec < 5) intervalSec = 5;
        long ms = intervalSec * 1000L;

        LocationRequest req = new LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, ms)
                .setMinUpdateIntervalMillis(ms)
                .build();

        callback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult result) {
                Location loc = result.getLastLocation();
                if (loc != null) handleLocation(loc);
            }
        };
        firstFix = true;

        try {
            client.requestLocationUpdates(req, callback, Looper.getMainLooper());
            Journal.add(this, "Surveillance active, intervalle " + intervalSec + " s");
        } catch (SecurityException e) {
            Notif.show(this, "Portail", "Service: permission localisation manquante");
        }
    }

    private void handleLocation(Location loc) {
        SharedPreferences p = getSharedPreferences("cfg", MODE_PRIVATE);
        double homeLat, homeLng;
        float rOpen, rClose;
        try {
            homeLat = Double.parseDouble(p.getString("lat", "0"));
            homeLng = Double.parseDouble(p.getString("lng", "0"));
            rOpen = Float.parseFloat(p.getString("radius", "300"));
            rClose = Float.parseFloat(p.getString("radiusClose", "100"));
        } catch (Exception e) {
            return;
        }

        float[] res = new float[1];
        Location.distanceBetween(loc.getLatitude(), loc.getLongitude(), homeLat, homeLng, res);
        float dist = res[0];
        boolean insideOpen = dist <= rOpen;
        boolean insideClose = dist <= rClose;

        Journal.add(this, "fix: " + Math.round(dist) + " m");

        if (firstFix) {
            firstFix = false;
            wasInsideOpen = insideOpen;
            wasInsideClose = insideClose;
            return;
        }

        // Arrivee : on entre dans le grand rayon
        if (insideOpen && !wasInsideOpen) {
            Notif.show(this, "Portail", "ENTREE zone (dist " + Math.round(dist) + " m) -> ouverture");
            Trigger.checkBtAndOpen(this, "ouverture");
        }

        // Depart : on sort du petit rayon
        if (!insideClose && wasInsideClose) {
            Notif.show(this, "Portail", "SORTIE zone (dist " + Math.round(dist) + " m) -> fermeture");
            Trigger.checkBtAndOpen(this, "fermeture");
        }

        wasInsideOpen = insideOpen;
        wasInsideClose = insideClose;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startUpdates();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (client != null && callback != null) client.removeLocationUpdates(callback);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
