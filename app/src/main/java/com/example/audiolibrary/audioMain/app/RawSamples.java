package com.example.audiolibrary.audioMain.app;

import android.media.AudioFormat;

import com.example.audiolibrary.androidlibrary.sound.AudioTrack;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;

public class RawSamples {
    public static final ByteOrder ORDER = ByteOrder.BIG_ENDIAN;
    public static final int SHORT_BYTES = Short.SIZE / Byte.SIZE;
    public static final int FLOAT_BYTES = Float.SIZE / Byte.SIZE;

    public Info info;
    public File in;

    InputStream is;
    ReadBuffer readBuffer;
    OutputStream os;
    ByteOrder order;

    public static int getBytes(int format) {
        switch (format) {
            case AudioFormat.ENCODING_PCM_16BIT:
                return 2;
            case AudioFormat.ENCODING_PCM_8BIT:
                return 1;
            case Sound.ENCODING_PCM_24BIT_PACKED:
                return 3;
            case Sound.ENCODING_PCM_32BIT:
                return 4;
            case AudioFormat.ENCODING_PCM_FLOAT:
                return 4;
            default:
                throw new RuntimeException("unknown format");
        }
    }

    // get samples from bytes
    public static long getSamples(long len, int format) {
        return len / getBytes(format);
    }

    // get bytes from samples
    public static long getBufferLen(long samples, int format) {
        return samples * getBytes(format);
    }

    public static double getAmplitude(AudioTrack.SamplesBuffer buffer, int offset, int len) {
        double sum = 0;
        for (int i = offset; i < offset + len; i++) {
            switch (buffer.format) {
                case AudioFormat.ENCODING_PCM_8BIT:
                    break;
                case AudioFormat.ENCODING_PCM_16BIT:
                    sum += buffer.shorts[i] * buffer.shorts[i];
                    break;
                case Sound.ENCODING_PCM_24BIT_PACKED:
                    break;
                case Sound.ENCODING_PCM_32BIT:
                    break;
                case AudioFormat.ENCODING_PCM_FLOAT:
                    sum += buffer.floats[i] * buffer.floats[i];
                    break;
                default:
                    throw new RuntimeException("Unknown format");
            }
        }
        double a = Math.sqrt(sum / len);
        switch (buffer.format) {
            case AudioFormat.ENCODING_PCM_16BIT:
                return a / Short.MAX_VALUE;
            case AudioFormat.ENCODING_PCM_FLOAT:
                return a;
            default:
                throw new RuntimeException("Unknown format");
        }
    }

    public static double getDB(AudioTrack.SamplesBuffer buffer, int offset, int len) {
        return getDB(getAmplitude(buffer, offset, len));
    }

    public static double getDB(double amplitude) { // https://en.wikipedia.org/wiki/Sound_pressure
        return 20.0 * Math.log10(amplitude);
    }

    public static void getAmplitude(double[] banks, double[] result) { // shrink banks to result size
        int step = banks.length / result.length;
        int rem = banks.length % result.length;
        int di = 0; // data index
        int ra = 0; // reminder accumulator
        for (int i = 0; i < result.length; i++) {
            double sum = 0;
            int rs = ra / result.length; // reminder steps
            ra -= rs * result.length;
            int ke = Math.min(di + step + rs, banks.length);
            int ks = ke - di; // k sum size
            for (int k = di; k < ke; k++)
                sum = banks[k] * banks[k];
            di = ke;
            ra += rem;
            result[i] = Math.sqrt(sum / ks);
        }
    }

    public static class ReadBuffer {
        public int format;
        public int count; // samples count
        public byte[] bytes; // read/write buffer
        public ByteOrder order;

        public ReadBuffer(int format, int count, ByteOrder order) {
            this.format = format;
            this.count = count;
            this.order = order;
            switch (format) {
                case AudioFormat.ENCODING_PCM_8BIT:
                    bytes = new byte[count];
                    break;
                case AudioFormat.ENCODING_PCM_16BIT:
                    bytes = new byte[count * 2];
                    break;
                case Sound.ENCODING_PCM_24BIT_PACKED:
                    bytes = new byte[count * 3];
                    break;
                case Sound.ENCODING_PCM_32BIT:
                    bytes = new byte[count * 4];
                    break;
                case AudioFormat.ENCODING_PCM_FLOAT:
                    bytes = new byte[count * 4];
                    break;
                default:
                    throw new RuntimeException("Unknown format");
            }
        }
    }

    public static class Info {
        public int format; // AudioFormat.PCM...
        public int channels; // channels, raw data interpolated, for stereo: [0101010101...]
        public int hz; // samples per second
        public int bps; // bits per sample, signed integer

        public Info(Info i) {
            format = i.format;
            channels = i.channels;
            hz = i.hz;
            bps = i.bps;
        }

        public Info(int format, int hz, int channels, int bps) {
            this.channels = channels;
            this.hz = hz;
            this.bps = bps;
            this.format = format;
        }

