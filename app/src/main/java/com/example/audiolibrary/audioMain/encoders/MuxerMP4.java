package com.example.audiolibrary.audioMain.encoders;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;

import com.example.audiolibrary.androidlibrary.sound.AudioTrack;
import com.example.audiolibrary.audioMain.app.RawSamples;

import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@TargetApi(18) // depends on MediaMuxer
public class MuxerMP4 implements Encoder {
    public static final int SHORT_BYTES = Short.SIZE / Byte.SIZE;

    RawSamples.Info info;
    MediaCodec encoder;
    MediaMuxerCompat muxer;
    int audioTrackIndex;
    long NumSamples;
    ByteBuffer input;
    int inputIndex;

    public static Map<String, MediaCodecInfo> findEncoder(String mime) {
        mime = mime.toLowerCase();
        Map<String, MediaCodecInfo> map = new HashMap<>();
        int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            if (!codecInfo.isEncoder())
                continue;
            String[] types = codecInfo.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                String t = types[j].toLowerCase();
                if (t.startsWith(mime))
                    map.put(t, codecInfo);
            }
        }
        return map;
    }

    public static String prefered(String pref, Map<String, MediaCodecInfo> map) {
        pref = pref.toLowerCase();
        Iterator i = map.keySet().iterator();
        while (i.hasNext()) {
            String m = (String) i.next();
            m = m.toLowerCase();
            if (m.startsWith(pref))
                return m;
        }
        i = map.keySet().iterator();
        if (i.hasNext())
            return (String) i.next();
        return null;
    }

    public static MediaFormat getDefault(String pref, Map<String, MediaCodecInfo> map) {
        String p = prefered(pref, map);
        if (Build.VERSION.SDK_INT >= 21) {
            return map.get(p).getCapabilitiesForType(p).getDefaultFormat();
        } else {
            MediaFormat format = new MediaFormat();
            format.setString(MediaFormat.KEY_MIME, pref);
            return format;
        }
    }

    public void create(Context context, RawSamples.Info info, MediaFormat format, FileDescriptor out) {
        this.info = info;
        try {
            encoder = MediaCodec.createEncoderByType(format.getString(MediaFormat.KEY_MIME));
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            encoder.start();
            muxer = new MediaMuxerCompat(context, out, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void encode(AudioTrack.SamplesBuffer buf, int pos, int len) {
        encode2(buf, pos, len);
    }

    public void encode2(AudioTrack.SamplesBuffer buf, int pos, int len) {
        int end = pos + len;
        for (int offset = pos; offset < end; offset++) {
            if (input == null) {
                inputIndex = encoder.dequeueInputBuffer(-1);
                if (inputIndex < 0)
                    throw new RuntimeException("unable to open encoder input buffer");
                if (Build.VERSION.SDK_INT >= 21)
                    input = encoder.getInputBuffer(inputIndex);
                else
                    input = encoder.getInputBuffers()[inputIndex];
                input.clear();
            }
            switch (buf.format) {
                case AudioFormat.ENCODING_PCM_16BIT:
                    input.putShort(buf.shorts[offset]);
                    break;
                case AudioFormat.ENCODING_PCM_FLOAT:
                    input.putFloat(buf.floats[offset]);
                    break;
                default:
                    throw new RuntimeException("Unknown format");
            }
            if (!input.hasRemaining())
                queue();
        }
    }

    void queue() {
        if (input == null)
            return;
        encoder.queueInputBuffer(inputIndex, 0, input.position(), getCurrentTimeStamp(), 0);
        NumSamples += input.position() / info.channels / SHORT_BYTES;
        input = null;
        while (encode())
            ;// do encode()
    }

    boolean encode() {
        MediaCodec.BufferInfo outputInfo = new MediaCodec.BufferInfo();
        int outputIndex = encoder.dequeueOutputBuffer(outputInfo, 0);
        if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER)
            return false;

        if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            audioTrackIndex = muxer.addTrack(encoder.getOutputFormat());
            muxer.start();
            return true;
        }

        if (outputIndex >= 0) {
            ByteBuffer output;
            if (Build.VERSION.SDK_INT >= 21)
                output = encoder.getOutputBuffer(outputIndex);
            else
                output = encoder.getOutputBuffers()[outputIndex];
            output.position(outputInfo.offset);
            output.limit(outputInfo.offset + outputInfo.size);
            muxer.writeSampleData(audioTrackIndex, output, outputInfo);
            encoder.releaseOutputBuffer(outputIndex, false);
        }

        return true;
    }

    long getCurrentTimeStamp() {
        return NumSamples * 1000 * 1000 / info.hz;
    }

    public void end() {
        if (input != null)
            queue();
        int inputIndex = encoder.dequeueInputBuffer(-1);
        if (inputIndex >= 0) {
            ByteBuffer input;
            if (Build.VERSION.SDK_INT >= 21)
                input = encoder.getInputBuffer(inputIndex);
            else
                input = encoder.getInputBuffers()[inputIndex];
            input.clear();
            encoder.queueInputBuffer(inputIndex, 0, 0, getCurrentTimeStamp(), MediaCodec.BUFFER_FLAG_END_OF_STREAM);
        }
        while (encode())
            ;// do encode()
        encoder.stop();
        muxer.stop();
    }

    public RawSamples.Info getInfo() {
        return info;
    }

    public void close() {
        end();
        encoder.release();
        muxer.release();
    }
}
