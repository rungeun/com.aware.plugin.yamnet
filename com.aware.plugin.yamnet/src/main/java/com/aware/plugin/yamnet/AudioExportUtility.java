package com.aware.plugin.yamnet;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class AudioExportUtility {
    private static final String TAG = "AudioExportUtility";
    private static final int SAMPLE_RATE = 16000;
    private static final int BITS_PER_SAMPLE = 16;
    private static final int CHANNELS = 1;

    /**
     * Export audio data from database to WAV file
     */
    public static File exportAudioToWav(Context context, long timestamp) {
        // Query the audio table for the specific timestamp
        Cursor cursor = context.getContentResolver().query(
                Provider.YAMNet_Audio.CONTENT_URI,
                new String[]{Provider.YAMNet_Audio.RAW_AUDIO, Provider.YAMNet_Audio.TIMESTAMP},
                Provider.YAMNet_Audio.TIMESTAMP + " = ?",
                new String[]{String.valueOf(timestamp)},
                null
        );

        if (cursor != null && cursor.moveToFirst()) {
            try {
                // Get raw audio data
                byte[] audioData = cursor.getBlob(cursor.getColumnIndex(Provider.YAMNet_Audio.RAW_AUDIO));

                // Create output file
                File outputDir = new File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "yamnet_recordings");
                if (!outputDir.exists()) {
                    outputDir.mkdirs();
                }

                File outputFile = new File(outputDir, "yamnet_" + timestamp + ".wav");

                // Write WAV file
                writeWavFile(outputFile, audioData);

                Log.d(TAG, "Audio exported to: " + outputFile.getAbsolutePath());
                return outputFile;

            } catch (Exception e) {
                Log.e(TAG, "Error exporting audio: " + e.getMessage());
            } finally {
                cursor.close();
            }
        }

        return null;
    }

    /**
     * Write raw PCM data to WAV file
     */
    private static void writeWavFile(File outputFile, byte[] audioData) throws IOException {
        FileOutputStream fos = new FileOutputStream(outputFile);

        // WAV file header
        int dataSize = audioData.length;
        int fileSize = dataSize + 36; // 36 = header size - 8

        // RIFF header
        fos.write("RIFF".getBytes());
        fos.write(intToByteArray(fileSize));
        fos.write("WAVE".getBytes());

        // fmt subchunk
        fos.write("fmt ".getBytes());
        fos.write(intToByteArray(16)); // Subchunk1Size (16 for PCM)
        fos.write(shortToByteArray((short) 1)); // AudioFormat (1 for PCM)
        fos.write(shortToByteArray((short) CHANNELS)); // NumChannels
        fos.write(intToByteArray(SAMPLE_RATE)); // SampleRate
        fos.write(intToByteArray(SAMPLE_RATE * CHANNELS * BITS_PER_SAMPLE / 8)); // ByteRate
        fos.write(shortToByteArray((short) (CHANNELS * BITS_PER_SAMPLE / 8))); // BlockAlign
        fos.write(shortToByteArray((short) BITS_PER_SAMPLE)); // BitsPerSample

        // data subchunk
        fos.write("data".getBytes());
        fos.write(intToByteArray(dataSize));
        fos.write(audioData);

        fos.close();
    }

    /**
     * Convert int to byte array (little-endian)
     */
    private static byte[] intToByteArray(int value) {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array();
    }

    /**
     * Convert short to byte array (little-endian)
     */
    private static byte[] shortToByteArray(short value) {
        return ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value).array();
    }

    /**
     * Export all audio recordings from database
     */
    public static void exportAllRecordings(Context context) {
        Cursor cursor = context.getContentResolver().query(
                Provider.YAMNet_Data.CONTENT_URI,
                new String[]{Provider.YAMNet_Data._ID},
                null,
                null,
                Provider.YAMNet_Data.TIMESTAMP + " DESC"
        );

        if (cursor != null) {
            while (cursor.moveToNext()) {
                long id = cursor.getLong(cursor.getColumnIndex(Provider.YAMNet_Data._ID));
                exportAudioToWav(context, id);
            }
            cursor.close();
        }
    }
}