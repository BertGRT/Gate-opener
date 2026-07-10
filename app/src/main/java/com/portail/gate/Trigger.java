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

    // Ouverture a l'arrivee (plus de fermeture : geree par la tempo du portail)
    static void checkBtAndOpen(final Context ctx) {
        BtScan.connectedNames(ctx, found -> act(ctx, found));
    }

    private static synchronized void act(Context ctx, Set<String> found) {
        SharedPreferences p = ctx.getSharedPreferences("cfg", Context.MODE_PRIVATE);
        Set<String> portailVehs = toSet(p.getString("btNames", ""));
        Set<String> garageVehs = toSet(p.getString("btNamesGarage", ""));
        String portailId = p.getString("deviceId", "").trim();
        String garageId = p.getString("garageDeviceId", "").trim();

        boolean portailVeh = intersects(found, portailVehs);
        boolean garageVeh = intersects(found, garageVehs);

        // Aucun vehicule autorise connecte -> rien, aucune notif
        if (!portailVeh && !garageVeh) return;

        // Portail et garage independants
        if (portailVeh) fire(ctx, "Portail ouverture", portailId);
        if (garageVeh && !garageId.isEmpty()) fire(ctx, "Garage ouverture", garageId);
    }

    private static void fire(Context ctx, String label, String deviceId) {
        if (deviceId == null || deviceId.isEmpty()) return;
        long now = SystemClock.elapsedRealtime();
        Long last = lastAction.get(deviceId);
        if (last != null && now - last < COOLDOWN_MS) return; // anti-rebond silencieux
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
