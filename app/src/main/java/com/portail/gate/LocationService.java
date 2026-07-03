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
    private boolean wasInside = false;
    private boolean firstFix = true;

    @Override
    public void onCreate() {
        super.onCreate();
        startForegroundNotif();
        client = LocationServices.getFusedLocationProviderClient(this);
        startUpdates();
        Journal.add(this, "Service de surveillance demarre");
    }

    private void startForegroundNotif() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel c = new NotificationChannel("portail_svc", "Portail surveillance", NotificationManager.IMPORTANCE_LOW);
        nm.createNotificationChannel(c);
        Notification n = new Notification.Builder(this, "portail_svc")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle("Portail")
                .setContentText("Surveillance de l'arrivee active")
                .setOngoing(true)
                .build();
        startForeground(42, n);
    }

    private void startUpdates() {
        LocationRequest req = new LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 30000)
                .setMinUpdateIntervalMillis(15000)
                .build();
        callback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult result) {
                Location loc = result.getLastLocation();
                if (loc != null) handleLocation(loc);
            }
        };
        try {
            client.requestLocationUpdates(req, callback, Looper.getMainLooper());
        } catch (SecurityException e) {
            Notif.show(this, "Portail", "Service: permission localisation manquante");
        }
    }

    private void handleLocation(Location loc) {
        SharedPreferences p = getSharedPreferences("cfg", MODE_PRIVATE);
        double homeLat, homeLng;
        float radius;
        try {
            homeLat = Double.parseDouble(p.getString("lat", "0"));
            homeLng = Double.parseDouble(p.getString("lng", "0"));
            radius = Float.parseFloat(p.getString("radius", "300"));
        } catch (Exception e) {
            return;
        }

        float[] res = new float[1];
        Location.distanceBetween(loc.getLatitude(), loc.getLongitude(), homeLat, homeLng, res);
        float dist = res[0];
        boolean inside = dist <= radius;

        Journal.add(this, "fix: " + Math.round(dist) + " m (" + (inside ? "DANS" : "HORS") + ")");

        if (firstFix) {
            firstFix = false;
            wasInside = inside;
            return;
        }

        if (inside && !wasInside) {
            Notif.show(this, "Portail", "ENTREE zone detectee (dist " + Math.round(dist) + " m)");
            Trigger.checkBtAndOpen(this);
        }
        wasInside = inside;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
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
