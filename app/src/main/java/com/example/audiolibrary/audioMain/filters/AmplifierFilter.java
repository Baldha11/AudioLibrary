package com.example.audiolibrary.audioMain.filters;

import android.media.AudioFormat;

import com.example.audiolibrary.audioMain.app.Sound;

public class AmplifierFilter extends Filter {

    public static final int MAX = 4;

    double db;

    public AmplifierFilter(float amp) {
        this.db = Sound.log1(amp, MAX + 1) * MAX;
    }

    public void filter(Buffer buf) {
        int end = buf.pos + buf.len;
        for (int i = buf.pos; i < end; i++) {
            double d;
            switch (buf.buf.format) {
                case AudioFormat.ENCODING_PCM_16BIT:
                    d = buf.buf.shorts[i] * db;
                    break;
                case AudioFormat.ENCODING_PCM_FLOAT:
                    d = buf.buf.floats[i] * db;
                    break;
                default:
                    throw new RuntimeException("Unknown format");
            }
            switch (buf.buf.format) {
                case AudioFormat.ENCODING_PCM_16BIT: {
                    short s;
                    if (d > Short.MAX_VALUE)
                        s = Short.MAX_VALUE;
                    else
                        s = (short) d;
                    buf.buf.shorts[i] = s;
                    break;
                }
                case AudioFormat.ENCODING_PCM_FLOAT: {
                    buf.buf.floats[i] = (float) d;
                    break;
                }
                default:
                    throw new RuntimeException("Unknown format");
            }
        }
    }
}
