package com.portail.gate;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Trigger {

    private static final Map<String, Long> lastAction = new HashMap<>();
    private static final long COOLDOWN_MS = 60000;

    // action = "ouverture" ou "fermeture"
    static void checkBtAndOpen(final Context ctx, final String action) {
        BluetoothManager bm = (BluetoothManager) ctx.getSystemService(Context.BLUETOOTH_SERVICE);
        final BluetoothAdapter adapter = (bm != null) ? bm.getAdapter() : null;
        if (adapter == null) {
            Journal.add(ctx, "[" + action + "] Bluetooth indisponible");
            return;
        }

        final Set<String> found = new HashSet<>();
        final int[] pending = {2};
        final boolean[] finished = {false};
        int[] profiles = {BluetoothProfile.A2DP, BluetoothProfile.HEADSET};

        for (int prof : profiles) {
            boolean started = adapter.getProfileProxy(ctx, new BluetoothProfile.ServiceListener() {
                @Override
                public void onServiceConnected(int profile, BluetoothProfile proxy) {
                    try {
                        for (BluetoothDevice d : proxy.getConnectedDevices()) {
                            String dn;
                            try {
                                dn = d.getName();
                            } catch (SecurityException se) {
                                dn = null;
                            }
                            if (dn == null) dn = d.getAddress();
                            found.add(dn);
                        }
                    } catch (SecurityException se) {
                        Journal.add(ctx, "Permission BLUETOOTH_CONNECT manquante");
                    } finally {
                        adapter.closeProfileProxy(profile, proxy);
                    }
                    finish(ctx, found, pending, finished, action);
                }

                @Override
                public void onServiceDisconnected(int profile) {
                    finish(ctx, found, pending, finished, action);
                }
            }, prof);

            if (!started) finish(ctx, found, pending, finished, action);
        }
    }

    private static synchronized void finish(Context ctx, Set<String> found, int[] pending, boolean[] finished, String action) {
        pending[0]--;
        if (pending[0] > 0) return;
        if (finished[0]) return;
        finished[0] = true;

        SharedPreferences p = ctx.getSharedPreferences("cfg", Context.MODE_PRIVATE);
        Set<String> allVeh = toSet(p.getString("btNames", ""));
        Set<String> garageVeh = toSet(p.getString("btNamesGarage", ""));
        String portailId = p.getString("deviceId", "").trim();
        String garageId = p.getString("garageDeviceId", "").trim();

        boolean vehiclePresent = intersects(found, allVeh);
        boolean garageVehPresent = intersects(found, garageVeh);

        // PAS de notification si aucun vehicule autorise n'est connecte (juste le journal)
        if (!vehiclePresent) {
            Journal.add(ctx, "[" + action + "] pas de vehicule autorise (BT vus: " + found + ") -> ignore");
            return;
        }

        if (action.equals("ouverture")) {
            fire(ctx, "Portail ouverture", portailId);
            // Garage : ouverture seulement en approche, seulement pour la moto
            if (garageVehPresent && !garageId.isEmpty()) {
                fire(ctx, "Garage ouverture", garageId);
            }
        } else {
            fire(ctx, "Portail fermeture", portailId);
            // Garage : jamais de fermeture auto
        }
    }

    private static void fire(Context ctx, String label, String deviceId) {
        if (deviceId == null || deviceId.isEmpty()) return;
        long now = SystemClock.elapsedRealtime();
        Long last = lastAction.get(deviceId);
        if (last != null && now - last < COOLDOWN_MS) {
            Notif.show(ctx, "Portail", label + " ignoree (anti-rebond)");
            return;
        }
        lastAction.put(deviceId, now);
        new Thread(() -> {
            String r = SinricClient.open(ctx, deviceId);
            Notif.show(ctx, "Portail", label + " -> " + r);
        }).start();
    }

    private static Set<String> toSet(String csv) {
        Set<String> s = new HashSet<>();
        for (String x : csv.split(",")) {
            String t = x.trim().toLowerCase();
            if (!t.isEmpty()) s.add(t);
        }
        return s;
    }

    private static boolean intersects(Set<String> found, Set<String> allowedLower) {
        for (String f : found) {
            if (allowedLower.contains(f.trim().toLowerCase())) return true;
        }
        return false;
    }
}
