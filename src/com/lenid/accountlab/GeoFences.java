package com.lenid.accountlab;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;

/** Cold Kotlin Flow matching the location SDK's return type. The HTTP call runs on Dispatchers.IO. */
public final class GeoFences {
    public static Object flow(String operation, Object input, boolean enabled) throws Exception {
        Class<?> function = Class.forName("kotlin.jvm.functions.Function2");
        Class<?> flows = Class.forName("kotlinx.coroutines.flow.FlowKt");
        Class<?> collector = Class.forName("kotlinx.coroutines.flow.FlowCollector");
        String createId = UUID.randomUUID().toString().replace("-", "");
        Object block = Proxy.newProxyInstance(function.getClassLoader(), new Class[]{function}, (proxy, method, args) -> {
            if (!"invoke".equals(method.getName())) {
                if ("hashCode".equals(method.getName())) return System.identityHashCode(proxy);
                if ("equals".equals(method.getName())) return proxy == args[0];
                return "GeoFences." + operation;
            }
            Object response = request(operation, input, enabled, createId);
            try {
                return collector.getMethod("emit", Object.class, Class.forName("kotlin.coroutines.Continuation"))
                        .invoke(args[0], response, args[1]);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        });
        Object flow = flows.getMethod("flow", function).invoke(null, block);
        Object io = Class.forName("kotlinx.coroutines.Dispatchers").getMethod("getIO").invoke(null);
        return flows.getMethod("flowOn", Class.forName("kotlinx.coroutines.flow.Flow"),
                Class.forName("kotlin.coroutines.CoroutineContext")).invoke(null, flow, io);
    }

    private static Object request(String operation, Object value, boolean enabled, String createId) throws Exception {
        AccountGate.ensureContext();
        if (!AccountGate.logged()) throw new IllegalStateException("Войдите в аккаунт");
        Class<?> gsonClass = Class.forName("com.google.gson.Gson");
        Object gson = gsonClass.getConstructor().newInstance();
        JSONArray bindings = ControlBridge.api(AccountGate.context, "GET", "/v1/vehicles", null).getJSONArray("vehicles");
        String id = null;
        for (int i = 0; i < bindings.length(); i++) {
            JSONObject car = bindings.getJSONObject(i);
            if (!"active".equals(car.optString("state"))) continue;
            if (id != null) throw new IllegalStateException("Сначала выберите автомобиль");
            id = car.getString("binding_id");
        }
        if (id == null) throw new IllegalStateException("Сначала привяжите автомобиль");
        String path = "/v1/bindings/" + id + "/geofences";
        Class<?> bean = Class.forName("com.geely.snc.location.base.mode.ElectronicFenceResponseBean");
        Object result = null;
        if ("list".equals(operation)) {
            JSONArray rows = ControlBridge.api(AccountGate.context, "GET", path, null).getJSONArray("geofences");
            ArrayList<Object> list = new ArrayList<>();
            for (int i = 0; i < rows.length(); i++)
                list.add(gsonClass.getMethod("fromJson", String.class, Class.class).invoke(gson, rows.getJSONObject(i).toString(), bean));
            result = list;
        } else {
            JSONObject body = new JSONObject().put("operation", operation);
            if ("create".equals(operation) || "update".equals(operation)) {
                JSONObject fence = new JSONObject((String)gsonClass.getMethod("toJson",Object.class).invoke(gson,value));
                body.put("id", "create".equals(operation) ? createId : fence.getString("id"));
                fence.remove("id");
                body.put("fence", fence);
            } else {
                body.put("id", value);
                if ("enable".equals(operation)) body.put("enabled", enabled);
            }
            JSONObject reply = ControlBridge.api(AccountGate.context, "POST", path, body);
            if ("create".equals(operation))
                result = gsonClass.getMethod("fromJson",String.class,Class.class).invoke(gson,reply.getJSONObject("fence").toString(),bean);
        }
        return Class.forName("com.geely.snc.network.http.biz.model.http.BaseHttpResponse")
                .getConstructor(String.class,String.class,Object.class).newInstance("0","",result);
    }
}
