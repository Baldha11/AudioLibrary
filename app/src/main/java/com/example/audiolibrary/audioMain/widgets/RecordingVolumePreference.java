package com.example.audiolibrary.audioMain.widgets;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.os.Bundle;

import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.widget.TextViewCompat;
import androidx.fragment.app.Fragment;

import com.example.audiolibrary.R;
import com.example.audiolibrary.androidlibrary.preferences.SeekBarPreference;
import com.example.audiolibrary.androidlibrary.widgets.ThemeUtils;
import com.example.audiolibrary.audioMain.filters.AmplifierFilter;

public class RecordingVolumePreference extends SeekBarPreference {

    public static void show(Fragment f, String key) {
        DialogFragment d = DialogFragment.newInstance(key);
        d.setTargetFragment(f, 0);
        d.show(f.getFragmentManager(), "android.support.v7.preference.PreferenceFragment.DIALOG");
    }

    public static class DialogFragment extends SeekBarPreferenceDialogFragment {

        public static DialogFragment newInstance(String key) {
            DialogFragment fragment = new DialogFragment();
            Bundle b = new Bundle(1);
            b.putString("key", key);
            fragment.setArguments(b);
            return fragment;
        }

        @SuppressLint("RestrictedApi")
        @Override
        protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
            super.onPrepareDialogBuilder(builder);
            seekBar.setMax(AmplifierFilter.MAX * 100);
            seekBar.setProgress((int) (value * 100));
            TextView t = new TextView(getContext());
//            TextViewCompat.setTextAppearance(t, R.style.TextAppearance_AppCompat_Caption);
            t.setText(R.string.recording_volume_text);
            int p = ThemeUtils.dp2px(getContext(), 10);
            t.setPadding(p, p, p, p);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.gravity = Gravity.CENTER;
            layout.addView(t, lp);
            builder.setNeutralButton(R.string.default_button, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                }
            });
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final AlertDialog d = (AlertDialog) super.onCreateDialog(savedInstanceState);
            d.setOnShowListener(new DialogInterface.OnShowListener() {
                @Override
                public void onShow(DialogInterface dialog) {
                    Button b = d.getButton(DialogInterface.BUTTON_NEUTRAL);
                    b.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            mPreferenceChanged = true;
                            value = 1;
                            seekBar.setProgress((int) (value * 100));
//                            updateText();
                        }
                    });
                }
            });
            return d;
        }
    }

    public RecordingVolumePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public String format(float value) {
        return super.format(value);
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

    @Override
    public boolean callChangeListener(Object newValue) {
        update((Float) newValue);
        return super.callChangeListener(newValue);
    }

    public void update(Object value) {
        Float v = (Float) value;
        setSummary(format(v));
    }
}
