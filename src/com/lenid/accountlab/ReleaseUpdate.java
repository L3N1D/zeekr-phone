package com.lenid.accountlab;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.HttpsURLConnection;

public final class ReleaseUpdate {
    private static final String RELEASES = "https://github.com/L3N1D/zeekr-phone/releases/";
    private static final ExecutorService worker = Executors.newSingleThreadExecutor();
    private static long lastCheck;
    private static boolean running;

    // Called on the UI thread whenever either entry screen resumes.
    public static void check(Activity screen) {
        final SharedPreferences state = screen.getSharedPreferences("release-update", Context.MODE_PRIVATE);
        long download = state.getLong("download", -1);
        long now = android.os.SystemClock.elapsedRealtime();
        if (running || (download < 0 && lastCheck != 0 && now-lastCheck < 60000)) return;
        running = true;
        lastCheck = now;
        worker.execute(() -> {
            try {
                long installed = screen.getPackageManager().getPackageInfo(screen.getPackageName(), 0).versionCode;
                if (download >= 0 && installed >= state.getLong("build", 0)) {
                    clearDownload(screen, state, download);
                } else if (download >= 0) {
                    finishDownload(screen, state, download);
                } else {
                    JSONObject release = latest();
                    long build = release.getLong("build");
                    String hash = release.getString("sha256");
                    if (build <= installed || build > Integer.MAX_VALUE || !hash.matches("[0-9a-f]{64}")) return;
                    runVisible(screen, () -> new AlertDialog.Builder(screen)
                        .setTitle("Обновление приложения")
                        .setMessage("Доступна сборка " + build + ". Скачать обновление (" +
                            (release.optLong("size") / 1048576) + " МБ)?")
                        .setNegativeButton("Позже", null)
                        .setPositiveButton("Скачать", (d,w) -> start(screen,state,build,hash)).show());
                }
            } catch (Exception error) {
                Log.w("ZeekrUpdate","Update check unavailable",error);
            } finally {
                screen.runOnUiThread(() -> running=false);
            }
        });
    }
    private static JSONObject latest() throws Exception {
        HttpsURLConnection c=(HttpsURLConnection)new URL(RELEASES+"latest/download/phone-update.json").openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(12000);
        try (InputStream in=c.getInputStream(); ByteArrayOutputStream data=new ByteArrayOutputStream()) {
            byte[] buf=new byte[2048];
            int n;
            while ((n=in.read(buf))!=-1) {
                if (data.size()+n>16384) throw new java.io.IOException("Manifest too large");
                data.write(buf,0,n);
            }
            return new JSONObject(data.toString("UTF-8"));
        } finally { c.disconnect(); }
    }
    private static void runVisible(Activity screen,Runnable action) {
        screen.runOnUiThread(() -> {
            if (!screen.isDestroyed() && !screen.isFinishing() && screen.hasWindowFocus()) action.run();
        });
    }
    private static void start(Activity screen,SharedPreferences state,long build,String hash) {
        try {
            DownloadManager manager=screen.getSystemService(DownloadManager.class);
            DownloadManager.Request request=new DownloadManager.Request(Uri.parse(RELEASES+"download/build-"+build+"/Zeekr-Phone.apk"))
                .setTitle("Обновление ZEEKR").setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setDestinationInExternalFilesDir(screen,Environment.DIRECTORY_DOWNLOADS,"zeekr-"+build+"-"+System.currentTimeMillis()+".apk");
            long id=manager.enqueue(request);
            state.edit().putLong("download",id).putLong("build",build).putString("hash",hash).commit();
            poll(screen);
        } catch (RuntimeException e) { Toast.makeText(screen,"Не удалось начать загрузку",Toast.LENGTH_LONG).show(); }
    }
    private static void poll(Activity screen) {
        screen.getWindow().getDecorView().postDelayed(() -> {
            if (!screen.isFinishing() && !screen.isDestroyed() && screen.hasWindowFocus()) check(screen);
        },3000);
    }
    private static void clearDownload(Activity screen,SharedPreferences state,long id) {
        screen.getSystemService(DownloadManager.class).remove(id);
        state.edit().clear().commit();
    }
    private static void finishDownload(Activity screen,SharedPreferences state,long id) throws Exception {
        DownloadManager manager=screen.getSystemService(DownloadManager.class);
        int status=DownloadManager.STATUS_FAILED;
        try (Cursor row=manager.query(new DownloadManager.Query().setFilterById(id))) {
            if (row!=null && row.moveToFirst()) status=row.getInt(row.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
        }
        if (status==DownloadManager.STATUS_FAILED) {
            clearDownload(screen,state,id);
            runVisible(screen,() -> Toast.makeText(screen,"Загрузка обновления прервана",Toast.LENGTH_LONG).show());
            return;
        }
        if (status!=DownloadManager.STATUS_SUCCESSFUL) {
            screen.runOnUiThread(() -> poll(screen));
            return;
        }
        Uri apk=manager.getUriForDownloadedFile(id);
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try (InputStream in=screen.getContentResolver().openInputStream(apk)) {
            byte[] buf=new byte[131072];int n;
            while ((n=in.read(buf))!=-1) digest.update(buf,0,n);
        }
        StringBuilder hex=new StringBuilder();
        for (byte b:digest.digest()) hex.append(String.format(Locale.ROOT,"%02x",b&255));
        if (!hex.toString().equals(state.getString("hash",""))) {
            clearDownload(screen,state,id);
            runVisible(screen,() -> Toast.makeText(screen,"Файл обновления не прошёл проверку",Toast.LENGTH_LONG).show());
            return;
        }
        runVisible(screen,() -> new AlertDialog.Builder(screen).setTitle("Обновление загружено")
            .setMessage("Открыть установщик Android?").setNegativeButton("Позже",null)
            .setPositiveButton("Установить",(d,w) -> {
                try {
                    Intent intent=screen.getPackageManager().canRequestPackageInstalls()
                        ? new Intent(Intent.ACTION_VIEW).setDataAndType(apk,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        : new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+screen.getPackageName()));
                    screen.startActivity(intent);
                } catch (RuntimeException e) { Toast.makeText(screen,"Не удалось открыть установщик",Toast.LENGTH_LONG).show(); }
            }).show());
    }
}
