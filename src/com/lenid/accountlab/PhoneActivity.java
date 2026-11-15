package com.lenid.accountlab;
import android.app.*;import android.os.*;import android.content.*;import android.graphics.*;import android.graphics.drawable.*;import android.text.InputType;import android.view.*;import android.widget.*;import org.json.*;import java.io.*;import java.util.concurrent.*;
public final class PhoneActivity extends Activity {
 LabHttp http;SharedPreferences prefs;ExecutorService worker=Executors.newSingleThreadExecutor();Handler timer=new Handler();LinearLayout root,box;EditText email,otp,name,password;String emailValue="",challenge="",notice="",tab="profile";volatile JSONObject me,vehicle;String pairingId="",pairingPhrase="";volatile boolean busy=false;int INK=0xff232323;
 VehicleScanner scanner;
 boolean startup, resumed, openingVehicle;
 interface Work{void run()throws Exception;}
 protected void onResume(){super.onResume();resumed=true;if(scanner!=null){scanner.resume();return;}routeAfterSync();if(!openingVehicle)ReleaseUpdate.check(this);}
 protected void onPause(){resumed=false;if(scanner!=null)scanner.pause();super.onPause();}
 private void routeAfterSync(){
     if(!startup || !resumed || busy || openingVehicle || me==null || !logged() || isFinishing() || isDestroyed())return;
     try {
         if(AccountGate.hasActiveVehicle(me)){
             openingVehicle=true;
             clearPendingPair();
             AccountGate.openVehicle(this);
         }
     } catch(JSONException e){notice="Не удалось прочитать список автомобилей";}
 }
 private void leaveSection(){
     if(startup || !logged())finishAffinity();
     else finish();
 }
 @Override public void onBackPressed(){if(scanner!=null)closeScanner();else leaveSection();}
 private void rememberPair(String id,String phrase) throws JSONException {
     prefs.edit().putString("pending_pair_owner",me.getJSONObject("profile").getString("user_id"))
         .putString("pending_pair_id",id).putString("pending_pair_phrase",phrase).commit();
     pairingId=id;pairingPhrase=phrase;
 }
 private void clearPendingPair(){
     prefs.edit().remove("pending_pair_owner").remove("pending_pair_id").remove("pending_pair_phrase").apply();
     pairingId="";pairingPhrase="";
 }
 public void onCreate(Bundle b){super.onCreate(b);
  setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
  tab=getIntent().getStringExtra("section");startup=tab==null;if(startup)tab="vehicles";getWindow().setStatusBarColor(0xfff6f7f8);getWindow().setNavigationBarColor(0xffffffff);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);prefs=getSharedPreferences("phone",0);AccountGate.init(this);emailValue=prefs.getString("email","");try{http=new LabHttp(this);}catch(Exception e){notice="Сервис временно недоступен";}if(http!=null&&logged())run(this::sync);else draw();timer.postDelayed(pairPoll,3000);}
 boolean logged(){return !prefs.getString("access","").isEmpty();}
 void tokens(JSONObject t)throws Exception{prefs.edit().putString("access",t.getString("access_token")).putString("refresh",t.getString("refresh_token")).putString("email",emailValue).commit();}
 synchronized JSONObject api(String method,String path,JSONObject body)throws Exception{if(http==null)throw new IOException("Сначала подключите сервер");try{return http.call(8768,method,path,body,prefs.getString("access",""));}catch(LabHttp.Failure e){if(e.status!=401||!logged())throw e;JSONObject fresh;try{fresh=http.call(8768,"POST","/v1/auth/refresh",new JSONObject().put("refresh_token",prefs.getString("refresh","")),null);}catch(LabHttp.Failure f){if(f.status==401){AccountGate.clear(this);prefs.edit().remove("photo_uri").remove("photo_id").remove("last_vehicle").apply();me=null;vehicle=null;}throw f;}tokens(fresh);return http.call(8768,method,path,body,prefs.getString("access",""));}}
 void sync()throws Exception{
 JSONObject current=api("GET","/v1/me",null);AccountGate.save(this,current);me=current;
 JSONObject profile=current.getJSONObject("profile");
 if(profile.getString("user_id").equals(prefs.getString("pending_pair_owner",""))){
     pairingId=prefs.getString("pending_pair_id","");pairingPhrase=prefs.getString("pending_pair_phrase","");
 }else if(!pairingId.isEmpty()||prefs.contains("pending_pair_owner"))clearPendingPair();
 String id=profile.optString("avatar_object_id","");if(id.equals("null"))id="";if(id.isEmpty()){prefs.edit().remove("photo_uri").remove("photo_id").apply();}else if(!id.equals(prefs.getString("photo_id",""))||!new File(getFilesDir(),"avatar-"+id+".png").exists()){JSONObject photo=api("GET","/v1/me/avatar",null);byte[] data=android.util.Base64.decode(photo.getString("image_base64"),android.util.Base64.DEFAULT);File f=new File(getFilesDir(),"avatar-"+id+".png");try(FileOutputStream out=new FileOutputStream(f)){out.write(data);}prefs.edit().putString("photo_id",id).putString("photo_uri",android.net.Uri.fromFile(f).toString()).commit();}prefs.edit().remove("last_vehicle").apply();}
 void run(Work work){if(busy)return;busy=true;notice="Подождите…";draw();worker.execute(()->{try{work.run();if(notice.equals("Подождите…"))notice="";}catch(Exception e){notice=human(e);}finally{runOnUiThread(()->{busy=false;if(isFinishing()||isDestroyed())return;routeAfterSync();if(!openingVehicle)draw();});}});}
 String human(Exception e){if(e instanceof LabHttp.Failure){String code=((LabHttp.Failure)e).code;if(code.equals("credentials_invalid"))return "Неверный логин или пароль";if(code.equals("login_taken"))return "Этот логин уже занят";if(code.equals("login_invalid"))return "Логин: 3–32 латинские буквы, цифры, точка, дефис или _";if(code.equals("password_invalid"))return "Пароль должен содержать от 10 до 128 символов";if(code.equals("code_invalid"))return "Неверный или истёкший код";if(code.equals("email_delivery_failed"))return "Сервер не отправил письмо. Проверьте Gmail на компьютере.";if(code.equals("rate_limited"))return "Слишком много попыток. Подождите минуту.";if(code.equals("revision_conflict"))return "Профиль изменён на другом устройстве. Обновите его и повторите.";if(code.equals("phone_session_invalid")||code.startsWith("refresh_"))return "Сессия завершена. Войдите снова.";}if(e instanceof javax.net.ssl.SSLException)return "Не удалось подтвердить сертификат сервера. Проверьте дату устройства и подключение к серверу.";return "Ошибка: "+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage());}
 int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
 TextView text(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextColor(INK);t.setTextSize(size);t.setPadding(0,dp(8),0,dp(8));box.addView(t);return t;}
 Button button(String s,Runnable action){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setEnabled(!busy);box.addView(b,new LinearLayout.LayoutParams(-1,dp(52)));b.setOnClickListener(v->action.run());return b;}
 EditText field(String hint,String value,int type){EditText t=new EditText(this);t.setHint(hint);t.setContentDescription(hint);t.setText(value);t.setSingleLine();t.setInputType(type);box.addView(t,new LinearLayout.LayoutParams(-1,dp(56)));return t;}
 void draw(){if(scanner!=null)return;root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(0xfff6f7f8);ScrollView sc=new ScrollView(this);box=new LinearLayout(this);box.setOrientation(1);box.setPadding(dp(22),dp(18),dp(22),dp(24));sc.addView(box);root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);root.setOnApplyWindowInsetsListener((v,insets)->{int[] xy=new int[2];v.getLocationOnScreen(xy);int top=insets.getSystemWindowInsetTop();v.setPadding(0,Math.max(0,top-xy[1]),0,insets.getSystemWindowInsetBottom());return insets;});root.requestApplyInsets();
 if(http==null){text("Сервис временно недоступен",26);text("Попробуйте открыть приложение позже.",16);}
 else if(!logged()){text("Вход в аккаунт",28);email=field("Логин",emailValue,InputType.TYPE_CLASS_TEXT);password=field("Пароль","",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);text("Логин: 3–32 латинские буквы, цифры, точка, дефис или _. Пароль: от 10 символов.",14);button("Войти",()->passwordAuth(false));button("Создать аккаунт",()->passwordAuth(true));}

 else if(startup && me==null){
     if(!busy)action("Повторить",true,()->run(this::sync));
 }
 else{header(tab.equals("vehicles")?"Мои автомобили":"Мой аккаунт");if(tab.equals("vehicles"))pairingScreen();else profileScreen();}

 if(!notice.isEmpty())text(notice,14);
 }
 void passwordAuth(boolean register){String login=email.getText().toString().trim(),secret=password.getText().toString();emailValue=login;run(()->{tokens(http.call(8768,"POST",register?"/v1/auth/register":"/v1/auth/login",new JSONObject().put("login",login).put("password",secret),null));startup=true;tab="vehicles";me=null;sync();notice=register?"Аккаунт создан":"Вход выполнен";});}
 android.graphics.drawable.GradientDrawable shape(int color,int border,int radius){android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));if(border!=0)d.setStroke(dp(1),border);return d;}
 void gap(int height){View v=new View(this);box.addView(v,new LinearLayout.LayoutParams(1,dp(height)));}
 void header(String title){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);TextView back=new TextView(this);back.setText("‹");back.setTextSize(36);back.setTextColor(INK);back.setContentDescription("Назад");row.addView(back,new LinearLayout.LayoutParams(dp(42),dp(54)));back.setOnClickListener(v->leaveSection());TextView t=new TextView(this);t.setText(title);t.setTextSize(23);t.setTypeface(null,android.graphics.Typeface.BOLD);t.setTextColor(INK);row.addView(t);box.addView(row);gap(20);}
 void action(String label,boolean primary,Runnable click){Button b=button(label,click);b.setTextSize(16);b.setTypeface(null,android.graphics.Typeface.BOLD);b.setTextColor(primary?Color.WHITE:INK);b.setBackgroundTintList(null);b.setBackground(shape(primary?INK:Color.WHITE,primary?0:0xffdddddd,8));LinearLayout.LayoutParams lp=(LinearLayout.LayoutParams)b.getLayoutParams();lp.topMargin=dp(10);lp.bottomMargin=dp(4);b.setLayoutParams(lp);}
 void caption(String value){TextView t=text(value,14);t.setTextColor(0xff777777);}
 LinearLayout card(){LinearLayout outer=box;LinearLayout c=new LinearLayout(this);c.setOrientation(1);c.setPadding(dp(18),dp(14),dp(18),dp(18));c.setBackground(shape(Color.WHITE,0,12));outer.addView(c,new LinearLayout.LayoutParams(-1,-2));box=c;return outer;}
 void profileScreen(){LinearLayout outer=card();ImageView photo=new ImageView(this);photo.setContentDescription("Фото профиля");photo.setScaleType(ImageView.ScaleType.CENTER_CROP);photo.setBackground(shape(0xffeeeeee,0,48));photo.setClipToOutline(true);if(prefs.contains("photo_uri"))photo.setImageURI(android.net.Uri.parse(prefs.getString("photo_uri","")));else photo.setImageResource(android.R.drawable.ic_menu_myplaces);LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(dp(96),dp(96));pp.gravity=Gravity.CENTER_HORIZONTAL;pp.topMargin=dp(8);pp.bottomMargin=dp(16);box.addView(photo,pp);action("Изменить фото",false,()->startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE),61));gap(16);caption("Логин");text(emailValue,18);caption("Имя");String value=me==null?"":me.optJSONObject("profile").optString("display_name","");name=field("Имя профиля",value,InputType.TYPE_CLASS_TEXT);action("Сохранить изменения",true,()->{String n=name.getText().toString().trim();if(n.isEmpty()){notice="Введите имя";draw();return;}run(()->{JSONObject p=api("GET","/v1/me",null).getJSONObject("profile");api("PATCH","/v1/me/profile",new JSONObject().put("expected_revision",p.getInt("revision")).put("display_name",n));sync();notice="Изменения сохранены";});});box=outer;gap(20);action("Выйти из аккаунта",false,()->new AlertDialog.Builder(this).setTitle("Выйти из аккаунта?").setMessage("Для следующего входа понадобятся логин и пароль.").setNegativeButton("Отмена",null).setPositiveButton("Выйти",(d,w)->run(()->{api("POST","/v1/auth/logout",new JSONObject());AccountGate.clear(this);prefs.edit().remove("photo_uri").remove("photo_id").remove("last_vehicle").apply();me=null;vehicle=null;clearPendingPair();startup=true;tab="vehicles";notice="Вы вышли из аккаунта";})).show());
  action("Удалить аккаунт",false,this::confirmAccountDeletion);
 }
 void confirmAccountDeletion(){
  EditText secret=new EditText(this);
  secret.setSingleLine();
  secret.setHint("Пароль аккаунта");
  secret.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
  LinearLayout wrap=new LinearLayout(this);
  wrap.setPadding(dp(24),dp(8),dp(24),0);
  wrap.addView(secret,new LinearLayout.LayoutParams(-1,dp(56)));
  new AlertDialog.Builder(this).setTitle("Удалить аккаунт "+emailValue+"?")
   .setMessage("Профиль, фото, настройки и история команд будут удалены. Автомобили будут отвязаны. Для подтверждения введите пароль аккаунта.")
   .setView(wrap).setNegativeButton("Отмена",null).setPositiveButton("Удалить",(dialog,which)->{
    String value=secret.getText().toString();
    if(value.isEmpty()){notice="Введите пароль аккаунта";draw();return;}
    run(()->{
     JSONObject result=api("POST","/v1/me/delete",new JSONObject().put("password",value));
     if(!result.getBoolean("deleted"))throw new IOException("Удаление не подтверждено");
     prefs.edit().clear().commit();
     File[] avatars=getFilesDir().listFiles((dir,n)->n.matches("avatar-[a-f0-9]{32}\\.png"));
     if(avatars!=null)for(File avatar:avatars)avatar.delete();
     emailValue="";me=null;vehicle=null;clearPendingPair();notice="Аккаунт удалён";
    });
   }).show();
 }

 void pairingScreen(){JSONArray list=me==null?new JSONArray():me.optJSONArray("vehicles");int count=0;if(list!=null)for(int i=0;i<list.length();i++){JSONObject v=list.optJSONObject(i);if(!v.optString("state").equals("active"))continue;count++;LinearLayout outer=card();text(v.optString("label","Автомобиль"),22);caption("Привязан к вашему аккаунту");final String id=v.optString("binding_id");action("История команд",false,()->ControlBridge.history(this,id));action("Отвязать автомобиль",false,()->confirmUnbind(id));box=outer;gap(16);}if(count==0){LinearLayout outer=card();text("Добавьте автомобиль",23);caption("Отсканируйте QR-код на его экране и подтвердите привязку на обоих устройствах.");box=outer;gap(16);}if(pairingId.isEmpty())action("Привязать по QR-коду",true,this::openScanner);if(!pairingId.isEmpty()){gap(18);LinearLayout outer=card();caption("Сверьте фразу на двух экранах");text(pairingPhrase,23);caption("Подтвердите привязку на экране автомобиля.");action("Отменить привязку",false,()->run(()->{api("POST","/v1/pairings/"+pairingId+"/cancel",new JSONObject());clearPendingPair();notice="Привязка отменена";}));box=outer;}gap(12);action("Обновить список",false,()->run(()->sync()));
 if(startup)action("Мой аккаунт",false,()->startActivity(new Intent(this,PhoneActivity.class).putExtra("section","profile")));
 }
 void confirmUnbind(String id){EditText secret=new EditText(this);secret.setHint("Пароль аккаунта");secret.setSingleLine();secret.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);LinearLayout wrap=new LinearLayout(this);wrap.setPadding(dp(24),dp(8),dp(24),0);wrap.addView(secret,new LinearLayout.LayoutParams(-1,dp(56)));new AlertDialog.Builder(this).setTitle("Отвязать автомобиль?").setMessage("Автомобиль потеряет доступ к вашему профилю. Для повторного подключения понадобится QR-код. Подтвердите действие паролем.").setView(wrap).setNegativeButton("Отмена",null).setPositiveButton("Отвязать",(d,w)->{String value=secret.getText().toString();run(()->{tokens(http.call(8768,"POST","/v1/auth/login",new JSONObject().put("login",emailValue).put("password",value),null));api("POST","/v1/bindings/"+id+"/revoke",new JSONObject().put("idempotency_key",java.util.UUID.randomUUID().toString()));sync();notice="Автомобиль отвязан";});}).show();}
 void openScanner(){
     if(scanner!=null||busy||me==null)return;
     scanner=new VehicleScanner(this);
     scanner.resume();
     if(checkSelfPermission(android.Manifest.permission.CAMERA)!=android.content.pm.PackageManager.PERMISSION_GRANTED
         && !prefs.getBoolean("scanner_permission_answered",false))scanner.requestCamera();
 }
 void closeScanner(){
     if(scanner==null)return;
     scanner.pause();scanner=null;draw();
 }
 @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results){
     super.onRequestPermissionsResult(code,permissions,results);
     if(code==VehicleScanner.CAMERA_REQUEST&&results.length>0){
         prefs.edit().putBoolean("scanner_permission_answered",true).apply();
         if(scanner!=null)scanner.resume();
     }
 }
 void reviewQR(String scanned){try{JSONObject q=new JSONObject(scanned);if(scanned.length()>4096||q.getInt("version")!=1||!q.getString("origin").equals(http.origin)||!q.getString("pairing_id").matches("[a-f0-9]{32}")||q.getLong("expires_at")*1000<System.currentTimeMillis()||q.getString("pairing_secret").length()<20)throw new Exception("Неверный или истёкший QR-код");new AlertDialog.Builder(this).setTitle("Привязать автомобиль?").setMessage("Продолжайте, только если этот QR-код показан на экране вашего автомобиля.").setNegativeButton("Отмена",null).setPositiveButton("Продолжить",(d,w)->run(()->{String id=q.getString("pairing_id"),key=prefs.getString("claim_key_"+id,"");if(key.isEmpty()){key=java.util.UUID.randomUUID().toString();prefs.edit().putString("claim_key_"+id,key).commit();}JSONObject r=api("POST","/v1/pairings/"+id+"/claim",new JSONObject().put("pairing_secret",q.getString("pairing_secret")).put("idempotency_key",key));rememberPair(id,r.getString("confirmation_phrase"));notice="Ожидаем подтверждение на автомобиле";})).show();}catch(Exception e){notice="Этот QR-код не подходит или уже истёк. Обновите код на экране автомобиля.";draw();}}
 final Runnable pairPoll=new Runnable(){public void run(){if(resumed&&logged()&&!busy&&!pairingId.isEmpty())PhoneActivity.this.run(()->{JSONObject p=api("GET","/v1/me/pairings/"+pairingId,null);String state=p.getString("state");if(state.equals("bound")){sync();clearPendingPair();notice="Автомобиль привязан";}else if(state.equals("expired")||state.equals("cancelled")){clearPendingPair();notice="Привязка завершена без подключения. Создайте новый QR-код.";}});timer.postDelayed(this,3000);}};
 Bitmap decodePhoto(android.net.Uri uri)throws Exception{if(Build.VERSION.SDK_INT>=28)return ImageDecoder.decodeBitmap(ImageDecoder.createSource(getContentResolver(),uri),(decoder,info,source)->{float scale=Math.min(1f,512f/Math.max(info.getSize().getWidth(),info.getSize().getHeight()));decoder.setTargetSize(Math.max(1,(int)(info.getSize().getWidth()*scale)),Math.max(1,(int)(info.getSize().getHeight()*scale)));decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);});BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;try(InputStream in=getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(in,null,o);}if(o.outWidth<=0||o.outHeight<=0)throw new IOException("Не удалось прочитать фото");o.inJustDecodeBounds=false;o.inSampleSize=1;while(Math.max(o.outWidth,o.outHeight)/o.inSampleSize>512)o.inSampleSize*=2;Bitmap b;try(InputStream in=getContentResolver().openInputStream(uri)){b=BitmapFactory.decodeStream(in,null,o);}if(b==null)throw new IOException("Не удалось прочитать фото");try(InputStream in=getContentResolver().openInputStream(uri)){int orientation=new android.media.ExifInterface(in).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1);Matrix m=new Matrix();switch(orientation){case 2:m.setScale(-1,1);break;case 3:m.setRotate(180);break;case 4:m.setScale(1,-1);break;case 5:m.setRotate(90);m.postScale(-1,1);break;case 6:m.setRotate(90);break;case 7:m.setRotate(-90);m.postScale(-1,1);break;case 8:m.setRotate(-90);break;}if(!m.isIdentity()){Bitmap rotated=Bitmap.createBitmap(b,0,0,b.getWidth(),b.getHeight(),m,true);if(rotated!=b)b.recycle();b=rotated;}}catch(IOException ignored){}return b;}
 protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null)return;if(data.getData()==null)return;android.net.Uri uri=data.getData();if(request==61)run(()->{Bitmap b=decodePhoto(uri);ByteArrayOutputStream out=new ByteArrayOutputStream();b.compress(Bitmap.CompressFormat.JPEG,85,out);b.recycle();JSONObject a=api("POST","/v1/me/avatar",new JSONObject().put("image_base64",android.util.Base64.encodeToString(out.toByteArray(),android.util.Base64.NO_WRAP)));JSONObject p=api("GET","/v1/me",null).getJSONObject("profile");String n=p.optString("display_name","");if(n.trim().isEmpty())throw new IOException("Сначала сохраните имя профиля");api("PATCH","/v1/me/profile",new JSONObject().put("display_name",n).put("expected_revision",p.getInt("revision")).put("avatar_object_id",a.getString("avatar_object_id")));sync();notice="Фото сохранено на сервере";});}
 protected void onDestroy(){timer.removeCallbacksAndMessages(null);worker.shutdown();super.onDestroy();}
}
