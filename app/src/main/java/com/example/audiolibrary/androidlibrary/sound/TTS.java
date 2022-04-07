package com.example.audiolibrary.androidlibrary.sound;

import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.util.AndroidRuntimeException;
import android.util.Log;

import com.example.audiolibrary.androidlibrary.app.AlarmManager;
import com.example.audiolibrary.androidlibrary.preferences.OptimizationPreferenceCompat;
import com.example.audiolibrary.androidlibrary.widgets.Toast;

import java.util.HashMap;
import java.util.Locale;
import java.util.UUID;

public class TTS extends Sound {
    public static final String TAG = TTS.class.getSimpleName();

    public static final int DELAYED_DELAY = 5 * AlarmManager.SEC1;
    public static final String TTS_INIT = TTS.class.getCanonicalName() + ".TTS_INIT";

    public TextToSpeech tts;
    public Runnable delayed; // delayedSpeach. tts may not be initalized, on init done, run delayed.run()
    public int restart; // restart tts once if failed. on apk upgrade tts always failed.
    public Runnable onInit; // once

    public static void startTTSInstall(Context context) {
        try {
            Intent intent = new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (OptimizationPreferenceCompat.isCallable(context, intent))
                context.startActivity(intent);
        } catch (AndroidRuntimeException e) {
            Log.d(TAG, "Unable to load TTS", e);
            startTTSCheck(context);
        }
    }

    public static void startTTSCheck(Context context) {
        try {
            Intent intent = new Intent(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (OptimizationPreferenceCompat.isCallable(context, intent))
                context.startActivity(intent);
        } catch (AndroidRuntimeException e1) {
            Log.d(TAG, "Unable to load TTS", e1);
        }
    }

    public static class Speak {
        public Locale locale;
        public String text;

        public Speak(Locale l, String t) {
            locale = l;
            text = t;
        }

        @Override
        public String toString() {
            return text + " (" + locale + ")";
        }
    }

    public TTS(Context context) {
        super(context);
    }

    public void ttsCreate() {
        Log.d(TAG, "tts create");
        handler.removeCallbacks(onInit);
        onInit = new Runnable() {
            @Override
            public void run() {
                onInit();
            }
        };
        tts = new TextToSpeech(context, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(final int status) {
                if (status != TextToSpeech.SUCCESS)
                    return;
                handler.post(onInit);
            }
        });
    }

    public void onInit() {
        if (Build.VERSION.SDK_INT >= 21) {
            Channel c = getSoundChannel();
            tts.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(c.usage)
                    .setContentType(c.ct)
                    .build());
        }

        handler.removeCallbacks(onInit);
        onInit = null;

