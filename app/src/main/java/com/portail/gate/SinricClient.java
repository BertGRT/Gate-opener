package com.portail.gate;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class SinricClient {

    // Appel HTTP a executer sur un thread de fond (jamais sur le thread principal)
    static String open(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences("cfg", Context.MODE_PRIVATE);
        String apiKey = p.getString("apiKey", "");
        String deviceId = p.getString("deviceId", "");
        String body = "{\"type\":\"request\",\"action\":\"setPowerState\",\"value\":\"{\\\"state\\\":\\\"On\\\"}\"}";

        HttpURLConnection c = null;
        try {
            URL url = new URL("https://api.sinric.pro/api/v1/devices/" + deviceId + "/action");
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
