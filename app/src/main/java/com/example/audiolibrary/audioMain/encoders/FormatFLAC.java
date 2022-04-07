package com.example.audiolibrary.audioMain.encoders;

import android.media.AudioFormat;

import com.example.audiolibrary.androidlibrary.sound.AudioTrack;
import com.example.audiolibrary.audioMain.app.RawSamples;

import net.sourceforge.javaflacencoder.EncodingConfiguration;
import net.sourceforge.javaflacencoder.FLACEncoder;
import net.sourceforge.javaflacencoder.FLACFileOutputStream;
import net.sourceforge.javaflacencoder.StreamConfiguration;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;

// compile 'com.github.axet:java-flac-encoder:0.3.8'
public class FormatFLAC implements Encoder {
    public static final String EXT = "flac";

    RawSamples.Info info;
    FLACEncoder flacEncoder;
    FLACFileOutputStream flacOutputStream;
    int bpsMax;

    public FormatFLAC(RawSamples.Info info, FileDescriptor out) {
        this.info = info;

        StreamConfiguration sc = new StreamConfiguration();
        sc.setSampleRate(info.hz);
        int bps = info.bps;
        if (bps > StreamConfiguration.MAX_BITS_PER_SAMPLE)
            bps = StreamConfiguration.MAX_BITS_PER_SAMPLE; // library limitation
        bpsMax = (int) Math.pow(2, bps);
        sc.setBitsPerSample(bps);
        sc.setChannelCount(info.channels);

        EncodingConfiguration ec = new EncodingConfiguration();
        ec.setSubframeType(EncodingConfiguration.SubframeType.LPC);

        try {
            flacEncoder = new FLACEncoder();
            flacOutputStream = new FLACFileOutputStream(new FileOutputStream(out));
            flacEncoder.setStreamConfiguration(sc);
            flacEncoder.setEncodingConfiguration(ec);
            flacEncoder.setOutputStream(flacOutputStream);
            flacEncoder.openFLACStream();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void encode(AudioTrack.SamplesBuffer buf, int pos, int buflen) {
        try {
            int[] ii = new int[buflen];
            int end = pos + buflen;
            for (int i = pos; i < end; i++) {
                switch (buf.format) {
                    case AudioFormat.ENCODING_PCM_16BIT:
                        ii[i] = buf.shorts[i];
                        break;
                    case AudioFormat.ENCODING_PCM_FLOAT:
                        ii[i] = (int) (buf.floats[i] * bpsMax);
                        break;
                    default:
                        throw new RuntimeException("Unknown format");
                }
            }
            int count = buflen / info.channels;
            flacEncoder.addSamples(ii, count);
            flacEncoder.encodeSamples(count, false);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        try {
            flacEncoder.encodeSamples(flacEncoder.samplesAvailableToEncode(), true);
            flacOutputStream.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
