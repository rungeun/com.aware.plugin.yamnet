package com.aware.plugin.yamnet;

import android.app.IntentService;
import android.content.Intent;
import android.database.Cursor;
import android.util.Log;

import com.aware.Aware;

/**
 * Service to clean up old audio data from SQLite database
 * Runs every 24 hours to remove audio records older than 24 hours
 */
public class AudioMigrationService extends IntentService {
    private static final String TAG = "AWARE::YAMNet::Migration";
    private static final long RETENTION_PERIOD_MS = 24 * 60 * 60 * 1000; // 24 hours

    public AudioMigrationService() {
        super("AudioMigrationService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.d(TAG, "Starting audio migration service...");

        try {
            // Only proceed if audio file saving is enabled
            if (!Aware.getSetting(getApplicationContext(), Settings.SAVE_AUDIO_FILES).equals("true")) {
                Log.d(TAG, "Audio file saving is disabled, skipping migration");
                return;
            }

            cleanupOldAudioData();
            
        } catch (Exception e) {
            Log.e(TAG, "Error during audio migration: " + e.getMessage());
            e.printStackTrace();
        }
        
        Log.d(TAG, "Audio migration service completed");
    }

    /**
     * Remove audio records older than 24 hours from SQLite database
     */
    private void cleanupOldAudioData() {
        long cutoffTime = System.currentTimeMillis() - RETENTION_PERIOD_MS;
        
        // Query old records first to log how many will be deleted
        String selection = Provider.YAMNet_Audio.TIMESTAMP + " < ?";
        String[] selectionArgs = {String.valueOf(cutoffTime)};
        
        Cursor cursor = getContentResolver().query(
            Provider.YAMNet_Audio.CONTENT_URI,
            new String[]{Provider.YAMNet_Audio._ID},
            selection,
            selectionArgs,
            null
        );
        
        int recordCount = 0;
        if (cursor != null) {
            recordCount = cursor.getCount();
            cursor.close();
        }
        
        if (recordCount > 0) {
            // Delete old audio records from database
            int deletedCount = getContentResolver().delete(
                Provider.YAMNet_Audio.CONTENT_URI,
                selection,
                selectionArgs
            );
            
            Log.d(TAG, "Cleaned up " + deletedCount + " old audio records from database");
        } else {
            Log.d(TAG, "No old audio records to clean up");
        }
    }
}