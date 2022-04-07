package com.example.audiolibrary.audioMain.widgets;

import android.content.Context;
import android.content.res.TypedArray;
import android.media.AudioRecord;
import android.preference.ListPreference;
import android.util.AttributeSet;

import com.example.audiolibrary.audioMain.app.Sound;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;

public class SampleRatePreferenceCompat extends ListPreference {
    public static String KHZ = "kHz";

    public boolean findString(String s, CharSequence[] aa) {
        for (int i = 0; i < aa.length; i++) {
            if (aa[i].equals(s))
                return true;
        }
        return false;
    }

    public boolean findInt(int k, CharSequence[] aa) {
        String m = Integer.toString(k);
        for (int i = 0; i < aa.length; i++) {
            if (aa[i].equals(m))
                return true;
        }
        return false;
    }

    public SampleRatePreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public SampleRatePreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public SampleRatePreferenceCompat(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SampleRatePreferenceCompat(Context context) {
        super(context);
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

    public LinkedHashMap<Integer, String> getSources() {
        CharSequence[] text = getEntries();
        CharSequence[] values = getEntryValues();
        LinkedHashMap<Integer, String> mm = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i++) {
            String v = values[i].toString();
            String t = text[i].toString();
            mm.put(Integer.parseInt(v), t);
        }
        return mm;
    }

    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        super.onSetInitialValue(restoreValue, defaultValue);
        LinkedHashMap<Integer, String> mm = getSources();
        for (int i = 0; i < Sound.RATES.length; i++) {
            int r = Sound.RATES[i];
            if (mm.containsKey(r))
                continue;
            mm.put(r, (r / 1000) + " kHz");
        }
        mm = filter(mm);
        ArrayList<Integer> kk = new ArrayList<>(mm.keySet());
        Collections.sort(kk);
        Collections.reverse(kk);
        LinkedHashMap<String, String> dd = new LinkedHashMap<>();
        for (Integer k : kk)
            dd.put("" + k, mm.get(k));
        setValues(dd);
    }

    public LinkedHashMap<Integer, String> filter(LinkedHashMap<Integer, String> in) {
        LinkedHashMap<Integer, String> out = new LinkedHashMap<>();
        for (Integer rate : in.keySet()) {
            int bufferSize = AudioRecord.getMinBufferSize(rate, Sound.getChannels(getContext()), Sound.getAudioFormat(getContext()));
            if (bufferSize <= 0)
                continue;
            String v = in.get(rate);
            out.put(rate, v);
        }
        return out;
    }

    public void setValues(LinkedHashMap<String, String> mm) {
        String v = getValue();
        if (mm.size() > 1) {
            setEntryValues(mm.keySet().toArray(new String[0]));
            setEntries(mm.values().toArray(new String[0]));
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

    public void update(Object value) {
        String v = (String) value;
        int i = findIndexOfValue(v);
        if (i >= 0)
            v = getEntries()[i].toString();
        setSummary(v);
    }
}
