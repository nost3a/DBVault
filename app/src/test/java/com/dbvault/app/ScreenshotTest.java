package com.dbvault.app;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowLooper;

import java.io.File;
import java.io.FileOutputStream;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ScreenshotTest {

    @Test
    public void shotMain() throws Exception {
        android.app.Activity a = Robolectric.buildActivity(com.dbvault.app.ui.MainActivity.class)
                .setup().get();
        settle(a);
        shoot(a, "/tmp/shot_main.png");
    }

    private void settle(android.app.Activity a) {
        for (int i = 0; i < 40; i++) {
            ShadowLooper.idleMainLooper();
            try {
                Thread.sleep(25);
            } catch (InterruptedException ignored) {
            }
        }
        ShadowLooper.idleMainLooper();
    }

    private void shoot(android.app.Activity a, String path) throws Exception {
        View root = a.getWindow().getDecorView();
        int w = 1080;
        int h = 2160;
        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, w, h);

        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        root.draw(c);

        try (FileOutputStream out = new FileOutputStream(path)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        System.out.println("SHOT " + path + " " + bmp.getWidth() + "x" + bmp.getHeight());
    }
}
