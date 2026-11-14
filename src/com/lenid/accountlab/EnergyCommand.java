package com.lenid.accountlab;

import org.json.JSONObject;
import org.json.JSONTokener;

public final class EnergyCommand {
    // Schedules use the same Gson serializers and annotations as the base application.
    private static void parameters(String action, String[] keys, Object... values) {
        try {
            JSONObject result=new JSONObject();
            Class<?> type=Class.forName("com.google.gson.Gson");
            Object gson=type.getConstructor().newInstance();
            for(int i=0;i<keys.length;i++) {
                String json=(String)type.getMethod("toJson",Object.class).invoke(gson,values[i]);
                result.put(keys[i],new JSONTokener(json).nextValue());
            }
            NativeCommand.send(action,result);
        } catch(Exception e) {
            android.util.Log.e("OwnServerCommand","Cannot encode energy settings",e);
            if(AccountGate.context!=null)NativeCommand.note(AccountGate.context,"Не удалось прочитать настройки");
        }
    }
    public static void startCharge(){NativeCommand.send("charging.start",null);}
    public static void stopCharge(){NativeCommand.send("charging.stop",null);}
    public static void startDischarge(){NativeCommand.send("discharge.start",null);}
    public static void stopDischarge(){NativeCommand.send("discharge.stop",null);}
    public static void batteryTempRegulation(boolean on){NativeCommand.send(on?"battery.preheat.on":"battery.preheat.off",null);}
    public static void lowVoltageBatteryProtection(boolean on){NativeCommand.send(on?"battery.low_voltage.on":"battery.low_voltage.off",null);}
    public static void setChargeSoc(String soc){parameters("charging.limit",new String[]{"soc"},soc);}
    public static void setDischargeSoc(String soc){parameters("discharge.limit",new String[]{"soc"},soc);}
    public static void setSingleChargingPlan(String command,String soc){parameters("charging.single_plan",new String[]{"command","soc"},command,soc);}
    public static void setCycleChargingPlan(String command,Object schedule){parameters("charging.cycle_plan",new String[]{"command","scheduleList"},command,schedule);}
    public static void setChargingPlan(Object plan){parameters("charging.plan",new String[]{"request"},plan);}
    public static void setBookingCharge(Object plan){parameters("charging.booking",new String[]{"request"},plan);}
    public static void setTravelPlan(Object plan){parameters("travel.plan",new String[]{"request"},plan);}
    public static void setTravelPlanPlus(Object plan){parameters("travel.plan_plus",new String[]{"request"},plan);}
    public static void suspension(int level){parameters("suspension.level",new String[]{"level"},level);}
    public static void fridgeSchedule(Object plan){parameters("fridge.schedule",new String[]{"request"},plan);}
    public static void fridgeControl(Object settings){parameters("fridge.control",new String[]{"request"},settings);}
}
