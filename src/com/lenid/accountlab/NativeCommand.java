package com.lenid.accountlab;
import android.content.Context;import android.os.*;import android.widget.Toast;import org.json.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.AtomicBoolean;
/** Routes original command objects; never fabricates a vehicle execution result. */
public final class NativeCommand {
 static final Map<String,String> actions=new HashMap<>();static final ExecutorService worker=Executors.newSingleThreadExecutor();static final AtomicBoolean busy=new AtomicBoolean();
 static {String[][] rows={{"CentralLockCmd","doors.lock"},{"CentralUnLockCmd","doors.unlock"},{"WindowOpenCmd","windows.open"},{"WindowCloseCmd","windows.close"},{"WindowOpenHalfCmd","windows.vent"},{"TrunkOpenCmd","trunk.open"},{"TrunkCloseCmd","trunk.close"},{"ChargePortCoverOpenCmd","charge_port.open"},{"ChargePortCoverCloseCmd","charge_port.close"}};for(String[] r:rows)actions.put(r[0],r[1]);}
 static {
  actions.put("SunroofOpenCmd","sunroof.open");
  actions.put("SunroofCloseCmd","sunroof.close");
  actions.put("WinSunShadeOpenCmd","sunshade.open");
  actions.put("WinSunShadeCloseCmd","sunshade.close");
  actions.put("ChargeSlowOpenCmd","rear_charge_port.open");
  actions.put("ChargeSlowCloseCmd","rear_charge_port.close");
  actions.put("FrontHatchCoverOpenCmd","frunk.open");
  actions.put("SunroofVentCmd","sunroof.vent");
  actions.put("FindCarLightCmd","find.flash");
  actions.put("FindCarLightAndVoiceCmd","find.flash_horn");
  actions.put("SentryOpenCmd","sentry.on");
  actions.put("SentryCloseCmd","sentry.off");
  actions.put("BatteryTempRegulationOnCmd","battery.preheat.on");
  actions.put("BatteryTempRegulationOffCmd","battery.preheat.off");
  actions.put("SendSlideOpenLeftDoorCmd","doors.sliding.left.open");
  actions.put("SendDoubleDoorLeftOpenCmd","doors.double.left.open");
  actions.put("SendSlideCloseLeftDoorCmd","doors.sliding.left.close");
  actions.put("SendDoubleDoorLeftCloseCmd","doors.double.left.close");
  actions.put("SendSlideOpenRightDoorCmd","doors.sliding.right.open");
  actions.put("SendDoubleDoorRightOpenCmd","doors.double.right.open");
  actions.put("SendSlideCloseRightDoorCmd","doors.sliding.right.close");
  actions.put("SendDoubleDoorRightCloseCmd","doors.double.right.close");
 }
 static void note(Context c,String s){new Handler(Looper.getMainLooper()).post(()->Toast.makeText(c,s,Toast.LENGTH_LONG).show());}
 public static boolean dispatch(Object command){String action=command==null?null:actions.get(command.getClass().getSimpleName());if(action==null)return false;return send(action,null);}
 static boolean send(String action,JSONObject parameters){AccountGate.ensureContext();Context c=AccountGate.context;if(c==null)return true;if(!AccountGate.logged()){note(c,"Войдите в аккаунт");return true;}if(parameters==null&&!busy.compareAndSet(false,true)){note(c,"Дождитесь ответа сервера");return true;}
 final String key=UUID.randomUUID().toString();worker.execute(()->{try{JSONArray all=ControlBridge.api(c,"GET","/v1/vehicles",null).getJSONArray("vehicles");JSONObject selected=null;for(int i=0;i<all.length();i++){JSONObject b=all.getJSONObject(i);if("active".equals(b.optString("state"))){if(selected!=null)throw new Exception("Сначала выберите автомобиль");selected=b;}}if(selected==null)throw new Exception("Сначала привяжите автомобиль по QR");JSONObject result=ControlBridge.api(c,"POST","/v1/bindings/"+selected.getString("binding_id")+"/commands",new JSONObject().put("action",action).put("parameters",parameters).put("idempotency_key",key));android.util.Log.i("OwnServerCommand",action+" "+result.optString("command_id")+" "+result.optString("state"));note(c,"Команда принята сервером.");}catch(Exception e){android.util.Log.w("OwnServerCommand",action+" failed: "+e.getClass().getSimpleName());note(c,e instanceof LabHttp.Failure?"Сервер отклонил команду":e.getMessage()==null?"Отправка не подтверждена":e.getMessage());}finally{if(parameters==null)busy.set(false);}});return true;
 }
}
