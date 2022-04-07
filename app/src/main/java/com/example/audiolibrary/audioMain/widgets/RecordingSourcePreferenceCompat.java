package com.example.audiolibrary.audioMain.widgets;

import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.media.MediaRecorder;
import android.preference.ListPreference;
import android.provider.Settings;
import android.util.AttributeSet;


import com.example.audiolibrary.audioMain.app.Sound;

import java.util.LinkedHashMap;

public class RecordingSourcePreferenceCompat extends ListPreference {
    public static final String TAG = RecordingSourcePreferenceCompat.class.getSimpleName();

    public static void show(Context context) {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        context.startActivity(intent);
    }

    public RecordingSourcePreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        create();
    }

    public RecordingSourcePreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        create();
    }

    public RecordingSourcePreferenceCompat(Context context, AttributeSet attrs) {
        super(context, attrs);
        create();
    }

    public RecordingSourcePreferenceCompat(Context context) {
        super(context);
        create();
    }

    public LinkedHashMap<String, String> getSources() {
        CharSequence[] text = getEntries();
        CharSequence[] values = getEntryValues();
        LinkedHashMap<String, String> mm = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i++) {
            String v = values[i].toString();
            String t = text[i].toString();
            mm.put(v, t);
        }
        return mm;
    }

    public boolean isSourceSupported(int s) {
        if (s == MediaRecorder.AudioSource.UNPROCESSED && !Sound.isUnprocessedSupported(getContext()))
            return false;
        return true;
    }

    public LinkedHashMap<String, String> filter(LinkedHashMap<String, String> mm) {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        for (String v : mm.keySet()) {
            String t = mm.get(v);
            Integer s = Integer.parseInt(v);
            if (!isSourceSupported(s))
                continue;
            map.put(v, t);
        }
        return map;
    }

    public void setValues(LinkedHashMap<String, String> mm) {
        String v = getValue();
        if (mm.size() > 1) {
            setEntryValues(mm.keySet().toArray(new CharSequence[0]));
            setEntries(mm.values().toArray(new CharSequence[0]));
            int i = findIndexOfValue(v);
            if (i == -1)
                setValueIndex(0);
            else
                setValueIndex(i);
        } else {
//            setVisible(false);
        }
        update(v); // defaultValue null after defaults set
    }

    public void create() {
        LinkedHashMap<String, String> mm = getSources();
        mm = filter(mm);
        setValues(mm);
    }

    @Override
    public boolean callChangeListener(Object newValue) {
        update(newValue);
        return super.callChangeListener(newValue);
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        Object def = super.onGetDefaultValue(a, index);
        update(def);
        return def;
    }

    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        super.onSetInitialValue(restoreValue, defaultValue);
        update(getValue()); // defaultValue null after defaults set
    }

    public void update(Object value) {
        String v = (String) value;
        int i = findIndexOfValue(v);
        if (i >= 0)
            v = getEntries()[i].toString();
        setSummary(v);
    }

    public void onResume() {
        update(getValue());
    }
}
