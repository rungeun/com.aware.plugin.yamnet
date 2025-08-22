package com.aware.plugin.yamnet;

import android.app.IntentService;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.util.Base64;
import android.util.Log;

import com.aware.Aware;
import com.aware.Aware_Preferences;
import com.aware.plugin.yamnet.Settings;

import org.tensorflow.lite.Interpreter;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Audio collection service for YAMNet analysis
 * Collects 1 second of audio data at 16kHz for YAMNet model processing
 */
public class AudioAnalyser extends IntentService {
    private static final String TAG = "AWARE::YAMNet";
    private static final int SAMPLE_RATE = 16000; // YAMNet requires 16kHz

    public AudioAnalyser() {
        super(Aware.TAG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        // Check if microphone is available
        if (!isMicrophoneAvailable(getApplicationContext())) {
            Log.e(TAG, "Microphone not available");
            return;
        }

        // Get recording duration from settings
        int recordingDurationMs = 1000; // default 1 second
        try {
            String durationSetting = Aware.getSetting(getApplicationContext(), Settings.DURATION_PLUGIN_YAMNET);
            if (!durationSetting.isEmpty()) {
                recordingDurationMs = Integer.parseInt(durationSetting);
            }
        } catch (Exception e) {
            Log.w(TAG, "Error reading duration setting, using default: " + e.getMessage());
        }

        Log.d(TAG, "Recording duration: " + recordingDurationMs + " ms");

        // Calculate buffer size based on duration
        int bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
        );

        // Ensure buffer is large enough for the specified duration
        int minBufferSize = (SAMPLE_RATE * 2 * recordingDurationMs) / 1000; // samples * bytes * seconds
        if (bufferSize < minBufferSize) {
            bufferSize = minBufferSize;
        }

        // Initialize audio recorder
        AudioRecord recorder = new AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
        );

