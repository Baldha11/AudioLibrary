package com.example.audiolibrary.audioMain.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.lang.reflect.InvocationTargetException;

public class Sound extends com.example.audiolibrary.androidlibrary.sound.Sound {
    public static String TAG = Sound.class.getSimpleName();

    public static int SOURCE_INTERNAL_AUDIO = -100;

    // quite room gives me 20db
    public static int NOISE_DB = 20;
    // max 90 dB detection for android mic
    public static int MAXIMUM_DB = 90;
    public static int SOUND_STREAM = AudioManager.STREAM_MUSIC;
    public static int SOUND_CHANNEL = AudioAttributes.USAGE_MEDIA;
    public static int SOUND_TYPE = AudioAttributes.CONTENT_TYPE_MUSIC;

    public Intent intent; // internal audio recording intent

    public Sound(Context context) {
        super(context);
    }

    public void silent() {
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        if (shared.getBoolean(MainApplication.PREFERENCE_SILENT, false)) {
            super.silent();
        }
    }

    public void unsilent() {
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        if (shared.getBoolean(MainApplication.PREFERENCE_SILENT, false)) {
            super.unsilent();
        }
    }

    public static int getChannels(Context context) {
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        int i = Integer.parseInt(shared.getString(MainApplication.PREFERENCE_CHANNELS, "1"));
        return i;
    }

    public static int getInMode(Context context) {
        switch (getChannels(context)) {
            case 1:
                return AudioFormat.CHANNEL_IN_MONO;
            case 2:
                return AudioFormat.CHANNEL_IN_STEREO;
            default:
                throw new RuntimeException("unknown mode");
        }
    }

    public static int getOutMode(Context context) {
        switch (getChannels(context)) {
            case 1:
                return AudioFormat.CHANNEL_OUT_MONO;
            case 2:
                return AudioFormat.CHANNEL_OUT_STEREO;
            default:
                throw new RuntimeException("unknown mode");
        }
    }

    public static int indexOf(int[] ss, int s) {
        for (int i = 0; i < ss.length; i++) {
            if (ss[i] == s)
                return i;
        }
        return -1;
    }

    public static int getAudioFormat(Context context) {
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        String format = shared.getString(MainApplication.PREFERENCE_AUDIOFORMAT, "16");
        if (format.equals("16"))
            return AudioFormat.ENCODING_PCM_16BIT;
        if (format.equals("float"))
            return AudioFormat.ENCODING_PCM_FLOAT;
        throw new RuntimeException("Unknown format");
    }

    public static int getSampleRate(Context context) {
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        int sampleRate = Integer.parseInt(shared.getString(MainApplication.PREFERENCE_RATE, ""));
        sampleRate = Sound.getValidRecordRate(getAudioFormat(context), getInMode(context), sampleRate);
        if (sampleRate == -1)
            sampleRate = Sound.DEFAULT_RATE;
        return sampleRate;
    }

    public static void showInternalAudio(Activity a, int code) {
        MediaProjectionManager mp = (MediaProjectionManager) a.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        Intent intent = mp.createScreenCaptureIntent();
        a.startActivityForResult(intent, code);
    }

