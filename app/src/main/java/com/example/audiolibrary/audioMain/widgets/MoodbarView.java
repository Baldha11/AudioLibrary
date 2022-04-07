package com.example.audiolibrary.audioMain.widgets;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.AudioFormat;
import android.net.Uri;

import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.audiolibrary.androidlibrary.widgets.ThemeUtils;
import com.example.audiolibrary.audioMain.app.RawSamples;
import com.example.audiolibrary.audioMain.app.Sound;
import com.example.audiolibrary.audioMain.encoders.MediaDecoderCompat;

import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;

public class MoodbarView extends View {
    double[] banks = new double[0];
    double[] samples = new double[0];
    int p;
    int s;
    int ps;
    Paint paint;

    public static double[] getMoodbar(final Context context, Uri uri) throws IOException {
        MediaDecoderCompat decoder = MediaDecoderCompat.create(context, uri);
        Seeker os = new Seeker(decoder, 300);
        double[] banks = os.decode();
        os.close();
        decoder.close();
        return banks;
    }

    public static void saveMoodbar(double[] data, File cover) throws JSONException, IOException {
        JSONArray a = new JSONArray();
        for (double d : data)
            a.put(d);
        FileUtils.write(cover, a.toString(), Charset.defaultCharset());
    }

    public static double[] loadMoodbar(File cover) throws JSONException, IOException {
        String s = FileUtils.readFileToString(cover, Charset.defaultCharset());
        JSONArray a = new JSONArray(s);
        double[] data = new double[a.length()];
        for (int i = 0; i < a.length(); i++)
            data[i] = a.optDouble(i);
        return data;
    }

    public static class Seeker extends OutputStream {
        double sum = 0;
        int samples = 0;
        int i = 0;
        double[] banks;
        long current; // Us
        long part; // Us
        long d; // Us
        MediaDecoderCompat decoder;
        RawSamples.Info info;

        public Seeker(MediaDecoderCompat decoder, int wps) throws IOException {
            this.decoder = decoder;
            d = 1000L * decoder.getDuration();
            if (d == 0)
                throw new IOException("duration == 0");
            banks = new double[wps];
//            info = decoder.getInfo();
            part = d * info.channels / banks.length;
        }

        double[] decode() throws IOException {
            decoder.decode(this);
            return banks;
        }

        @Override
        public void write(int b) throws IOException {
            long l = decoder.getSampleTime() * 1000;
            push(b, l);
        }

        @Override
        public void write(@NonNull byte[] b, int off, int len) throws IOException {
            long l = decoder.getSampleTime() * 1000;
            boolean a = false;
            switch (info.format) {
                case AudioFormat.ENCODING_PCM_FLOAT: {
                    float[] ss = MediaDecoderCompat.asFloatBuffer(b, off, len);
                    for (float s : ss)
                        a |= push(s, l);
                    break;
                }
                case AudioFormat.ENCODING_PCM_16BIT: {
                    short[] ss = MediaDecoderCompat.asShortBuffer(b, off, len);
                    for (short s : ss)
                        a |= push(s, l);
                    break;
                }
                default:
                    throw new RuntimeException("Unknown format");
            }
            if (a && part > 1 * 1000 * 1000 && current < d) {
                current += part;
                decoder.seekTo(current / 1000);
            }
        }

        boolean push(double s, long l) {
            sum += s * s;
            samples++;
            if (d / banks.length <= 1000 * 1000 || samples / info.hz > 1) {
                pack(l);
                return true;
            }
            return false;
        }

        void pack(long l) {
            if (samples == 0)
                return;
            if (i >= banks.length)
                return;
            int ke = (int) (banks.length * l / d);
            if (ke <= i)
                return;
            if (ke > banks.length)
                ke = banks.length;
            int max;
            switch (info.format) {
                case AudioFormat.ENCODING_PCM_FLOAT: {
                    max = 1;
                    break;
                }
                case AudioFormat.ENCODING_PCM_16BIT: {
                    max = Short.MAX_VALUE;
                    break;
                }
                default:
                    throw new RuntimeException("Unknown format");
            }
            double d = Math.sqrt(sum / samples) / max;
            for (int k = i; k < ke; k++)
                banks[k] = d;
            i = ke;
            sum = 0;
            samples = 0;
        }

        @Override
        public void close() throws IOException {
            super.close();
            pack(d);
        }
    }

    public MoodbarView(Context context) {
        super(context);
        create();
    }

    public MoodbarView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        create();
    }

    public MoodbarView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        create();
    }

    @TargetApi(21)
    public MoodbarView(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        create();
    }

    void create() {
        p = ThemeUtils.dp2px(getContext(), 2);
        s = ThemeUtils.dp2px(getContext(), 1);
        ps = p + s;
        paint = new Paint();
        paint.setColor(ThemeUtils.getThemeColor(getContext(), android.R.attr.colorForeground));
        paint.setStyle(Paint.Style.FILL);
    }

    public void setData(double[] dd) {
        this.banks = dd;
        this.samples = new double[0];
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int h = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int steps = getWidth() / ps;
        if (samples.length != steps) {
            samples = new double[steps];
            RawSamples.getAmplitude(banks, samples);
        }
        Rect r = new Rect();
        int off = 0; // draw offset
        for (double sample : samples) {
            double db = RawSamples.getDB(sample);
            db = Sound.MAXIMUM_DB + db;
            db = db / Sound.MAXIMUM_DB;
            int hh = getHeight() / 2; // half height
            int dh = Math.max((int) (hh * db), s); // db height
            r.left = off;
            r.right = off + p;
            r.top = hh - dh;
            r.bottom = hh + dh;
            off = off + ps;
            canvas.drawRect(r, paint);
        }
    }
}
