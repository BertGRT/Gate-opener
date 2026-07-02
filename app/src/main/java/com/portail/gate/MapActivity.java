package com.portail.gate;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;

public class MapActivity extends Activity {

    private MapView map;
    private Marker marker;
    private double selLat, selLng;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Configuration.getInstance().setUserAgentValue(getPackageName());

        setContentView(R.layout.activity_map);
        map = findViewById(R.id.map);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);

        double lat = getIntent().getDoubleExtra("lat", 48.8566);
        double lng = getIntent().getDoubleExtra("lng", 2.3522);
        selLat = lat;
        selLng = lng;

        GeoPoint start = new GeoPoint(lat, lng);
        map.getController().setZoom(17.0);
        map.getController().setCenter(start);

        marker = new Marker(map);
        marker.setPosition(start);
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        map.getOverlays().add(marker);

        MapEventsReceiver receiver = new MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint p) {
                selLat = p.getLatitude();
                selLng = p.getLongitude();
                marker.setPosition(p);
                map.invalidate();
                return true;
            }

            @Override
            public boolean longPressHelper(GeoPoint p) {
                return false;
            }
        };
        map.getOverlays().add(0, new MapEventsOverlay(receiver));

        ((Button) findViewById(R.id.btnValidate)).setOnClickListener(v -> {
            Intent res = new Intent();
            res.putExtra("lat", selLat);
            res.putExtra("lng", selLng);
            setResult(RESULT_OK, res);
            finish();
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (map != null) map.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (map != null) map.onPause();
    }
}
