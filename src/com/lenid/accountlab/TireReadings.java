package com.lenid.accountlab;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Map;
import java.util.WeakHashMap;

public final class TireReadings {
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final ExecutorService worker = Executors.newSingleThreadExecutor();
    private static final Map<Activity, Runnable> screens = new WeakHashMap<>();
    private static final String[][] wheels = {
        {"lf","front_left"}, {"rf","front_right"}, {"lb","rear_left"}, {"rb","rear_right"}
    };

    public static void resume(Activity screen) {
        pause(screen);
        if (!AccountGate.logged()) return;
        render(screen, null);
        Runnable refresh = new Runnable() {
            public void run() {
                String profile = AccountGate.prefs().getString("verified_profile", "");
                worker.execute(() -> {
                    JSONObject tires = null;
                    try {
                        JSONArray vehicles = ControlBridge.api(screen.getApplicationContext(), "GET",
                                "/v1/me/vehicle-status", null).getJSONArray("vehicles");
                        if (vehicles.length() == 1) {
                            JSONObject car = vehicles.getJSONObject(0);
                            if (car.optBoolean("available") && !car.optBoolean("stale", true))
                                tires = car.getJSONObject("status").optJSONObject("tires");
                        }
                    } catch (Exception e) {
                        android.util.Log.w("OwnVehicleStatus", "Tire readings unavailable: " + e.getClass().getSimpleName());
                    }
                    final JSONObject snapshot = tires;
                    main.post(() -> {
                        if (screens.get(screen) != this) return;
                        boolean sameAccount = AccountGate.logged() && profile.equals(AccountGate.prefs().getString("verified_profile", ""));
                        render(screen, sameAccount ? snapshot : null);
                        main.postDelayed(this, 10000);
                    });
                });
            }
        };
        screens.put(screen, refresh);
        refresh.run();
    }

    public static void pause(Activity screen) {
        Runnable refresh = screens.remove(screen);
        if (refresh != null) main.removeCallbacks(refresh);
    }

    private static void render(Activity screen, JSONObject tires) {
        for (String[] wheel : wheels) {
            JSONObject data = tires == null ? null : tires.optJSONObject(wheel[1]);
            set(screen, wheel[0] + "_tire", "vehicle_tire_pressure_display", data, "pressure_kpa");
            set(screen, wheel[0] + "_temp", "vehicle_temperature_display", data, "temperature_c");
        }
    }

    private static void set(Activity screen, String id, String format, JSONObject values, String key) {
        TextView view = (TextView) AccountGate.named(screen, "textview_" + id);
        if (view == null) return;
        Object value = values == null ? null : values.opt(key);
        java.text.NumberFormat number = java.text.NumberFormat.getNumberInstance();
        number.setGroupingUsed(false);
        String display = value instanceof Number ? number.format(value) : "--";
        int resource = screen.getResources().getIdentifier(format, "string", screen.getPackageName());
        if (resource != 0) view.setText(screen.getString(resource, display));
    }
}
