package com.example.audiolibrary.audioMain.encoders;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.AudioFormat;

import com.example.audiolibrary.androidlibrary.app.Natives;
import com.example.audiolibrary.androidlibrary.sound.AudioTrack;
import com.example.audiolibrary.audioMain.app.RawSamples;
import com.github.axet.lamejni.Config;
import com.github.axet.opusjni.Opus;

import java.io.FileDescriptor;
import java.nio.ByteBuffer;

@TargetApi(21)
public class FormatOPUS implements Encoder {
    public static final String TAG = FormatOPUS.class.getSimpleName();

    public static final String EXT = "opus";

    RawSamples.Info info;
    Opus opus;
    long NumSamples;
    AudioTrack.SamplesBuffer left;
    int frameSize = 960; // default 20ms
    int hz; // encoding hz (info.hz - input, maybe lowered by resampler)
    Resample resample;

    public static void natives(Context context) {
        if (Config.natives) {
            Natives.loadLibraries(context, "opus", "opusjni");
            Config.natives = false;
        }
    }

    public static boolean supported(Context context) {
        try {
            FormatOPUS.natives(context);
            Opus v = new Opus();
            return true;
        } catch (NoClassDefFoundError | ExceptionInInitializerError | UnsatisfiedLinkError e) {
            return false;
        }
    }

    public static int getBitrate(int hz) { // https://wiki.xiph.org/index.php?title=Opus_Recommended_Settings
        if (hz < 16000)
            return 16000; // 0 - 16Hz
        else if (hz < 44100)
            return 24000; // 16 - 44Hz
        else
            return 32000; // 48Hz
    }

    public static int match(int hz) { // opus supports only selected Hz's
        int[] hh = new int[]{
                // 8000, 12000, 16000, // 8Hz && 12Hz && 16Hz crashing MediaPlayer https://gitlab.com/axet/android-audio-recorder/issues/23
                24000,
                48000,
        };
        int i = Integer.MAX_VALUE;
        int r = 0;
        for (int h : hh) {
            int d = Math.abs(hz - h);
            if (d <= i) { // higher is better
                i = d;
                r = h;
            }
        }
        return r;
    }

    public FormatOPUS(Context context, RawSamples.Info info, FileDescriptor out) {
        natives(context);
        create(info, out);
    }

    public void create(final RawSamples.Info info, FileDescriptor out) {
        this.info = info;
        this.hz = match(info.hz);

        if (hz != info.hz)
            resample = new Resample(info.format, info.hz, info.channels, hz);

        opus = new Opus();
        opus.open(info.channels, hz, getBitrate(info.hz));
    }

    @Override
    public void encode(AudioTrack.SamplesBuffer buf, int pos, int len) {
        if (resample != null) {
            resample.write(buf, pos, len);
            resample();
            return;
        }
        encode2(buf, pos, len);
    }

    void encode2(AudioTrack.SamplesBuffer buf, int pos, int len) {
        if (left != null) {
            AudioTrack.SamplesBuffer ss = new AudioTrack.SamplesBuffer(info.format, left.pos + len);
            left.flip();
            ss.put(left);
            ss.put(buf, pos, len);
            buf = ss;
            pos = 0;
            len = ss.pos;
        }
        if (frameSize == 0) {
            if (len < 240) {
                frameSize = 120;
            } else if (len < 480) {
                frameSize = 240;
            } else if (len < 960) {
                frameSize = 480;
            } else if (len < 1920) {
                frameSize = 960;
            } else if (len < 2880) {
                frameSize = 1920;
            } else {
                frameSize = 2880;
            }
        }
        int frameSizeStereo = frameSize * info.channels;
        int lenEncode = len / frameSizeStereo * frameSizeStereo;
        int end = pos + lenEncode;
        for (int p = pos; p < end; p += frameSizeStereo) {
            byte[] bb;
            switch (buf.format) {
                case AudioFormat.ENCODING_PCM_16BIT:
                    bb = opus.encode(buf.shorts, p, frameSizeStereo);
                    break;
                case AudioFormat.ENCODING_PCM_FLOAT:
                    bb = opus.encode_float(buf.floats, p, frameSizeStereo);
                    break;
                default:
                    throw new RuntimeException("Unknown format");
            }
            encode(ByteBuffer.wrap(bb), frameSize);
            NumSamples += frameSizeStereo / info.channels;
        }
        int diff = len - lenEncode;
        if (diff > 0) {
            left = new AudioTrack.SamplesBuffer(info.format, diff);
            left.put(buf, end, diff);
        } else {
            left = null;
        }
    }

    void resample() {
        int len;
        while ((len = resample.read(resample.buf)) > 0) {
            resample.buf.flip();
            encode2(resample.buf, 0, len);
        }
    }

    void encode(ByteBuffer bb, long dur) { // empty, override
    }

    public void close() {
        if (resample != null) {
            resample.end();
            resample();
            resample.close();
            resample = null;
        }
        opus.close();
    }

    long getCurrentTimeStamp() {
        return NumSamples * 1000 / info.hz;
    }

    public RawSamples.Info getInfo() {
        return info;
    }
}
