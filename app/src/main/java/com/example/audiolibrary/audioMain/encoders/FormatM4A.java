package com.example.audiolibrary.audioMain.encoders;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;

import com.example.audiolibrary.androidlibrary.sound.AudioTrack;
import com.example.audiolibrary.audioMain.app.RawSamples;

import java.io.FileDescriptor;
import java.util.Map;

@TargetApi(18)
public class FormatM4A extends MuxerMP4 {
    public static final String EXT = "m4a";

    public static final String CONTENTTYPE_MP4 = "audio/mp4";
    public static final String CONTENTTYPE_MP4A = "audio/mp4a-latm";

    Resample resample;

    public FormatM4A(Context context, RawSamples.Info info, FileDescriptor out) {
        Map<String, MediaCodecInfo> map = MuxerMP4.findEncoder(CONTENTTYPE_MP4);
        if (map.isEmpty())
            throw new RuntimeException("mp4 not supported");

        int hz = info.hz;
        if (hz > 96000) {
            info = new RawSamples.Info(info);
            info.hz = 96000;
            resample = new Resample(info.format, hz, info.channels, info.hz);
        }

        MediaFormat format = MuxerMP4.getDefault(CONTENTTYPE_MP4A, map);
        format.setInteger(MediaFormat.KEY_SAMPLE_RATE, info.hz); // not supporting above 96 kHz
        format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, info.channels);
        format.setInteger(MediaFormat.KEY_BIT_RATE, Factory.getBitrate(info.hz));
        format.setInteger(MediaFormat.KEY_PCM_ENCODING, info.format);
        create(context, info, format, out);
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

    void resample() {
        int len;
        while ((len = resample.read(resample.buf)) > 0) {
            resample.buf.flip();
            encode2(resample.buf, 0, len);
        }
    }

    public void close() {
        if (resample != null) {
            resample.end();
            resample();
            resample.close();
            resample = null;
        }
        super.close();
    }
}
