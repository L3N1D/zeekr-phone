package com.lenid.accountlab;

import android.util.Log;
import org.json.JSONObject;

public final class DestinationCommand {
    public static void send(String name, String address, Double longitude, Double latitude, Object callback) {
        NativeCommand.Result result = (accepted, message) -> {
            try {
                Class.forName("kotlin.jvm.functions.Function2").getMethod("invoke", Object.class, Object.class)
                    .invoke(callback, accepted, message);
            } catch (ReflectiveOperationException e) {
                Log.w("OwnServerCommand", "Map callback unavailable", e);
            }
        };
        try {
            JSONObject destination = new JSONObject().put("name", name == null ? "" : name)
                .put("address", address == null ? "" : address).put("longitude", longitude).put("latitude", latitude);
            if (latitude == null || longitude == null) throw new IllegalArgumentException();
            NativeCommand.send("navigation.destination", destination, result);
        } catch (Exception e) {
            result.complete(false, "У места отсутствуют координаты");
        }
    }
}
