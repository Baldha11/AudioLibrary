package com.example.audiolibrary.audioMain.filters;

import android.media.AudioFormat;

import com.example.audiolibrary.audioMain.app.RawSamples;

import java.util.ArrayList;

import uk.me.berndporr.iirj.Butterworth;


public class VoiceFilter extends Filter {
    RawSamples.Info info;
    ArrayList<Butterworth> bb = new ArrayList<>();

    public VoiceFilter(RawSamples.Info info) {
        this.info = info;
        for (int i = 0; i < info.channels; i++) {
            Butterworth b = new Butterworth();
            b.bandPass(2, info.hz, 1650, 2700);
            bb.add(b);
        }
    }

    @Override
    public void filter(Buffer buf) {
        for (int i = 0; i < buf.len; i++) {
            int c = i % info.channels;
            Butterworth b = bb.get(c);
            int pos = buf.pos + i;
            double d;
            switch (buf.buf.format) {
                case AudioFormat.ENCODING_PCM_16BIT:
                    d = buf.buf.shorts[pos] / (double) Short.MAX_VALUE;
                    break;
                case AudioFormat.ENCODING_PCM_FLOAT:
                    d = buf.buf.floats[pos];
                    break;
                default:
                    throw new RuntimeException("Unknown format");
            }
            d = b.filter(d);
            switch (buf.buf.format) {
                case AudioFormat.ENCODING_PCM_16BIT:
                    buf.buf.shorts[pos] = (short) (d * Short.MAX_VALUE);
                    break;
                case AudioFormat.ENCODING_PCM_FLOAT:
                    buf.buf.floats[pos] = (float) d;
                    break;
                default:
                    throw new RuntimeException("Unknown format");
            }
        }
    }
}
