package com.example.audiolibrary.audioMain.widgets;

import android.content.Context;
import android.content.res.TypedArray;
import android.preference.ListPreference;
import android.util.AttributeSet;


import com.example.audiolibrary.audioMain.encoders.Factory;

import java.util.LinkedHashMap;

public class EncodingsPreferenceCompat extends ListPreference {

    public EncodingsPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public EncodingsPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public EncodingsPreferenceCompat(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public EncodingsPreferenceCompat(Context context) {
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

    public LinkedHashMap<String, String> getSources() {
        CharSequence[] text = getEntries();
        CharSequence[] values = getEntryValues();
        LinkedHashMap<String, String> mm = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i++) {
            String v = values[i].toString();
            String t = text[i].toString();
            mm.put(v, t);
        }
        for (int i = 0; i < Factory.ENCODERS.length; i++) {
            String v = Factory.ENCODERS[i];
            String t = "." + v;
            if (mm.containsKey(v))
                continue;
            mm.put(v, t);
        }
        return mm;
    }

    public boolean isEncoderSupported(String v) {
        return Factory.isEncoderSupported(getContext(), v);
    }

    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        super.onSetInitialValue(restoreValue, defaultValue);
        LinkedHashMap<String, String> mm = getSources();
        mm = filter(mm);
        setValues(mm);
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

    public LinkedHashMap<String, String> filter(LinkedHashMap<String, String> mm) {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        for (String v : mm.keySet()) {
            String t = mm.get(v);
            if (!isEncoderSupported(v))
                continue;
            map.put(v, t);
        }
        return map;
    }

    public void update(Object value) {
        String v = (String) value;
        setSummary("." + v);
    }
}
