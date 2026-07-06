package com.portail.gate;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

public class LocationService extends Service {

    private static final float MAX_ACCURACY_M = 100f;

    private FusedLocationProviderClient client;
    private LocationCallback callback;
    private BroadcastReceiver btReceiver;
    private boolean gpsActive = false;
    private boolean wasInsideOpen = false;
    private boolean wasInsideClose = false;
    private boolean firstFix = true;

    @Override
    public void onCreate() {
        super.onCreate();
        client = LocationServices.getFusedLocationProviderClient(this);
        showOngoing("En attente d'un vehicule");
        registerBt();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Au demarrage / re-enregistrement : si un vehicule est deja connecte, on lance le GPS
        BtScan.connectedNames(this, found -> {
            if (BtScan.anyAuthorized(this, found)) startGps();
            else stopGps();
        });
        return START_STICKY;
    }

    private void registerBt() {
        IntentFilter f = new IntentFilter();
        f.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        f.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        btReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                String a = intent.getAction();
                if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(a)) {
                    BtScan.connectedNames(LocationService.this, found -> {
                        if (BtScan.anyAuthorized(LocationService.this, found)) startGps();
                    });
                } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(a)) {
                    BtScan.connectedNames(LocationService.this, found -> {
                        if (!BtScan.anyAuthorized(LocationService.this, found)) stopGps();
                    });
                }
            }
        };
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(btReceiver, f, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(btReceiver, f);
        }
    }

    private void startGps() {
        if (gpsActive) return;
        gpsActive = true;
        firstFix = true;

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
        try {
            client.requestLocationUpdates(req, callback, Looper.getMainLooper());
            showOngoing("Vehicule connecte - surveillance GPS active");
        } catch (SecurityException e) {
            gpsActive = false;
            showOngoing("Permission localisation manquante");
        }
    }

    private void stopGps() {
        if (!gpsActive) return;
        gpsActive = false;
        if (callback != null) client.removeLocationUpdates(callback);
        showOngoing("En attente d'un vehicule");
    }

    private void handleLocation(Location loc) {
        if (loc.hasAccuracy() && loc.getAccuracy() > MAX_ACCURACY_M) {
            return; // fix trop imprecis
        }

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

        showOngoing("Distance " + Math.round(dist) + " m");

        if (firstFix) {
            firstFix = false;
            wasInsideOpen = insideOpen;
            wasInsideClose = insideClose;
            return;
        }

        if (insideOpen && !wasInsideOpen) {
            Trigger.checkBtAndOpen(this, "ouverture");
        }
        if (!insideClose && wasInsideClose) {
            Trigger.checkBtAndOpen(this, "fermeture");
        }

        wasInsideOpen = insideOpen;
        wasInsideClose = insideClose;
    }

    private void showOngoing(String text) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel c = new NotificationChannel("portail_svc", "Portail surveillance", NotificationManager.IMPORTANCE_LOW);
        nm.createNotificationChannel(c);
        Notification n = new Notification.Builder(this, "portail_svc")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle("Portail")
                .setContentText(text)
                .setOngoing(true)
                .build();
        startForeground(42, n);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (client != null && callback != null) client.removeLocationUpdates(callback);
        if (btReceiver != null) {
            try {
                unregisterReceiver(btReceiver);
            } catch (Exception e) {
                // ignore
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
