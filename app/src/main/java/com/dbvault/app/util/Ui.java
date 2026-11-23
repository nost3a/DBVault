package com.dbvault.app.util;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.util.TypedValue;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;

import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;

/**
 * 一些琐碎的界面工具
 * small UI helpers
 */
public final class Ui {

    private Ui() {
    }

    public static int dp(Context ctx, float v) {
        return (int) (v * ctx.getResources().getDisplayMetrics().density + 0.5f);
    }

    public static int color(Context ctx, int res) {
        return ContextCompat.getColor(ctx, res);
    }

    public static void edgeToEdge(Activity a) {
        Window w = a.getWindow();
        w.setBackgroundDrawable(new ColorDrawable(Color.BLACK));
        WindowCompat.setDecorFitsSystemWindows(w, false);
        w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
    }

    public static void hideKeyboard(View v) {
        InputMethodManager imm = (InputMethodManager) v.getContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }

    public static void showKeyboard(View v) {
        InputMethodManager imm = (InputMethodManager) v.getContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            v.requestFocus();
            imm.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    public static float sp(Context ctx, float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v,
                ctx.getResources().getDisplayMetrics());
    }

    public static String humanSize(long bytes) {
        if (bytes <= 0) return "—";
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format("%.0f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format("%.1f MB", mb);
        return String.format("%.2f GB", mb / 1024.0);
    }
}
