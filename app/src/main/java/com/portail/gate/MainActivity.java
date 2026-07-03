package com.portail.gate;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

        ((Button) findViewById(R.id.btnPickMap)).setOnClickListener(v -> openMap());
        ((Button) findViewById(R.id.btnPickBt)).setOnClickListener(v -> showBtPicker());
        ((Button) findViewById(R.id.btnPerms)).setOnClickListener(v -> requestPerms());
        ((Button) findViewById(R.id.btnSave)).setOnClickListener(v -> save());
        ((Button) findViewById(R.id.btnTest)).setOnClickListener(v -> test());
        ((Button) findViewById(R.id.btnLog)).setOnClickListener(v -> startActivity(new Intent(this, LogActivity.class)));
    }

    private void openMap() {
        Intent i = new Intent(this, MapActivity.class);
        try {
            i.putExtra("lat", Double.parseDouble(lat.getText().toString().trim()));
            i.putExtra("lng", Double.parseDouble(lng.getText().toString().trim()));
        } catch (Exception e) {
            // pas de coords valides -> la carte utilisera son point par defaut
        }
        startActivityForResult(i, 200);
    }

    @Override
    protected void onActivityResult(int req, int resultCode, Intent data) {
        super.onActivityResult(req, resultCode, data);
        if (req == 200 && resultCode == RESULT_OK && data != null) {
            lat.setText(String.valueOf(data.getDoubleExtra("lat", 0)));
            lng.setText(String.valueOf(data.getDoubleExtra("lng", 0)));
        }
    }

    private void showBtPicker() {
        BluetoothManager bm = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = (bm != null) ? bm.getAdapter() : null;
        if (adapter == null) {
            Toast.makeText(this, "Bluetooth indisponible", Toast.LENGTH_LONG).show();
            return;
        }

        Set<BluetoothDevice> bonded;
        try {
            bonded = adapter.getBondedDevices();
        } catch (SecurityException e) {
            Toast.makeText(this, "Autorise d'abord les permissions (bouton 1)", Toast.LENGTH_LONG).show();
            return;
        }
        if (bonded == null || bonded.isEmpty()) {
            Toast.makeText(this, "Aucun appareil Bluetooth appaire", Toast.LENGTH_LONG).show();
            return;
        }

        final List<String> names = new ArrayList<>();
        for (BluetoothDevice d : bonded) {
            String n = null;
            try {
                n = d.getName();
            } catch (SecurityException e) {
                // ignore
            }
            if (n == null) n = d.getAddress();
            if (!names.contains(n)) names.add(n);
        }

        final Set<String> current = new HashSet<>();
        for (String s : btNames.getText().toString().split(",")) {
            String t = s.trim();
            if (!t.isEmpty()) current.add(t);
        }

        final boolean[] checked = new boolean[names.size()];
        for (int i = 0; i < names.size(); i++) {
            checked[i] = current.contains(names.get(i));
        }

        CharSequence[] items = names.toArray(new CharSequence[0]);
        new AlertDialog.Builder(this)
                .setTitle("Appareils Bluetooth autorises")
                .setMultiChoiceItems(items, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton("OK", (dialog, which) -> {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < names.size(); i++) {
                        if (checked[i]) {
                            if (sb.length() > 0) sb.append(", ");
                            sb.append(names.get(i));
                        }
                    }
                    btNames.setText(sb.toString());
                })
                .setNegativeButton("Annuler", null)
                .show();
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
            status.setText("Enregistre. (voir la notif / le journal)");
        } catch (Exception ex) {
            status.setText("Erreur: " + ex.getMessage());
        }
    }

    private void test() {
        status.setText("Test en cours...");
        new Thread(() -> {
            final String r = SinricClient.open(this);
            runOnUiThread(() -> status.setText("Test: " + r));
            Notif.show(this, "Portail", "Test manuel: " + r);
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
            Notif.show(ctx, "Portail", "Coordonnees invalides");
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
            Notif.show(ctx, "Portail", "Permission localisation manquante");
            return;
        }
        LocationServices.getGeofencingClient(ctx).addGeofences(req, pi)
                .addOnSuccessListener(a -> Notif.show(ctx, "Portail", "Geofence enregistree OK (rayon " + ra + " m)"))
                .addOnFailureListener(e -> Notif.show(ctx, "Portail", "Echec geofence: " + e.getMessage()));
    }
}
