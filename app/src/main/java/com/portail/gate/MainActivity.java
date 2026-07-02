package com.portail.gate;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;

public class MainActivity extends Activity {

    private EditText apiKey, deviceId, lat, lng, radius, btNames;
    private TextView status;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);

        apiKey = findViewById(R.id.apiKey);
        deviceId = findViewById(R.id.deviceId);
        lat = findViewById(R.id.lat);
        lng = findViewById(R.id.lng);
        radius = findViewById(R.id.radius);
        btNames = findViewById(R.id.btNames);
        status = findViewById(R.id.status);

        SharedPreferences p = getSharedPreferences("cfg", MODE_PRIVATE);
        apiKey.setText(p.getString("apiKey", ""));
        deviceId.setText(p.getString("deviceId", "6a34287f09efd1746c0ff137"));
        lat.setText(p.getString("lat", ""));
        lng.setText(p.getString("lng", ""));
        radius.setText(p.getString("radius", "300"));
        btNames.setText(p.getString("btNames", "Toyota Multimedia, Moto"));

        ((Button) findViewById(R.id.btnPerms)).setOnClickListener(v -> requestPerms());
        ((Button) findViewById(R.id.btnSave)).setOnClickListener(v -> save());
        ((Button) findViewById(R.id.btnTest)).setOnClickListener(v -> test());
    }

    private void requestPerms() {
        requestPermissions(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                "android.permission.BLUETOOTH_CONNECT",
                "android.permission.POST_NOTIFICATIONS"
        }, 100);
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] res) {
        super.onRequestPermissionsResult(req, perms, res);
        if (req == 100) {
            // La localisation en arriere-plan doit etre demandee separement (Android 11+)
            requestPermissions(new String[]{"android.permission.ACCESS_BACKGROUND_LOCATION"}, 101);
        } else if (req == 101) {
            Toast.makeText(this, "Verifie: Localisation = 'Toujours autoriser'", Toast.LENGTH_LONG).show();
        }
    }

    private void save() {
        SharedPreferences.Editor e = getSharedPreferences("cfg", MODE_PRIVATE).edit();
        e.putString("apiKey", apiKey.getText().toString().trim());
        e.putString("deviceId", deviceId.getText().toString().trim());
        e.putString("lat", lat.getText().toString().trim());
        e.putString("lng", lng.getText().toString().trim());
        e.putString("radius", radius.getText().toString().trim());
        e.putString("btNames", btNames.getText().toString());
        e.apply();
        try {
            registerGeofence(this);
            status.setText("Geofence activee. En attente d'arrivee.");
        } catch (Exception ex) {
            status.setText("Erreur: " + ex.getMessage());
        }
    }

    private void test() {
        status.setText("Test en cours...");
        new Thread(() -> {
            final String r = SinricClient.open(this);
            runOnUiThread(() -> status.setText("Test: " + r));
        }).start();
    }

    static void registerGeofence(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences("cfg", Context.MODE_PRIVATE);
        double la, lo;
        float ra;
        try {
            la = Double.parseDouble(p.getString("lat", "0"));
            lo = Double.parseDouble(p.getString("lng", "0"));
            ra = Float.parseFloat(p.getString("radius", "300"));
        } catch (Exception e) {
            return;
        }

        Geofence g = new Geofence.Builder()
                .setRequestId("maison")
                .setCircularRegion(la, lo, ra)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                .setNotificationResponsiveness(0)
                .build();

        GeofencingRequest req = new GeofencingRequest.Builder()
                .setInitialTrigger(0)
                .addGeofence(g)
                .build();

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 31) {
            flags |= PendingIntent.FLAG_MUTABLE;
        }
        Intent i = new Intent(ctx, GeofenceReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 0, i, flags);

        if (ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        LocationServices.getGeofencingClient(ctx).addGeofences(req, pi);
    }
}
