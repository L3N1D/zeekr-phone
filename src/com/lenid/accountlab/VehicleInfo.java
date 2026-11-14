package com.lenid.accountlab;
import android.app.*;import android.view.*;import android.widget.*;import android.graphics.drawable.GradientDrawable;import org.json.*;import java.util.*;import java.util.concurrent.*;
public final class VehicleInfo {
 static final WeakHashMap<Activity,State> states=new WeakHashMap<>();
 static final ExecutorService worker=Executors.newSingleThreadExecutor();
 static class State{LinearLayout card;TextView text;boolean busy;long next;}
 static int dp(Activity a,int n){return (int)(n*a.getResources().getDisplayMetrics().density+.5f);}
 public static void attach(Activity a){
  View car=a.findViewById(a.getResources().getIdentifier("car_model","id",a.getPackageName()));
  if(car==null)return;
  View root=(View)car.getParent();View scroll=root.findViewById(a.getResources().getIdentifier("scroll_view","id",a.getPackageName()));
  if(!(scroll instanceof ViewGroup)||((ViewGroup)scroll).getChildCount()==0)return;
  View content=((ViewGroup)scroll).getChildAt(0);if(!(content instanceof LinearLayout))return;
  State s=states.get(a);if(s==null){s=new State();states.put(a,s);}
  if(s.card==null||s.card.getParent()!=content){
   s.card=new LinearLayout(a);s.card.setOrientation(1);s.card.setPadding(dp(a,20),dp(a,18),dp(a,20),dp(a,18));
   GradientDrawable bg=new GradientDrawable();bg.setColor(0xffffffff);bg.setCornerRadius(dp(a,16));s.card.setBackground(bg);
   TextView title=new TextView(a);title.setText("Об автомобиле");title.setTextSize(18);title.setTextColor(0xff232323);s.card.addView(title);
   s.text=new TextView(a);s.text.setTextSize(14);s.text.setTextColor(0xff62666b);s.text.setPadding(0,dp(a,12),0,0);s.text.setText("VIN: нет данных\nПробег: нет данных");s.card.addView(s.text);
   LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(dp(a,24),dp(a,12),dp(a,24),dp(a,12));((LinearLayout)content).addView(s.card,0,lp);
   State state=s;s.card.setOnClickListener(v->{state.next=0;fetch(a,state);});s.next=0;
  }
  if(car.isShown())fetch(a,s);
 }
 static void fetch(Activity a,State s){if(s.busy||System.currentTimeMillis()<s.next||!AccountGate.logged())return;s.busy=true;s.next=System.currentTimeMillis()+30000;
  worker.execute(()->{String result;try{JSONArray rows=ControlBridge.api(a,"GET","/v1/vehicles",null).getJSONArray("vehicles");StringBuilder b=new StringBuilder();for(int i=0;i<rows.length();i++){JSONObject v=rows.getJSONObject(i);if(!"active".equals(v.optString("state")))continue;if(b.length()>0)b.append("\n\n");if(rows.length()>1)b.append(v.optString("label","Автомобиль")).append('\n');String vin=v.isNull("vin")?"":v.optString("vin","");b.append("VIN: ").append(vin.isEmpty()?"нет данных":vin).append("\nПробег: ");if(v.isNull("odometer_km"))b.append("нет данных");else{b.append(java.text.NumberFormat.getNumberInstance(new Locale("ru","RU")).format(v.getDouble("odometer_km"))).append(" км");if(!v.isNull("observed_at"))b.append("\nДанные на ").append(new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm",new Locale("ru","RU")).format(new Date(v.getLong("observed_at")*1000)));}}
   result=b.length()==0?"Автомобиль ещё не привязан":b.toString();
  }catch(Exception e){result="Не удалось получить данные\nНажмите, чтобы повторить";}
  final String value=result;a.runOnUiThread(()->{s.busy=false;if(!a.isDestroyed()&&AccountGate.logged()&&!value.contentEquals(s.text.getText()))s.text.setText(value);});
  });
 }
}
