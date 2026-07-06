package com.portail.gate;

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
        BtScan.connectedNames(ctx, found -> act(ctx, found, action));
    }

    private static synchronized void act(Context ctx, Set<String> found, String action) {
        SharedPreferences p = ctx.getSharedPreferences("cfg", Context.MODE_PRIVATE);
        Set<String> allVeh = toSet(p.getString("btNames", ""));
        Set<String> garageVeh = toSet(p.getString("btNamesGarage", ""));
        String portailId = p.getString("deviceId", "").trim();
        String garageId = p.getString("garageDeviceId", "").trim();

        boolean vehiclePresent = intersects(found, allVeh);
        boolean garageVehPresent = intersects(found, garageVeh);

        // Aucun vehicule autorise connecte -> aucune action, aucune notif
        if (!vehiclePresent) return;

        if (action.equals("ouverture")) {
            fire(ctx, "Portail ouverture", portailId);
            if (garageVehPresent && !garageId.isEmpty()) {
                fire(ctx, "Garage ouverture", garageId);
            }
        } else {
            fire(ctx, "Portail fermeture", portailId);
        }
    }

    private static void fire(Context ctx, String label, String deviceId) {
        if (deviceId == null || deviceId.isEmpty()) return;
        long now = SystemClock.elapsedRealtime();
        Long last = lastAction.get(deviceId);
        if (last != null && now - last < COOLDOWN_MS) {
            return; // anti-rebond silencieux
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
