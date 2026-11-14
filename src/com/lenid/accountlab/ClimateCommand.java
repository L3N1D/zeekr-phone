package com.lenid.accountlab;

import org.json.JSONArray;
import org.json.JSONObject;

// Arguments come from the stock climate controls, after their capability and selection logic.
public final class ClimateCommand {
    private static final String PREFIX = "climate.";
    private static String name(Object command) {
        return command == null ? "" : command.getClass().getSimpleName();
    }
    private static JSONObject body(Object... pairs) {
        try {
            JSONObject result = new JSONObject();
            for (int i=0;i<pairs.length;i+=2)
                if (pairs[i+1]!=null) result.put((String)pairs[i],JSONObject.wrap(pairs[i+1]));
            return result;
        } catch (Exception error) { throw new IllegalArgumentException(error); }
    }
    private static void send(String action, JSONObject values, Object specification) {
        try {
            if (specification!=null) {
                Iterable<?> list=(Iterable<?>)specification.getClass().getMethod("getServiceParameters").invoke(specification);
                JSONArray args=new JSONArray();
                if (list!=null) for (Object item:list) {
                    Class<?> type=item.getClass();
                    args.put(body("key",type.getMethod("getKey").invoke(item),"value",type.getMethod("getValue").invoke(item)));
                }
                if (args.length()!=0) values.put("serviceParameters",args);
            }
            NativeCommand.send(PREFIX+action,values);
        } catch (Exception error) {
            android.util.Log.w("OwnServerCommand","Climate parameters unreadable",error);
            if (AccountGate.context!=null) NativeCommand.note(AccountGate.context,"Не удалось прочитать параметры климата");
        }
    }
    public static void air(boolean on,int minutes,float temp,Object command,String source,boolean combined,Object spec) {
        send("ac",body("enabled",on,"duration_minutes",minutes,"temperature_c",(double)temp),spec);
    }
    public static void seats(int mode,int[] vent,int[] heat,boolean[] ventChanged,boolean[] heatChanged,int minutes,
                             boolean timeChanged,Object command,boolean combined,Object spec) {
        send(mode==1?"seat_vent":"seat_heat",body("levels",mode==1?vent:heat,
            "changed",mode==1?ventChanged:heatChanged,"duration_minutes",minutes,"time_changed",timeChanged),spec);
    }
    public static void wheel(boolean on,int minutes,boolean combined,Object spec) {
        send("wheel_heat",body("enabled",on,"duration_minutes",minutes),spec);
    }
    public static void ventilation(Object cmd,boolean on,int minutes,boolean combined,Object spec) {
        send("cabin_vent",body("enabled",on,"duration_minutes",minutes),spec);
    }
    public static void fragrance(boolean on,String level,String code,String id,boolean sterilize,int minutes) {
        send("fragrance",body("enabled",on,"level",level,"code",code,"id",id,"sterilize",sterilize,"duration_minutes",minutes),null);
    }
    public static void oxygen(Object cmd,boolean on,int minutes) {
        send("oxygen",body("enabled",on,"duration_minutes",minutes),null);
    }
    public static boolean simple(Object command,Object spec) {
        String n=name(command);
        switch(n) {
            case "DefrostOnCmd": case "DefrostOffCmd": send("defrost",body("enabled",n.equals("DefrostOnCmd")),spec);break;
            case "RSMOnCmd": case "RSMOffCmd": send("thermostat",body("enabled",n.equals("RSMOnCmd")),spec);break;
            case "AirPurificationOpenCmd": case "AirPurificationCloseCmd": send("purify",body("enabled",n.equals("AirPurificationOpenCmd")),spec);break;
            default:return false;
        }
        return true;
    }
    public static void intelligent(Object bean) {
        try {
            Class<?> t=bean.getClass();
            send("smart_clean",body("command",t.getMethod("getCommand").invoke(bean),"source",t.getMethod("getDataSource").invoke(bean)),null);
        } catch (Exception e) {android.util.Log.w("OwnServerCommand","Invalid air cleaning settings",e);}
    }
}
