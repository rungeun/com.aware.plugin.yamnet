package com.aware.plugin.yamnet.syncadapters;

import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.IBinder;
import android.util.Log;

import com.aware.plugin.yamnet.Provider;
import com.aware.syncadapters.AwareSyncAdapter;

/**
 * YAMNet sync adapter service
 * Uses AWARE's built-in sync adapter to handle data synchronization
 */
public class Yamnet_Sync extends Service {
    private AwareSyncAdapter sSyncAdapter = null;
    private static final Object sSyncAdapterLock = new Object();

    @Override
    public void onCreate() {
        super.onCreate();
        synchronized (sSyncAdapterLock) {
            if (sSyncAdapter == null) {
                sSyncAdapter = new AwareSyncAdapter(getApplicationContext(), true, true);
                sSyncAdapter.init(
                        new String[]{Provider.DATABASE_TABLES[0]},  // 첫 번째 테이블만
                        new String[]{Provider.TABLES_FIELDS[0]},    // 첫 번째 테이블 필드만
                        new Uri[]{
                                Provider.YAMNet_Data.CONTENT_URI
                        }
                );
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return sSyncAdapter.getSyncAdapterBinder();
    }
}