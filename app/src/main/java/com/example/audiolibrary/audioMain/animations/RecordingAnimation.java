package com.example.audiolibrary.audioMain.animations;

import android.view.View;
import android.view.animation.Animation;

import androidx.recyclerview.widget.RecyclerView;

import com.example.audiolibrary.R;
import com.example.audiolibrary.androidlibrary.animations.ExpandAnimation;
import com.example.audiolibrary.androidlibrary.animations.MarginAnimation;

public class RecordingAnimation extends ExpandAnimation {
    public static Animation apply(final RecyclerView list, final View v, final boolean expand, boolean animate) {
        return apply(new LateCreator() {
            @Override
            public MarginAnimation create() {
                return new RecordingAnimation(list, v, expand);
            }
        }, v, expand, animate);
    }

    public RecordingAnimation(RecyclerView list, View v, boolean expand) {
        super(list, v, v.findViewById(R.id.recording_player), null, expand);
    }

    @Override
    public void expandRotate(float e) {
    }
}
