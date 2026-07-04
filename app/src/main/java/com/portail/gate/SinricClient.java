package com.portail.gate;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class SinricClient {

    // Envoie une impulsion (setPowerState On) au device Sinric indique
    static String open(Context ctx, String deviceId) {
        if (deviceId == null || deviceId.trim().isEmpty()) return "device vide";
        SharedPreferences p = ctx.getSharedPreferences("cfg", Context.MODE_PRIVATE);
        String apiKey = p.getString("apiKey", "");
        String body = "{\"type\":\"request\",\"action\":\"setPowerState\",\"value\":\"{\\\"state\\\":\\\"On\\\"}\"}";

        HttpURLConnection c = null;
        try {
            URL url = new URL("https://api.sinric.pro/api/v1/devices/" + deviceId.trim() + "/action");
            c = (HttpURLConnection) url.openConnection();
            c.setRequestMethod("POST");
            c.setRequestProperty("x-sinric-api-key", apiKey);
            c.setRequestProperty("Content-Type", "application/json");
            c.setDoOutput(true);
            c.setConnectTimeout(10000);
            c.setReadTimeout(10000);
            OutputStream os = c.getOutputStream();
            os.write(body.getBytes("UTF-8"));
            os.close();
            int code = c.getResponseCode();
            return "HTTP " + code;
        } catch (Exception e) {
            return "Erreur: " + e.getMessage();
        } finally {
            if (c != null) c.disconnect();
        }
    }
}
