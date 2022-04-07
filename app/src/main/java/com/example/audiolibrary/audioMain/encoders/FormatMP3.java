package com.example.audiolibrary.audioMain.encoders;

import android.content.Context;
import android.media.AudioFormat;

import com.example.audiolibrary.androidlibrary.app.Natives;
import com.example.audiolibrary.androidlibrary.sound.AudioTrack;
import com.example.audiolibrary.audioMain.app.RawSamples;
import com.github.axet.lamejni.Config;
import com.github.axet.lamejni.Lame;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class FormatMP3 implements Encoder {
    public static final String EXT = "mp3";

    FileOutputStream writer;
    FileChannel fc;
    Lame lame;

    public static void natives(Context context) {
        if (Config.natives) {
            Natives.loadLibraries(context, "lame", "lamejni");
            Config.natives = false;
        }
    }

    public static boolean supported(Context context) {
        try {
            FormatMP3.natives(context);
            Lame v = new Lame();
            return true;
        } catch (NoClassDefFoundError | ExceptionInInitializerError | UnsatisfiedLinkError e) {
            return false;
        }
    }

    public FormatMP3(Context context, RawSamples.Info info, FileDescriptor out) {
        natives(context);
        lame = new Lame();
        int b = Factory.getBitrate(info.hz) / 1000;
        lame.open(info.channels, info.hz, b, 4);
        writer = new FileOutputStream(out);
        fc = writer.getChannel();
    }

    @Override
    public void encode(AudioTrack.SamplesBuffer buf, int pos, int len) {
        byte[] bb;
        switch (buf.format) {
            case AudioFormat.ENCODING_PCM_16BIT:
                bb = lame.encode(buf.shorts, pos, len);
                break;
            case AudioFormat.ENCODING_PCM_FLOAT:
                bb = lame.encode_float(buf.floats, pos, len);
                break;
            default:
                throw new RuntimeException("Unknown format");
        }
        try {
            fc.write(ByteBuffer.wrap(bb));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        try {
            byte[] bb = lame.encode(null, 0, 0);
            fc.write(ByteBuffer.wrap(bb));
            bb = lame.close();
            fc.position(0);
            fc.write(ByteBuffer.wrap(bb));
            writer.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
