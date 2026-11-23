package com.dbvault.app;

import android.app.Application;

/**
 * 全局入口，仅用于拿到 application context
 * application singleton
 */
public class App extends Application {

    private static App instance;

    public static App get() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }
}
