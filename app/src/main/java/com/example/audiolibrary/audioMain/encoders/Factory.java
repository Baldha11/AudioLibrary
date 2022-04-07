package com.example.audiolibrary.audioMain.encoders;

import android.content.Context;
import android.os.Build;


import com.example.audiolibrary.audioMain.app.RawSamples;

import java.io.FileDescriptor;

public class Factory {

    public static String[] ENCODERS = new String[]{FormatOGG.EXT,
            FormatWAV.EXT,
            FormatFLAC.EXT,
            Format3GP.EXT,
            FormatAMR.EXT,
            FormatM4A.EXT,
            FormatMKA_AAC.EXT,
            FormatMP3.EXT,
            FormatOPUS.EXT,
            FormatOPUS.EXT
    };

    public static int getBitrate(int hz) {
        if (hz < 16000)
            return 32000;
        else if (hz < 44100)
            return 64000;
        else
            return 128000;
    }

    public static boolean isEncoderSupported(Context context, String ext) {
        if (FormatOGG.supported(context)) {
            if (ext.equals(FormatOGG.EXT))
                return true;
        }
        if (ext.equals(FormatWAV.EXT))
            return true;
        if (Build.VERSION.SDK_INT >= 21) {
            if (ext.equals(Format3GP.EXT))
                return false;
        }
        if (Build.VERSION.SDK_INT >= 21) {
            if (ext.equals(FormatAMR.EXT))
                return false;
        }
        if (Build.VERSION.SDK_INT >= 18) {
            if (ext.equals(FormatM4A.EXT))
                return false;
        }
        if (Build.VERSION.SDK_INT >= 16) {
            if (ext.equals(FormatMKA_AAC.EXT))
                return false; // not supporting 24-bin PCM (float)
        }
        if (FormatMP3.supported(context)) {
            if (ext.equals(FormatMP3.EXT))
                return true;
        }
        if (ext.equals(FormatFLAC.EXT))
            return true;
        if (ext.equals(FormatOPUS.EXT)) {
            if (Build.VERSION.SDK_INT >= 23) { // Android 6.0 (has ogg/opus support) https://en.wikipedia.org/wiki/Opus_(audio_format)
                if (FormatOPUS_OGG.supported(context))
                    return true; // android6+ supports ogg/opus
            } else if (Build.VERSION.SDK_INT >= 21) { // android 5.0 (has mka/opus support only)
                if (FormatOPUS_MKA.supported(context))
                    return true; // android6+ supports ogg/opus
            }
        }
        return false;
    }

    public static Encoder getEncoder(Context context, String ext, RawSamples.Info info, FileDescriptor out) {
        if (ext.equals(FormatOGG.EXT))
            return new FormatOGG(context, info, out);
        if (ext.equals(FormatWAV.EXT))
            return new FormatWAV(info, out);
        if (ext.equals(Format3GP.EXT))
            return new Format3GP(context, info, out);
        if (ext.equals(FormatAMR.EXT))
            return new FormatAMR(context, info, out);
        if (ext.equals(FormatM4A.EXT))
            return new FormatM4A(context, info, out);
        if (ext.equals(FormatMKA_AAC.EXT))
            return new FormatMKA_AAC(info, out); // not supporting 24-bin PCM (float)
        if (ext.equals(FormatMP3.EXT))
            return new FormatMP3(context, info, out);
        if (ext.equals(FormatFLAC.EXT))
            return new FormatFLAC(info, out);
        if (ext.equals(FormatOPUS.EXT)) {
            if (Build.VERSION.SDK_INT >= 23) // Android 6.0 (has ogg/opus support) https://en.wikipedia.org/wiki/Opus_(audio_format)
                return new FormatOPUS_OGG(context, info, out); // android6+ supports ogg/opus
            else if (Build.VERSION.SDK_INT >= 21) // android 5.0 (has mka/opus support only)
                return new FormatOPUS_MKA(context, info, out); // android6+ supports ogg/opus
        }
        return null;
    }

    public static long getEncoderRate(int format, String ext, int rate) {
        if (ext.equals(FormatM4A.EXT)) {
            long y1 = 365723; // one minute sample 16000Hz
            long x1 = 16000; // at 16000
            long y2 = 493743; // one minute sample
            long x2 = 44000; // at 44000
            long x = rate;
            long y = (x - x1) * (y2 - y1) / (x2 - x1) + y1;
            return y / 60;
        }

        if (ext.equals(FormatMKA_AAC.EXT)) { // same codec as m4a, but different container
            long y1 = 365723; // one minute sample 16000Hz
            long x1 = 16000; // at 16000
            long y2 = 493743; // one minute sample
            long x2 = 44000; // at 44000
            long x = rate;
            long y = (x - x1) * (y2 - y1) / (x2 - x1) + y1;
            return y / 60;
        }

        if (ext.equals(FormatOGG.EXT)) {
            long y1 = 174892; // one minute sample 16000Hz
            long x1 = 16000; // at 16000
            long y2 = 405565; // one minute sample
            long x2 = 44000; // at 44000
            long x = rate;
            long y = (x - x1) * (y2 - y1) / (x2 - x1) + y1;
            return y / 60;
        }

        if (ext.equals(FormatMP3.EXT)) {
            long y1 = 376344; // one minute sample 16000Hz
            long x1 = 16000; // at 16000
            long y2 = 464437; // one minute sample
            long x2 = 44000; // at 44000
            long x = rate;
            long y = (x - x1) * (y2 - y1) / (x2 - x1) + y1;
            return y / 60;
        }

        if (ext.equals(FormatFLAC.EXT)) {
            long y1 = 1060832; // one minute sample 16000Hz
            long x1 = 16000; // at 16000
            long y2 = 1296766; // one minute sample
            long x2 = 44000; // at 44000
            long x = rate;
            long y = (x - x1) * (y2 - y1) / (x2 - x1) + y1;
            return y / 60;
        }

        if (ext.equals(FormatOPUS.EXT)) {
            long y1 = 202787; // one minute sample 16000Hz
            long x1 = 16000; // at 16000
            long y2 = 319120; // one minute sample
            long x2 = 44000; // at 44000
            long x = rate;
            long y = (x - x1) * (y2 - y1) / (x2 - x1) + y1;
            return y / 60;
        }

        if (ext.startsWith(Format3GP.EXT)) {
            long y1 = 119481; // one minute sample 16000Hz
            long x1 = 16000; // at 16000
            long y2 = 119481; // one minute sample
            long x2 = 44000; // at 44000
            long x = rate;
            long y = (x - x1) * (y2 - y1) / (x2 - x1) + y1;
            return y / 60;
        }

        if (ext.startsWith("aac")) {
            long y1 = 104276; // one minute sample 16000Hz
            long x1 = 16000; // at 16000
            long y2 = 104276; // one minute sample
            long x2 = 44000; // at 44000
            long x = rate;
            long y = (x - x1) * (y2 - y1) / (x2 - x1) + y1;
            return y / 60;
        }

        // default raw
        int c = RawSamples.getBytes(format);
        return c * rate;
    }
}
