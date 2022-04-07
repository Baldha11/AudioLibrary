package com.example.audiolibrary.audioMain.encoders;

import android.util.Log;

import com.example.audiolibrary.androidlibrary.sound.AudioTrack;
import com.example.audiolibrary.audioMain.app.RawSamples;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.ByteOrder;

import vavi.sound.pcm.resampling.ssrc.SSRC;


public class Resample {
    public static final String TAG = Resample.class.getSimpleName();

    public static final ByteOrder ORDER = ByteOrder.LITTLE_ENDIAN;
    public static final int PIPE_SIZE = 100 * 1024;

    Thread thread;
    PipedOutputStream os;
    PipedInputStream is;
    RuntimeException delayed;
    RawSamples rs;
    AudioTrack.SamplesBuffer buf;

    public Resample(int format, final int sampleRate, final int channels, final int hz) {
        try {
            buf = new AudioTrack.SamplesBuffer(format, 1000);
            os = new PipedOutputStream();
            is = new PipedInputStream(PIPE_SIZE);
            rs = new RawSamples(is, os, new RawSamples.Info(format, sampleRate, channels), ORDER, 1000);
            final PipedInputStream pis = new PipedInputStream(os);
            final PipedOutputStream pos = new PipedOutputStream(is);
            final int c = RawSamples.getBytes(rs.info.format);
            thread = new Thread("SSRC") {
                @Override
                public void run() {
                    try {
                        SSRC ssrc = new SSRC(pis, pos, sampleRate, hz, c, c, channels, Integer.MAX_VALUE, 0, 0, true);
                    } catch (RuntimeException e) {
                        Log.d(TAG, "SSRC failed", e);
                        delayed = e;
                    } catch (IOException e) {
                        Log.d(TAG, "SSRC failed", e);
                        delayed = new RuntimeException(e);
                    }
                }
            };
            thread.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void end() {
        if (delayed != null)
            throw delayed;
        try {
            is.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void write(AudioTrack.SamplesBuffer buf, int pos, int len) {
        if (delayed != null)
            throw delayed;
        rs.write(buf, pos, len);
        try {
            os.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public int read(AudioTrack.SamplesBuffer b) {
        if (delayed != null)
            throw delayed;
        try {
            int len = is.available();
            if (len <= 0)
                return len;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return rs.read(b);
    }

    public void close() {
        if (thread != null) {
            thread.interrupt();
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            thread = null;
        }
    }
}