        done(delayed);
        handler.removeCallbacks(delayed);
        delayed = null;
    }

    public void close() { // external close
        closeTTS();
    }

    public void closeTTS() { // internal close
        Log.d(TAG, "closeTTS()");
        if (tts != null) {
            tts.shutdown();
            tts = null;
        }
        handler.removeCallbacks(onInit);
        onInit = null;
        dones.remove(delayed);
        handler.removeCallbacks(delayed);
        delayed = null;
    }

    public void playSpeech(final Speak speak, final Runnable done) {
        dones.add(done);

        dones.remove(delayed);
        handler.removeCallbacks(delayed);
        delayed = null;

        if (tts == null)
            ttsCreate();

        // clear delayed(), sound just played
        final Runnable clear = new Runnable() {
            @Override
            public void run() {
                dones.remove(delayed);
                handler.removeCallbacks(delayed);
                delayed = null;
                done(done);
            }
        };

        if (Build.VERSION.SDK_INT < 15) {
            tts.setOnUtteranceCompletedListener(new TextToSpeech.OnUtteranceCompletedListener() {
                @Override
                public void onUtteranceCompleted(String s) {
                    handler.post(clear);
                }
            });
        } else {
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override
                public void onStart(final String utteranceId) { // tts start speaking
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            dones.remove(delayed);
                            handler.removeCallbacks(delayed);
                            delayed = null;
                        }
                    });
                }

                public void onRangeStart(final String utteranceId, final int start, final int end, final int frame) { // API26
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            TTS.this.onRangeStart(utteranceId, start, end, frame);
                        }
                    });
                }

                @Override
                public void onDone(final String utteranceId) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            TTS.this.onDone(utteranceId, clear);
                        }
                    });
                }

                @Override
                public void onError(final String utteranceId) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            TTS.this.onError(utteranceId, clear);
                        }
                    });
                }
            });
        }

        // TTS may say failed, but play sounds successfully. we need regardless or failed do not
        // play speech twice if clear.run() was called.
        if (!playSpeech(speak)) {
            Log.d(TAG, "Waiting for TTS");
            Toast.makeText(context, "Waiting for TTS", Toast.LENGTH_SHORT).show();
            dones.remove(delayed);
            handler.removeCallbacks(delayed);
            delayed = new Runnable() {
                @Override
                public void run() {
                    Log.d(TAG, "delayed run()");
                    if (!playSpeech(speak)) {
                        closeTTS();
                        if (restart >= 1) {
                            Log.d(TAG, "Failed TTS again, skipping");
                            Toast.makeText(context, "Failed TTS again, skipping", Toast.LENGTH_SHORT).show();
                            clear.run();
                        } else {
                            Log.d(TAG, "Failed TTS again, restarting");
                            restart++;
                            Toast.makeText(context, "Failed TTS again, restarting", Toast.LENGTH_SHORT).show();
                            dones.remove(delayed);
                            handler.removeCallbacks(delayed);
                            delayed = new Runnable() {
                                @Override
                                public void run() {
                                    playSpeech(speak, done);
                                }
                            };
                            dones.add(delayed);
                            handler.postDelayed(delayed, DELAYED_DELAY);
                        }
                    }
                }
            };
            dones.add(delayed);
            handler.postDelayed(delayed, DELAYED_DELAY);
        }
    }

    public Locale getUserLocale() {
        return Locale.getDefault();
    }

    public Locale getTTSLocale() {
        Locale locale = getUserLocale();

        if (tts == null)
            return locale;

        if (tts.isLanguageAvailable(locale) == TextToSpeech.LANG_NOT_SUPPORTED) {
            String lang = locale.getLanguage();
            locale = new Locale(lang);
        }

        if (tts.isLanguageAvailable(locale) == TextToSpeech.LANG_NOT_SUPPORTED) { // user selection not supported.
            locale = null;
            if (Build.VERSION.SDK_INT >= 21) {
                Voice v = tts.getDefaultVoice();
                if (v != null)
                    locale = v.getLocale();
            }
            if (locale == null) {
                if (Build.VERSION.SDK_INT >= 18)
                    locale = tts.getDefaultLanguage();
                else
                    locale = tts.getLanguage();
            }
            if (locale == null)
                locale = Locale.getDefault();
            if (tts.isLanguageAvailable(locale) == TextToSpeech.LANG_NOT_SUPPORTED) {
                String lang = locale.getLanguage(); // default tts voice not supported. use 'lang' "ru" of "ru_RU"
                locale = new Locale(lang);
            }
            if (tts.isLanguageAvailable(locale) == TextToSpeech.LANG_NOT_SUPPORTED) {
                locale = Locale.getDefault(); // default 'lang' tts voice not supported. use 'system default lang'
                String lang = locale.getLanguage();
                locale = new Locale(lang);
            }
            if (tts.isLanguageAvailable(locale) == TextToSpeech.LANG_NOT_SUPPORTED)
                locale = new Locale("en"); // 'system default lang' tts voice not supported. use 'en'
            if (tts.isLanguageAvailable(locale) == TextToSpeech.LANG_NOT_SUPPORTED)
                return null; // 'en' not supported? do not speak
        }

        if (tts.isLanguageAvailable(locale) == TextToSpeech.LANG_MISSING_DATA) {
            startTTSInstall(context);
            return null;
        }

        return locale;
    }

    public boolean playSpeech(Speak speak) {
        if (onInit != null)
            return false;
        return playSpeech(speak.locale, speak.text);
    }

    public boolean playSpeech(Locale locale, String speak) {
        tts.setLanguage(locale);
        if (Build.VERSION.SDK_INT >= 21) {
            Bundle params = new Bundle();
            params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, getSoundChannel().streamType);
            params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, getVolume());
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "DONE");
            if (tts.speak(speak, TextToSpeech.QUEUE_FLUSH, params, UUID.randomUUID().toString()) != TextToSpeech.SUCCESS)
                return false;
        } else {
            HashMap<String, String> params = new HashMap<>();
            params.put(TextToSpeech.Engine.KEY_PARAM_STREAM, String.valueOf(getSoundChannel().streamType));
            params.put(TextToSpeech.Engine.KEY_PARAM_VOLUME, Float.toString(getVolume()));
            params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "DONE");
            if (tts.speak(speak, TextToSpeech.QUEUE_FLUSH, params) != TextToSpeech.SUCCESS)
                return false;
        }
        restart = 0;
        return true;
    }

    public void onRangeStart(String utteranceId, int start, int end, int frame) {
    }

    public void onDone(String utteranceId, Runnable done) {
        done.run();
    }

    public void onError(String utteranceId, Runnable done) {
        done.run();
    }
}
