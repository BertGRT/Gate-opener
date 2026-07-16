package com.portail.gate;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity {

    private EditText apiKey, deviceId, garageDeviceId, lat, lng, radius, interval, btNames, btNamesGarage;
    private TextView status;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);

        apiKey = findViewById(R.id.apiKey);
        deviceId = findViewById(R.id.deviceId);
        garageDeviceId = findViewById(R.id.garageDeviceId);
        lat = findViewById(R.id.lat);
        lng = findViewById(R.id.lng);
        radius = findViewById(R.id.radius);
        interval = findViewById(R.id.interval);
        btNames = findViewById(R.id.btNames);
        btNamesGarage = findViewById(R.id.btNamesGarage);
        status = findViewById(R.id.status);

        SharedPreferences p = getSharedPreferences("cfg", MODE_PRIVATE);
        apiKey.setText(p.getString("apiKey", ""));
        deviceId.setText(p.getString("deviceId", "6a34287f09efd1746c0ff137"));
        garageDeviceId.setText(p.getString("garageDeviceId", "6934ac806ebb39d664c84311"));
        lat.setText(p.getString("lat", ""));
        lng.setText(p.getString("lng", ""));
        radius.setText(p.getString("radius", "300"));
        interval.setText(p.getString("interval", "15"));
        btNames.setText(p.getString("btNames", "CAR MULTIMEDIA, Moto"));
        btNamesGarage.setText(p.getString("btNamesGarage", "Moto"));

        ((Button) findViewById(R.id.btnPickMap)).setOnClickListener(v -> openMap());
        ((Button) findViewById(R.id.btnPickBt)).setOnClickListener(v -> showBtPicker(btNames));
        ((Button) findViewById(R.id.btnPickBtGarage)).setOnClickListener(v -> showBtPicker(btNamesGarage));
        ((Button) findViewById(R.id.btnShowBt)).setOnClickListener(v -> showConnectedBt());
        ((Button) findViewById(R.id.btnPerms)).setOnClickListener(v -> requestPerms());
        ((Button) findViewById(R.id.btnSave)).setOnClickListener(v -> save());
        ((Button) findViewById(R.id.btnTest)).setOnClickListener(v -> testDevice(deviceId, "portail"));
        ((Button) findViewById(R.id.btnTestGarage)).setOnClickListener(v -> testDevice(garageDeviceId, "garage"));
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

    // Selecteur d'appareils Bluetooth appaires, ecrit dans le champ 'target'
    private void showBtPicker(final EditText target) {
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
        for (String s : target.getText().toString().split(",")) {
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
                    target.setText(sb.toString());
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    // Diagnostic : ce que voient les profils VS ce que suit le service
    private void showConnectedBt() {
        final String last = getSharedPreferences("cfg", MODE_PRIVATE).getString("lastConnected", "");
        BtScan.connectedNames(this, found -> runOnUiThread(() -> {
            String msg = "Profils A2DP / mains-libres :\n"
                    + (found.isEmpty() ? "(vide)" : found.toString())
                    + "\n\nSuivi par le service (evenements Bluetooth) :\n"
                    + (last.isEmpty() ? "(vide)" : last);
            new AlertDialog.Builder(this)
                    .setTitle("Diagnostic Bluetooth")
                    .setMessage(msg)
                    .setPositiveButton("OK", null)
                    .show();
        }));
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
        e.putString("garageDeviceId", garageDeviceId.getText().toString().trim());
        e.putString("lat", lat.getText().toString().trim());
        e.putString("lng", lng.getText().toString().trim());
        e.putString("radius", radius.getText().toString().trim());
        e.putString("interval", interval.getText().toString().trim());
        e.putString("btNames", btNames.getText().toString());
        e.putString("btNamesGarage", btNamesGarage.getText().toString());
        e.apply();

        startMonitoring(this);
        Notif.show(this, "Portail", "Surveillance demarree (GPS actif seulement en vehicule)");
        status.setText("Surveillance active (GPS uniquement en vehicule).");
    }

    private void testDevice(final EditText idField, final String label) {
        status.setText("Test " + label + "...");
        final String id = idField.getText().toString().trim();
        new Thread(() -> {
            final String r = SinricClient.open(this, id);
            runOnUiThread(() -> status.setText("Test " + label + ": " + r));
            Notif.show(this, "Portail", "Test manuel " + label + ": " + r);
        }).start();
    }

    // Demarre le service de surveillance en avant-plan
    static void startMonitoring(Context ctx) {
        ctx.startForegroundService(new Intent(ctx, LocationService.class));
    }
}