        // Wait for recorder to initialize
        while (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Log.e(TAG, "Interrupted while waiting for recorder initialization");
                return;
            }
        }

        if (recorder.getRecordingState() == AudioRecord.RECORDSTATE_STOPPED) {
            recorder.startRecording();
        }

        Log.d(TAG, "Starting " + recordingDurationMs + "ms audio collection for YAMNet analysis...");

        // Collect audio data for the specified duration
        ByteArrayOutputStream audioStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[bufferSize];
        int totalBytesRead = 0;
        int targetBytes = (SAMPLE_RATE * 2 * recordingDurationMs) / 1000; // Calculate target bytes based on duration

        long startTime = System.currentTimeMillis();

        while (totalBytesRead < targetBytes) {
            int bytesToRead = Math.min(buffer.length, targetBytes - totalBytesRead);
            int bytesRead = recorder.read(buffer, 0, bytesToRead);

            if (bytesRead > 0) {
                audioStream.write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;
            }

            // Timeout protection (max duration + 1 second)
            if (System.currentTimeMillis() - startTime > recordingDurationMs + 1000) {
                Log.w(TAG, "Recording timeout reached");
                break;
            }
        }

        // Stop recording
        recorder.stop();
        recorder.release();

        byte[] audioData = audioStream.toByteArray();
        Log.d(TAG, "Collected " + audioData.length + " bytes of audio data in " + recordingDurationMs + "ms");

        // Perform YAMNet analysis
        String yamnetResults = performYAMNetAnalysis(audioData);
        Log.d(TAG, "YAMNet analysis result: " + yamnetResults);

        // Store results in database
        long timestamp = System.currentTimeMillis();
        String deviceId = Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID);

        // 1. 분석 결과를 메인 테이블에 저장 (동기화됨)
        ContentValues mainData = new ContentValues();
        mainData.put(Provider.YAMNet_Data.TIMESTAMP, timestamp);
        mainData.put(Provider.YAMNet_Data.DEVICE_ID, deviceId);
        mainData.put(Provider.YAMNet_Data.DURATION, recordingDurationMs);  // 수집 시간 추가
        mainData.put(Provider.YAMNet_Data.ANALYSIS_RESULTS, yamnetResults);

        getContentResolver().insert(Provider.YAMNet_Data.CONTENT_URI, mainData);

        // 2. 원본 오디오를 별도 테이블에 저장 (로컬 전용)
        ContentValues audioValues = new ContentValues();
        audioValues.put(Provider.YAMNet_Audio.TIMESTAMP, timestamp);
        audioValues.put(Provider.YAMNet_Audio.DEVICE_ID, deviceId);
        audioValues.put(Provider.YAMNet_Audio.DURATION, recordingDurationMs);  // 수집 시간 추가
        audioValues.put(Provider.YAMNet_Audio.RAW_AUDIO, audioData);  // 실제 오디오 바이트 배열

        getContentResolver().insert(Provider.YAMNet_Audio.CONTENT_URI, audioValues);

        // Save audio file if enabled
        if (Aware.getSetting(getApplicationContext(), Settings.SAVE_AUDIO_FILES).equals("true")) {
            saveAudioFile(audioData, timestamp);
        }

        // Notify observers
        if (Plugin.getSensorObserver() != null) {
            Plugin.getSensorObserver().onAudioAnalyzed(mainData);
        }

        Log.d(TAG, "YAMNet analysis completed");
    }

    /**
     * Perform YAMNet analysis on audio data
     */
    private String performYAMNetAnalysis(byte[] audioData) {
        Log.d(TAG, "Starting YAMNet analysis...");
        try {
            // Load TensorFlow Lite model
            Context context = getApplicationContext();
            Log.d(TAG, "Loading model file...");

            // 모델 파일 존재 확인
            try {
                String[] assets = context.getAssets().list("");
                Log.d(TAG, "Assets folder contains:");
                for (String asset : assets) {
                    Log.d(TAG, " - " + asset);
                }
            } catch (IOException e) {
                Log.e(TAG, "Error listing assets: " + e.getMessage());
            }

            Interpreter tflite = new Interpreter(loadModelFile(context, "yamnet.tflite"));
            // 다른 파일명인 경우:
            // Interpreter tflite = new Interpreter(loadModelFile(context, "lite-model_yamnet_classification_tflite_1.tflite"));
            Log.d(TAG, "Model loaded successfully");

            // Load labels
            List<String> labels = loadLabels(context);

            // Convert byte array to float array normalized to [-1, 1]
            float[] floatAudio = convertBytesToFloat(audioData);

            // Prepare input/output buffers
            float[][] output = new float[1][521]; // YAMNet has 521 classes

            // Run inference
            tflite.run(floatAudio, output);

            // Find top 5 predictions
            List<Prediction> predictions = getTopPredictions(output[0], labels, 5);

            // Create JSON result
            JSONObject result = new JSONObject();
            result.put("status", "success");
            result.put("timestamp", System.currentTimeMillis());

            JSONArray predictionsArray = new JSONArray();
            for (Prediction pred : predictions) {
                JSONObject predObj = new JSONObject();
                predObj.put("label", pred.label);
                predObj.put("score", pred.score);
                predObj.put("index", pred.index);
                predictionsArray.put(predObj);
            }
            result.put("predictions", predictionsArray);

            tflite.close();
            return result.toString();

        } catch (Exception e) {
            Log.e(TAG, "Error performing YAMNet analysis: " + e.getMessage());
            e.printStackTrace();
            return "{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}";
        }
    }

    /**
     * Load TFLite model from assets
     */
    private MappedByteBuffer loadModelFile(Context context, String modelPath) throws IOException {
        AssetFileDescriptor fileDescriptor = context.getAssets().openFd(modelPath);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    /**
     * Convert 16-bit PCM byte array to normalized float array
     */
    private float[] convertBytesToFloat(byte[] audioData) {
        short[] shortData = new short[audioData.length / 2];
        ByteBuffer.wrap(audioData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shortData);

        float[] floatData = new float[shortData.length];
        for (int i = 0; i < shortData.length; i++) {
            // Normalize to [-1, 1]
            floatData[i] = shortData[i] / 32768.0f;
        }

        return floatData;
    }

    /**
     * Load labels from assets
     */
    private List<String> loadLabels(Context context) {
        List<String> labels = new ArrayList<>();
        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(context.getAssets().open("yamnet_labels.txt"))
            );
            String line;
            while ((line = reader.readLine()) != null) {
                labels.add(line.trim());
            }
            reader.close();
        } catch (IOException e) {
            Log.e(TAG, "Error loading labels: " + e.getMessage());
            // Return default labels if file not found
            for (int i = 0; i < 521; i++) {
                labels.add("Class " + i);
            }
        }
        return labels;
    }

    /**
     * Get top N predictions from model output
     */
    private List<Prediction> getTopPredictions(float[] scores, List<String> labels, int topN) {
        List<Prediction> predictions = new ArrayList<>();

        // Create list of all predictions
        for (int i = 0; i < scores.length && i < labels.size(); i++) {
            predictions.add(new Prediction(i, labels.get(i), scores[i]));
        }

        // Sort by score descending
        Collections.sort(predictions, new Comparator<Prediction>() {
            @Override
            public int compare(Prediction a, Prediction b) {
                return Float.compare(b.score, a.score);
            }
        });

        // Return top N
        return predictions.subList(0, Math.min(topN, predictions.size()));
    }

    /**
     * Simple class to hold prediction results
     */
    private static class Prediction {
        int index;
        String label;
        float score;

        Prediction(int index, String label, float score) {
            this.index = index;
            this.label = label;
            this.score = score;
        }
    }

    /**
     * Check if the microphone is available
     */
    public static boolean isMicrophoneAvailable(Context context) {
        MediaRecorder recorder = new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.DEFAULT);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
        recorder.setOutputFile(new File(context.getCacheDir(), "micAvailTest.tmp").getAbsolutePath());

        boolean available = true;
        try {
            recorder.prepare();
            recorder.start();
        } catch (Exception e) {
            available = false;
        } finally {
            try {
                recorder.stop();
                recorder.reset();
                recorder.release();
            } catch (Exception ignored) {}
        }

        return available;
    }

    /**
     * Save audio data as WAV file to permanent storage
     */
    private void saveAudioFile(byte[] audioData, long timestamp) {
        try {
            // Create directory structure: /audio/YYYY-MM-DD/
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            String dateStr = dateFormat.format(new Date(timestamp));
            
            File audioDir = new File(getExternalFilesDir(null), "audio");
            File dayDir = new File(audioDir, dateStr);
            
            if (!dayDir.exists()) {
                dayDir.mkdirs();
            }
            
            // Create filename: yamnet_YYYY-MM-DD_HH-MM-SS.wav
            SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US);
            String timeStr = timeFormat.format(new Date(timestamp));
            String filename = "yamnet_" + timeStr + ".wav";
            
            File audioFile = new File(dayDir, filename);
            
            // Write WAV file
            writeWavFile(audioFile, audioData);
            
            Log.d(TAG, "Audio file saved: " + audioFile.getAbsolutePath());
            
        } catch (Exception e) {
            Log.e(TAG, "Error saving audio file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Write raw PCM data to WAV file
     */
    private void writeWavFile(File outputFile, byte[] audioData) throws IOException {
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
        fos.write(shortToByteArray((short) 1)); // NumChannels (mono)
        fos.write(intToByteArray(SAMPLE_RATE)); // SampleRate
        fos.write(intToByteArray(SAMPLE_RATE * 1 * 16 / 8)); // ByteRate
        fos.write(shortToByteArray((short) (1 * 16 / 8))); // BlockAlign
        fos.write(shortToByteArray((short) 16)); // BitsPerSample

        // data subchunk
        fos.write("data".getBytes());
        fos.write(intToByteArray(dataSize));
        fos.write(audioData);

        fos.close();
    }

    /**
     * Convert int to byte array (little-endian)
     */
    private byte[] intToByteArray(int value) {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array();
    }

    /**
     * Convert short to byte array (little-endian)
     */
    private byte[] shortToByteArray(short value) {
        return ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value).array();
    }
}