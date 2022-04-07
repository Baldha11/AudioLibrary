package com.example.audiolibrary.audioMain.encoders;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.MediaFormat;

import com.example.audiolibrary.androidlibrary.sound.AudioTrack;
import com.example.audiolibrary.audioMain.app.RawSamples;

import java.io.FileDescriptor;

@TargetApi(21)
public class FormatAMR extends MuxerMP4 { // for high bitrate AMR_WB
    public static final String EXT = "amr";

    public static final String CONTENTTYPE_AMRWB = "audio/amr-wb";
    public static final int BITRATES[] = {6600, 8850, 12650, 14250, 15850, 18250, 19850, 23050, 23850};

    Resample resample;

    public FormatAMR(Context context, RawSamples.Info info, FileDescriptor out) {
        MediaFormat format = new MediaFormat();

        int hz = info.hz;
        if (hz != 16000) {
            info = new RawSamples.Info(info);
            info.hz = 16000;
            resample = new Resample(info.format, hz, info.channels, info.hz);
        }

        format.setInteger(MediaFormat.KEY_PCM_ENCODING, info.format);

        format.setString(MediaFormat.KEY_MIME, CONTENTTYPE_AMRWB);
        format.setInteger(MediaFormat.KEY_SAMPLE_RATE, info.hz); // only 16000 supported
        format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, info.channels);
        format.setInteger(MediaFormat.KEY_BIT_RATE, 23850); // set maximum

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
