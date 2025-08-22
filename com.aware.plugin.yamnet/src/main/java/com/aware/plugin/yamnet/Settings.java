package com.aware.plugin.yamnet;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.util.Log;

import com.aware.Aware;
import com.aware.ui.AppCompatPreferenceActivity;

public class Settings extends AppCompatPreferenceActivity implements OnSharedPreferenceChangeListener {

    /**
     * Activate/deactivate plugin
     */
    public static final String STATUS_PLUGIN_YAMNET = "status_plugin_yamnet";

    /**
     * How frequently do we sample the microphone (default = 5) in minutes
     */
    public static final String FREQUENCY_PLUGIN_YAMNET = "frequency_plugin_yamnet";

    /**
     * Audio recording duration (default = 1000) in milliseconds
     */
    public static final String DURATION_PLUGIN_YAMNET = "duration_plugin_yamnet";

    /**
     * Enable/disable config updates
     */
    public static final String ENABLE_CONFIG_UPDATE = "enable_config_update";

    /**
     * Save audio files to permanent storage (default = false)
     */
    public static final String SAVE_AUDIO_FILES = "save_audio_files";

    private static CheckBoxPreference active, saveAudioFiles;
    private static EditTextPreference frequency, duration;
    private static final String TAG = "yamnet";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences_yamnet);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(this);

        if (Aware.getSetting(getApplicationContext(), ENABLE_CONFIG_UPDATE).length() == 0) {
            Aware.setSetting(getApplicationContext(), ENABLE_CONFIG_UPDATE, true);
        }

        if (Aware.getSetting(getApplicationContext(), STATUS_PLUGIN_YAMNET).length() == 0) {
            Aware.setSetting(getApplicationContext(), STATUS_PLUGIN_YAMNET, true);
        }

        if (Aware.getSetting(getApplicationContext(), FREQUENCY_PLUGIN_YAMNET).length() == 0) {
            Aware.setSetting(getApplicationContext(), FREQUENCY_PLUGIN_YAMNET, 5);
        }

        if (Aware.getSetting(getApplicationContext(), DURATION_PLUGIN_YAMNET).length() == 0) {
            Aware.setSetting(getApplicationContext(), DURATION_PLUGIN_YAMNET, 1000);
        }

        if (Aware.getSetting(getApplicationContext(), SAVE_AUDIO_FILES).length() == 0) {
            Aware.setSetting(getApplicationContext(), SAVE_AUDIO_FILES, false);
        }
    }

    private void updatePreferencesState() {
        boolean configUpdateEnabled = Aware.getSetting(getApplicationContext(), ENABLE_CONFIG_UPDATE).equals("true");

        active.setEnabled(configUpdateEnabled);
        frequency.setEnabled(configUpdateEnabled);
        duration.setEnabled(configUpdateEnabled);
        saveAudioFiles.setEnabled(configUpdateEnabled);
    }

    @Override
    protected void onResume() {
        super.onResume();

        active = (CheckBoxPreference) findPreference(STATUS_PLUGIN_YAMNET);
        String statusValue = Aware.getSetting(getApplicationContext(), STATUS_PLUGIN_YAMNET);
        boolean isActive = statusValue.equals("true");
        PreferenceManager.getDefaultSharedPreferences(this).edit()
                .putBoolean(STATUS_PLUGIN_YAMNET, isActive)
                .apply();
        active.setChecked(isActive);

        // Frequency
        frequency = (EditTextPreference) findPreference(FREQUENCY_PLUGIN_YAMNET);
        String freqValue = Aware.getSetting(getApplicationContext(), FREQUENCY_PLUGIN_YAMNET);
        PreferenceManager.getDefaultSharedPreferences(this).edit()
                .putString(FREQUENCY_PLUGIN_YAMNET, freqValue)
                .apply();
        frequency.setText(freqValue);
        frequency.setSummary("Every " + freqValue + " minutes");

        // Duration
        duration = (EditTextPreference) findPreference(DURATION_PLUGIN_YAMNET);
        String durValue = Aware.getSetting(getApplicationContext(), DURATION_PLUGIN_YAMNET);
        PreferenceManager.getDefaultSharedPreferences(this).edit()
                .putString(DURATION_PLUGIN_YAMNET, durValue)
                .apply();
        duration.setText(durValue);
        duration.setSummary("Record " + durValue + " milliseconds");

        // Save Audio Files
        saveAudioFiles = (CheckBoxPreference) findPreference(SAVE_AUDIO_FILES);
        String saveValue = Aware.getSetting(getApplicationContext(), SAVE_AUDIO_FILES);
        boolean isSaveAudioEnabled = saveValue.equals("true");
        PreferenceManager.getDefaultSharedPreferences(this).edit()
                .putBoolean(SAVE_AUDIO_FILES, isSaveAudioEnabled)
                .apply();
        saveAudioFiles.setChecked(isSaveAudioEnabled);

        // Update preferences state based on enable_config_update
        updatePreferencesState();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Preference preference = findPreference(key);
        boolean configUpdateEnabled = Aware.getSetting(getApplicationContext(), ENABLE_CONFIG_UPDATE).equals("true");

        if (!configUpdateEnabled && !key.equals(STATUS_PLUGIN_YAMNET)) {
            Log.d(TAG, "Config updates disabled. Ignoring change to: " + key);
            return;
        }

        if (preference.getKey().equals(STATUS_PLUGIN_YAMNET)) {
            boolean isChecked = sharedPreferences.getBoolean(key, false);
            Aware.setSetting(getApplicationContext(), key, isChecked);
            active.setChecked(isChecked);

            if (isChecked) {
                Aware.startPlugin(getApplicationContext(), "com.aware.plugin.yamnet");
            } else {
                Aware.stopPlugin(getApplicationContext(), "com.aware.plugin.yamnet");
            }
        }
        else if (preference.getKey().equals(FREQUENCY_PLUGIN_YAMNET)) {
            String value = sharedPreferences.getString(key, "5");
            Aware.setSetting(getApplicationContext(), key, value);
            frequency.setSummary("Every " + value + " minutes");
        }
        else if (preference.getKey().equals(DURATION_PLUGIN_YAMNET)) {
            String value = sharedPreferences.getString(key, "1000");
            Aware.setSetting(getApplicationContext(), key, value);
            duration.setSummary("Record " + value + " milliseconds");
        }
        else if (preference.getKey().equals(SAVE_AUDIO_FILES)) {
            boolean isChecked = sharedPreferences.getBoolean(key, false);
            Aware.setSetting(getApplicationContext(), key, isChecked);
            saveAudioFiles.setChecked(isChecked);
        }
    }
}