    @SuppressLint("MissingPermission")
    @RequiresApi(api = Build.VERSION_CODES.M)
    public static AudioRecord createInternalAudio(Context context, int format, int sampleRate, Intent intent) { // API29 Screen/Audio recording API
        int c = getInMode(context);
        final int min = AudioRecord.getMinBufferSize(sampleRate, c, format);
        if (min <= 0)
            throw new RuntimeException("Unable to initialize AudioRecord: Bad audio values");
        try {
            AudioFormat audioFormat = new AudioFormat.Builder()
                    .setEncoding(format)
                    .setSampleRate(sampleRate)
                    .setChannelMask(c)
                    .build();
            AudioRecord.Builder build = new AudioRecord.Builder()
                    .setAudioFormat(audioFormat)
                    .setBufferSizeInBytes(min);
            if (Build.VERSION.SDK_INT >= 29) {
                MediaProjectionManager mp = (MediaProjectionManager) context.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                MediaProjection mediaProjection = mp.getMediaProjection(Activity.RESULT_OK, intent);
                Class MediaProjection = mediaProjection.getClass();
                Class AudioPlaybackCaptureConfigurationBuilder = Class.forName("android.media.AudioPlaybackCaptureConfiguration$Builder");
                Object ab = AudioPlaybackCaptureConfigurationBuilder.getConstructor(MediaProjection).newInstance(mediaProjection);
                AudioPlaybackCaptureConfigurationBuilder.getDeclaredMethod("addMatchingUsage", int.class).invoke(ab, AudioAttributes.USAGE_MEDIA); // AudioAttributes#USAGE_UNKNOWN or AudioAttributes#USAGE_GAME or AudioAttributes#USAGE_MEDIA
                Object config = AudioPlaybackCaptureConfigurationBuilder.getDeclaredMethod("build").invoke(ab);
                Class AudioPlaybackCaptureConfiguration = Class.forName("android.media.AudioPlaybackCaptureConfiguration");
                Class AudioRecorderBuilder = build.getClass();
                AudioRecorderBuilder.getDeclaredMethod("setAudioPlaybackCaptureConfig", AudioPlaybackCaptureConfiguration).invoke(build, config);
            } else {
                build.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
            }
            return build.build();
        } catch (NoSuchMethodException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InstantiationException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressLint("MissingPermission")
    public static AudioRecord createAudioRecorder(Context context, int format, int sampleRate, int[] ss, int i) {
        AudioRecord r = null;

        int c = getInMode(context);
        final int min = AudioRecord.getMinBufferSize(sampleRate, c, format);
        if (min <= 0)
            throw new RuntimeException("Unable to initialize AudioRecord: Bad audio values");

        for (; i < ss.length; i++) {
            int s = ss[i];
            try {
                r = new AudioRecord(s, sampleRate, c, format, min);
                if (r.getState() == AudioRecord.STATE_INITIALIZED)
                    return r;
            } catch (IllegalArgumentException e) {
                Log.d(TAG, "Recorder Create Failed: " + s, e);
            }
        }
        if (r == null || r.getState() != AudioRecord.STATE_INITIALIZED)
            throw new RuntimeException("Unable to initialize AudioRecord");

        return r;
    }

    public static void throwError(int readSize) {
        switch (readSize) {
            case AudioRecord.ERROR:
                throw new RuntimeException("AudioRecord.ERROR");
            case AudioRecord.ERROR_BAD_VALUE:
                throw new RuntimeException("AudioRecord.ERROR_BAD_VALUE");
            case AudioRecord.ERROR_INVALID_OPERATION:
                throw new RuntimeException("AudioRecord.ERROR_INVALID_OPERATION");
            case AudioRecord.ERROR_DEAD_OBJECT:
                throw new RuntimeException("AudioRecord.ERROR_DEAD_OBJECT");
        }
    }

    public static boolean isUnprocessedSupported(Context context) {
        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (Build.VERSION.SDK_INT >= 24) {
            String s = am.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED);
            if (s == null || !s.equals(Boolean.toString(true)))
                return false;
        } else {
            return false;
        }
        return true;
    }

    @SuppressLint("MissingPermission")
    @RequiresApi(api = Build.VERSION_CODES.M)
    public AudioRecord createAudioRecorder(int format, int sampleRate, int[] ss, int i) {
        AudioRecord r = null;

        int c = getInMode(context);
        final int min = AudioRecord.getMinBufferSize(sampleRate, c, format);
        if (min <= 0)
            throw new RuntimeException("Unable to initialize AudioRecord: Bad audio values");

        for (; i < ss.length; i++) {
            int s = ss[i];
            try {
                if (s == Sound.SOURCE_INTERNAL_AUDIO)
                    r = createInternalAudio(context, format, sampleRate, intent);
                else
                    r = new AudioRecord(s, sampleRate, c, format, min);
                if (r.getState() == AudioRecord.STATE_INITIALIZED)
                    return r;
            } catch (IllegalArgumentException e) {
                Log.d(TAG, "Recorder Create Failed: " + s, e);
            }
        }
        if (r == null || r.getState() != AudioRecord.STATE_INITIALIZED)
            throw new RuntimeException("Unable to initialize AudioRecord");

        return r;
    }

    public boolean permitted() {
        return intent != null;
    }

    public void onActivityResult(Intent data) {
        this.intent = data;
    }
}
