package com.example.audiolibrary.audioMain.encoders;

import android.content.Context;
import android.media.AudioFormat;

import com.example.audiolibrary.androidlibrary.app.Natives;
import com.example.audiolibrary.androidlibrary.sound.AudioTrack;
import com.example.audiolibrary.audioMain.app.RawSamples;
import com.github.axet.lamejni.Config;
import com.github.axet.vorbisjni.Vorbis;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;

public class FormatOGG implements Encoder {
    public static final String EXT = "ogg";

    FileOutputStream writer;
    Vorbis vorbis;

    public static void natives(Context context) {
        if (Config.natives) {
            Natives.loadLibraries(context, "ogg", "vorbis", "vorbisjni");
            Config.natives = false;
        }
    }

    public static boolean supported(Context context) {
        try {
            FormatOGG.natives(context);
            Vorbis v = new Vorbis();
            return true;
        } catch (NoClassDefFoundError | ExceptionInInitializerError | UnsatisfiedLinkError e) {
            return false;
        }
    }

    public FormatOGG(Context context, RawSamples.Info info, FileDescriptor out) {
        natives(context);
        vorbis = new Vorbis();
        vorbis.open(info.channels, info.hz, 0.4f);
        writer = new FileOutputStream(out);
    }

    @Override
    public void encode(AudioTrack.SamplesBuffer buf, int pos, int len) {
        byte[] bb;
        switch (buf.format) {
            case AudioFormat.ENCODING_PCM_16BIT:
                bb = vorbis.encode(buf.shorts, pos, len);
                break;
            case AudioFormat.ENCODING_PCM_FLOAT:
                bb = vorbis.encode_float(buf.floats, pos, len);
                break;
            default:
                throw new RuntimeException("Unknown format");
        }
        try {
            writer.write(bb);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        try {
            byte[] bb = vorbis.encode(null, 0, 0);
            writer.write(bb);
            writer.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        vorbis.close();
    }
}
