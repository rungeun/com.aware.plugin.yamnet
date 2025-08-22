package com.aware.plugin.yamnet;

import android.Manifest;
import android.accounts.Account;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SyncRequest;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;
import android.util.Log;

import com.aware.Aware;
import com.aware.Aware_Preferences;
import com.aware.ui.PermissionsHandler;
import com.aware.utils.Aware_Plugin;
import com.aware.utils.PluginsManager;
import com.aware.utils.Scheduler;

import org.json.JSONException;
import org.json.JSONObject;

public class Plugin extends Aware_Plugin {

    public static final String SCHEDULER_PLUGIN_YAMNET = "SCHEDULER_PLUGIN_YAMNET";
    public static final String SCHEDULER_MIGRATION = "SCHEDULER_MIGRATION";
    private static String TAG = "AWARE::YAMNet";
    private static final int PERMISSION_REQUEST_CODE = 123;
    private static final String PLUGIN_PACKAGE_NAME = "com.aware.plugin.yamnet";
    private static AWARESensorObserver awareSensor;

    @Override
    public void onCreate() {
        super.onCreate();

        AUTHORITY = Provider.getAuthority(this);
        TAG = "AWARE::YAMNet";
        REQUIRED_PERMISSIONS.add(Manifest.permission.RECORD_AUDIO);
        REQUIRED_PERMISSIONS.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        REQUIRED_PERMISSIONS.add(Manifest.permission.READ_EXTERNAL_STORAGE);

        if (checkAndRequestPermissions()) {
            Log.d(TAG, "Permissions OK, initializing plugin");
            initializePlugin();
        } else {
            Log.d(TAG, "Permissions not granted yet");
        }
    }

    private boolean checkAndRequestPermissions() {
        if (!PERMISSIONS_OK) {
            Log.d(TAG, "Requesting permissions...");
            Intent permissions = new Intent(this, PermissionsHandler.class);
            permissions.putExtra(PermissionsHandler.EXTRA_REQUIRED_PERMISSIONS, REQUIRED_PERMISSIONS);
            permissions.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(permissions);
            return false;
        }
        return true;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        if (PERMISSIONS_OK) {

            DEBUG = Aware.getSetting(this, Aware_Preferences.DEBUG_FLAG).equals("true");

            initializeSettings();

            setupScheduler();
            
            setupMigrationScheduler();

            setupSync();
        } else {
            Log.e(TAG, "Required permissions not granted");
        }

        return START_STICKY;
    }

    private void initializePlugin() {
        if (Aware.getSetting(getApplicationContext(), Settings.STATUS_PLUGIN_YAMNET).isEmpty()) {
            Aware.setSetting(getApplicationContext(), Settings.STATUS_PLUGIN_YAMNET, true);
        }
    }

    private void initializeSettings() {
        if (Aware.getSetting(getApplicationContext(), Settings.FREQUENCY_PLUGIN_YAMNET).isEmpty()) {
            Aware.setSetting(getApplicationContext(), Settings.FREQUENCY_PLUGIN_YAMNET, 5);
        }
    }

    private void setupScheduler() {
        try {
            Scheduler.Schedule audioSampler = Scheduler.getSchedule(this, SCHEDULER_PLUGIN_YAMNET);
            if (audioSampler == null || audioSampler.getInterval() != Long.parseLong(Aware.getSetting(this, Settings.FREQUENCY_PLUGIN_YAMNET))) {
                audioSampler = new Scheduler.Schedule(SCHEDULER_PLUGIN_YAMNET)
                        .setInterval(Long.parseLong(Aware.getSetting(this, Settings.FREQUENCY_PLUGIN_YAMNET)))
                        .setActionType(Scheduler.ACTION_TYPE_SERVICE)
                        .setActionClass(getPackageName() + "/" + AudioAnalyser.class.getName());
                Scheduler.saveSchedule(this, audioSampler);
            }
        } catch (JSONException e) {
            if (DEBUG) Log.e(TAG, "Error setting up scheduler: " + e.getMessage());
        }
    }

    private void setupMigrationScheduler() {
        try {
            // Set up daily migration service (runs every 24 hours)
            Scheduler.Schedule migrationSchedule = Scheduler.getSchedule(this, SCHEDULER_MIGRATION);
            if (migrationSchedule == null) {
                migrationSchedule = new Scheduler.Schedule(SCHEDULER_MIGRATION)
                        .setInterval(24 * 60) // 24 hours in minutes
                        .setActionType(Scheduler.ACTION_TYPE_SERVICE)
                        .setActionClass(getPackageName() + "/" + AudioMigrationService.class.getName());
                Scheduler.saveSchedule(this, migrationSchedule);
                
                Log.d(TAG, "Audio migration scheduler set up (24h interval)");
            }
        } catch (JSONException e) {
            if (DEBUG) Log.e(TAG, "Error setting up migration scheduler: " + e.getMessage());
        }
    }

    private void setupSync() {
        try {
            Account aware_account = Aware.getAWAREAccount(this);
            String authority = Provider.getAuthority(this);

            if (aware_account != null) {
                ContentResolver.setIsSyncable(aware_account, authority, 1);
                ContentResolver.setSyncAutomatically(aware_account, authority, true);

                if (Aware.isStudy(this)) {
                    long frequency = Long.parseLong(Aware.getSetting(this, Aware_Preferences.FREQUENCY_WEBSERVICE)) * 60;

                    SyncRequest request = new SyncRequest.Builder()
                            .syncPeriodic(frequency, frequency / 3)
                            .setSyncAdapter(aware_account, authority)
                            .setExtras(new Bundle())
                            .build();
                    ContentResolver.requestSync(request);

                    Log.d(TAG, "Sync configured with frequency: " + frequency + " seconds");
                }

            } else {
                Log.e(TAG, "Failed to setup sync - no AWARE account found");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting up sync adapter: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void onDestroy() {
        try {
            // Remove sync settings
            if (Aware.getAWAREAccount(this) != null) {
                ContentResolver.setSyncAutomatically(Aware.getAWAREAccount(this), Provider.getAuthority(this), false);
                ContentResolver.removePeriodicSync(
                        Aware.getAWAREAccount(this),
                        Provider.getAuthority(this),
                        Bundle.EMPTY
                );
            }

            Scheduler.removeSchedule(this, SCHEDULER_PLUGIN_YAMNET);
            Scheduler.removeSchedule(this, SCHEDULER_MIGRATION);
            super.onDestroy();
        } catch (Exception e) {
            if (DEBUG) Log.e(TAG, "Error during plugin cleanup: " + e.getMessage());
        }
    }

    public static void setSensorObserver(AWARESensorObserver observer) {
        awareSensor = observer;
    }

    public static AWARESensorObserver getSensorObserver() {
        return awareSensor;
    }

    public interface AWARESensorObserver {
        void onAudioAnalyzed(ContentValues data);
    }
}