        public Info(int format, int hz, int channels) {
            this.channels = channels;
            this.hz = hz;
            this.bps = getBytes(format) * Byte.SIZE;
            this.format = format;
        }

        public Info(String json) throws JSONException {
            load(new JSONObject(json));
        }

        public Info(JSONObject json) throws JSONException {
            load(json);
        }

        @Override
        public String toString() {
            String f;
            switch (format) {
                case AudioFormat.ENCODING_PCM_16BIT:
                    f = "16";
                    break;
                case AudioFormat.ENCODING_PCM_FLOAT:
                    f = "F";
                    break;
                default:
                    f = "?";
                    break;
            }
            return "[format=" + f + ", hz=" + hz + ", cn=" + channels + ", bps=" + bps + "]";
        }

        public JSONObject save() throws JSONException {
            JSONObject json = new JSONObject();
            json.put("format", format);
            json.put("channels", channels);
            json.put("hz", hz);
            json.put("bps", bps);
            return json;
        }

        public void load(JSONObject json) throws JSONException {
            format = json.optInt("format", AudioFormat.ENCODING_PCM_16BIT); // old recording was in 16bit format
            channels = json.getInt("channels");
            hz = json.getInt("hz");
            bps = json.getInt("bps");
        }
    }

    public RawSamples(File in, Info info) {
        this.info = info;
        this.in = in;
        this.order = ORDER;
    }

    public RawSamples(InputStream is, OutputStream os, Info info, ByteOrder order, int count) {
        this.info = info;
        this.order = order;
        this.is = is;
        this.os = os;
        this.readBuffer = new ReadBuffer(info.format, count, order);
    }

    // open for writing with specified offset to truncate file
    public void open(long writeOffset) {
        trunk(writeOffset);
        try {
            os = new BufferedOutputStream(new FileOutputStream(in, true));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // open for reading
    //
    // bufReadSize - samples count
    public void open(int bufReadSize) {
        try {
            readBuffer = new ReadBuffer(info.format, bufReadSize, order);
            is = new FileInputStream(in);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // open for read with initial offset and buffer read size
    //
    // offset - samples count offset
    // bufReadSize - samples count
    public void open(long offset, int bufReadSize) {
        try {
            readBuffer = new ReadBuffer(info.format, bufReadSize, order);
            is = new FileInputStream(in);
            is.skip(offset * getBytes(info.format));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public int read(AudioTrack.SamplesBuffer buf) {
        try {
            int len = is.read(readBuffer.bytes);
            if (len <= 0)
                return len;
            int s = (int) getSamples(len, info.format);
            switch (buf.format) {
                case AudioFormat.ENCODING_PCM_8BIT:
                    break;
                case AudioFormat.ENCODING_PCM_16BIT:
                    ByteBuffer.wrap(readBuffer.bytes, 0, len).order(order).asShortBuffer().get(buf.shorts, 0, s);
                    break;
                case Sound.ENCODING_PCM_24BIT_PACKED:
                    break;
                case Sound.ENCODING_PCM_32BIT:
                    break;
                case AudioFormat.ENCODING_PCM_FLOAT:
                    ByteBuffer.wrap(readBuffer.bytes, 0, len).order(order).asFloatBuffer().get(buf.floats, 0, s);
                    break;
                default:
                    throw new RuntimeException("Unknown format");
            }
            return s;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void write(short val) {
        try {
            ByteBuffer bb = ByteBuffer.allocate(SHORT_BYTES);
            bb.order(order);
            bb.putShort(val);
            os.write(bb.array(), 0, bb.limit());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void write(AudioTrack.SamplesBuffer buf, int pos, int len) {
        try {
            switch (buf.format) {
                case AudioFormat.ENCODING_PCM_8BIT:
                    break;
                case AudioFormat.ENCODING_PCM_16BIT: {
                    ByteBuffer bb = ByteBuffer.allocate(len * SHORT_BYTES);
                    bb.order(order);
                    bb.asShortBuffer().put(buf.shorts, pos, len);
                    os.write(bb.array(), 0, bb.limit());
                    break;
                }
                case Sound.ENCODING_PCM_24BIT_PACKED:
                    break;
                case Sound.ENCODING_PCM_32BIT:
                    break;
                case AudioFormat.ENCODING_PCM_FLOAT: {
                    ByteBuffer bb = ByteBuffer.allocate(len * FLOAT_BYTES);
                    bb.order(order);
                    bb.asFloatBuffer().put(buf.floats, pos, len);
                    os.write(bb.array(), 0, bb.limit());
                    break;
                }
                default:
                    throw new RuntimeException("Unknown format");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public long getSamples() {
        return getSamples(in.length(), info.format);
    }

    public void trunk(long pos) {
        try {
            FileOutputStream fos = new FileOutputStream(in, true);
            FileChannel outChan = fos.getChannel();
            outChan.truncate(getBufferLen(pos, info.format));
            fos.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void close() {
        try {
            if (is != null) {
                is.close();
                is = null;
            }
            if (os != null) {
                os.close();
                os = null;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
