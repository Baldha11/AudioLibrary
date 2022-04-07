package com.example.audiolibrary.audioMain.encoders;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.MediaFormat;

import com.example.audiolibrary.androidlibrary.sound.AudioTrack;
import com.example.audiolibrary.audioMain.app.RawSamples;

import java.io.FileDescriptor;

@TargetApi(21)
public class Format3GP extends MuxerMP4 { // for low bitrate, AMR_NB
    public static final String EXT = "3gp";

    public static final String CONTENTTYPE_3GPP = "audio/3gpp";
    public static final int BITRATES[] = {4750, 5150, 5900, 6700, 7400, 7950, 10200, 12200};

    Resample resample;

    public Format3GP(Context context, RawSamples.Info info, FileDescriptor out) {
        MediaFormat format = new MediaFormat();

        format.setInteger(MediaFormat.KEY_PCM_ENCODING, info.format);

        int hz = info.hz;
        if (hz != 8000) {
            info = new RawSamples.Info(info);
            info.hz = 8000;
            resample = new Resample(info.format, hz, info.channels, info.hz);
        }

        format.setString(MediaFormat.KEY_MIME, CONTENTTYPE_3GPP);
        format.setInteger(MediaFormat.KEY_SAMPLE_RATE, info.hz); // 8000 only supported
        format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, info.channels);
        format.setInteger(MediaFormat.KEY_BIT_RATE, 12200); // set maximum

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
