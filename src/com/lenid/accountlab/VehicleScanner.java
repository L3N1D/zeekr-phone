package com.lenid.accountlab;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.ResultPoint;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.BarcodeView;
import com.journeyapps.barcodescanner.CameraPreview;
import com.journeyapps.barcodescanner.DefaultDecoderFactory;
import java.util.Collections;
import java.util.List;

final class VehicleScanner implements BarcodeCallback {
    static final int CAMERA_REQUEST = 64;
    private final PhoneActivity activity;
    private final BarcodeView preview;
    private final Finder finder;
    private final TextView hint, retry;
    private boolean active;

    VehicleScanner(PhoneActivity activity) {
        this.activity = activity;
        LinearLayout page = new LinearLayout(activity);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Color.WHITE);
        page.setFitsSystemWindows(true);
        LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = label("‹", 36);
        back.setGravity(Gravity.CENTER);
        back.setContentDescription("Назад");
        back.setOnClickListener(v -> activity.closeScanner());
        header.addView(back, dp(56), -1);
        TextView title = label("Сканировать QR-код", 22);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.addView(title);
        page.addView(header, -1, dp(64));

        FrameLayout camera = new FrameLayout(activity);
        camera.setBackgroundColor(0xff161719);
        preview = new BarcodeView(activity);
        preview.setUseTextureView(true);
        preview.setDecoderFactory(new DefaultDecoderFactory(Collections.singleton(BarcodeFormat.QR_CODE)));
        camera.addView(preview);
        finder = new Finder();
        camera.addView(finder);
        page.addView(camera, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout footer = new LinearLayout(activity);
        footer.setOrientation(LinearLayout.VERTICAL);
        footer.setPadding(dp(24), dp(20), dp(24), dp(24));
        hint = label("", 17);
        footer.addView(hint);
        retry = label("", 16);
        retry.setGravity(Gravity.CENTER);
        retry.setTextColor(Color.WHITE);
        GradientDrawable pill = new GradientDrawable();
        pill.setColor(0xff252629);
        pill.setCornerRadius(dp(14));
        retry.setBackground(pill);
        LinearLayout.LayoutParams button = new LinearLayout.LayoutParams(-1, dp(48));
        button.topMargin = dp(12);
        footer.addView(retry, button);
        page.addView(footer);
        preview.addStateListener(new CameraPreview.StateListener() {
            public void previewSized() { finder.invalidate(); }
            public void previewStarted() { finder.invalidate(); }
            public void previewStopped() {}
            public void cameraClosed() {}
            public void cameraError(Exception failure) {
                if (activity.scanner != VehicleScanner.this || !activity.resumed) return;
                pause();
                message("Не удалось открыть камеру.", "Повторить", () -> resume());
            }
        });
        activity.setContentView(page);
        message("Наведите камеру на QR-код на экране автомобиля.", null, null);
    }

    private TextView label(String text, int size) {
        TextView view = new TextView(activity);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(0xff232323);
        return view;
    }
    private int dp(int value) { return activity.dp(value); }

    private void message(String text, String action, Runnable click) {
        hint.setText(text);
        retry.setText(action);
        retry.setVisibility(click == null ? View.GONE : View.VISIBLE);
        retry.setOnClickListener(click == null ? null : v -> click.run());
    }

    void requestCamera() {
        activity.requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST);
    }

    void resume() {
        if (active || !activity.resumed) return;
        if (activity.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            boolean settings = activity.prefs.getBoolean("scanner_permission_answered", false)
                && !activity.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA);
            message("Для сканирования нужен доступ к камере.", settings ? "Открыть настройки" : "Разрешить камеру",
                settings ? () -> activity.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", activity.getPackageName(), null))) : this::requestCamera);
            return;
        }
        active = true;
        message("Наведите камеру на QR-код на экране автомобиля.", null, null);
        preview.setKeepScreenOn(true);
        preview.resume();
        preview.decodeSingle(this);
    }

    void pause() {
        active = false;
        preview.setKeepScreenOn(false);
        preview.stopDecoding();
        preview.pause();
    }

    public void barcodeResult(BarcodeResult result) {
        if (activity.scanner != this || !activity.resumed || result == null) return;
        activity.closeScanner();
        activity.reviewQR(result.getText());
    }
    public void possibleResultPoints(List<ResultPoint> points) {}

    private final class Finder extends View {
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Path corners = new Path();
        Finder() { super(activity); }
        @Override protected void onDraw(Canvas c) {
            Rect frame = preview.getFramingRect();
            if (frame == null) return;
            int l = frame.left, r = frame.right, t = frame.top, b = frame.bottom;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0x88000000);
            c.drawRect(0, 0, getWidth(), t, paint);
            c.drawRect(0, b, getWidth(), getHeight(), paint);
            c.drawRect(0, t, l, b, paint);
            c.drawRect(r, t, getWidth(), b, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(Color.WHITE);
            paint.setStrokeWidth(dp(3));
            paint.setStrokeCap(Paint.Cap.ROUND);
            corners.reset();
            corner(l, t, 1, 1);
            corner(r, t, -1, 1);
            corner(l, b, 1, -1);
            corner(r, b, -1, -1);
            c.drawPath(corners, paint);
        }
        void corner(int x, int y, int dx, int dy) {
            int length = dp(24), radius = dp(7);
            corners.moveTo(x + dx * length, y);
            corners.lineTo(x + dx * radius, y);
            corners.quadTo(x, y, x, y + dy * radius);
            corners.lineTo(x, y + dy * length);
        }
    }
}
