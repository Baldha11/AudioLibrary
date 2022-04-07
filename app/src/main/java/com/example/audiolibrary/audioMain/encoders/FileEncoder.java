package com.example.audiolibrary.audioMain.encoders;

import android.content.Context;
import android.os.Handler;
import android.util.Log;

import com.example.audiolibrary.androidlibrary.sound.AudioTrack;
import com.example.audiolibrary.audioMain.app.RawSamples;
import com.example.audiolibrary.audioMain.filters.Filter;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class FileEncoder {
    public static final String TAG = FileEncoder.class.getSimpleName();

    Context context;
    Handler handler = new Handler();

    public File in;
    public Encoder encoder;
    RawSamples rs;
    Thread thread;
    long samples;
    long cur;
    Throwable t;
    final AtomicBoolean pause = new AtomicBoolean(false);
    public ArrayList<Filter> filters = new ArrayList<>();

    public FileEncoder(Context context, File in, RawSamples.Info info, Encoder encoder) {
        this.context = context;
        this.in = in;
        this.rs = new RawSamples(in, info);
        this.encoder = encoder;
    }

    public void run(final Runnable progress, final Runnable done, final Runnable error) {
        thread = new Thread("FileEncoder") {
            @Override
            public void run() {
                cur = 0;
                try {
                    samples = rs.getSamples();
                    AudioTrack.SamplesBuffer buf = new AudioTrack.SamplesBuffer(rs.info.format, 1000);
                    rs.open(buf.capacity); // FileNotFoundException if sdcard removed?
                    while (true) {
                        try {
                            synchronized (pause) {
                                if (pause.get())
                                    pause.wait();
                            }
                        } catch (InterruptedException e) {
                            return;
                        }
                        int len = rs.read(buf);
                        if (len > 0) {
                            Filter.Buffer b = new Filter.Buffer(buf, 0, len);
                            for (Filter f : filters)
                                f.filter(b);
                            if (b.len > 0)
                                encoder.encode(b.buf, b.pos, b.len);
                            handler.post(progress);
                            synchronized (thread) {
                                cur += b.len;
                            }
                        } else {
                            break;
                        }
                    }
                    closeFiles();
                    handler.post(done);
                } catch (RuntimeException e) {
                    Log.e(TAG, "FileEncoder Exception", e);
                    t = e;
                    handler.post(error);
                }
            }
        };
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
    }

    public int getProgress() {
        synchronized (thread) {
            return (int) (cur * 100 / samples);
        }
    }

    public long getCurrent() {
        synchronized (thread) {
            return cur;
        }
    }

    public long getTotal() {
        synchronized (thread) {
            return samples;
        }
    }

    public Throwable getException() {
        return t;
    }

    public void pause() {
        synchronized (pause) {
            pause.set(true);
        }
    }

    public void resume() {
        synchronized (pause) {
            pause.set(false);
            pause.notifyAll();
        }
    }

    public void closeFiles() {
        if (encoder != null) {
            encoder.close();
            encoder = null;
        }
        if (rs != null) {
            rs.close();
            rs = null;
        }
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
        closeFiles();
        handler.removeCallbacksAndMessages(null); // prevent call progress/done after encoder closed()
    }
}
