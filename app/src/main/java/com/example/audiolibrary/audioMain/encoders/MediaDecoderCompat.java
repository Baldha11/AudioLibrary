package com.example.audiolibrary.audioMain.encoders;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.ParcelFileDescriptor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.audiolibrary.androidlibrary.sound.MediaPlayerCompat;
import com.example.audiolibrary.audioMain.app.RawSamples;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class MediaDecoderCompat {
    public static final String TAG = MediaDecoderCompat.class.getSimpleName();

    public static final ByteOrder ORDER = ByteOrder.LITTLE_ENDIAN;
    public static final int SHORT_BYTES = Short.SIZE / Byte.SIZE;
    public static final int FLOAT_BYTES = Float.SIZE / Byte.SIZE;

    public static short[] asShortBuffer(byte[] buf, int off, int len) {
        short[] ss = new short[len / SHORT_BYTES];
        ByteBuffer.wrap(buf, off, len).order(ORDER).asShortBuffer().get(ss);
        return ss;
    }

    public static float[] asFloatBuffer(byte[] buf, int off, int len) {
        float[] ss = new float[len / FLOAT_BYTES];
        ByteBuffer.wrap(buf, off, len).order(ORDER).asFloatBuffer().get(ss);
        return ss;
    }

    public static Class forName(String name) throws ClassNotFoundException {
        return MediaPlayerCompat.forName(name);
    }

    public static MediaDecoderCompat create(Context context, Uri uri) throws IOException {
        try {
            ExoOutputStream os = new ExoOutputStream();
            return new Wrapper(context, uri, createExoDecoder25(context, uri, os), os);
        } catch (IOException e) {
            throw e; // delayed error from MediaDecoder
        } catch (Exception e) {
            if (Build.VERSION.SDK_INT >= 16)
                return new MediaDecoder(context, uri);
            else
                throw new IOException("Unsupported file format");
        }
    }

    @SuppressWarnings("unchecked")
    public static Object createExoDecoder(final ExoOutputStream os) throws ClassNotFoundException, IllegalAccessException, InstantiationException, NoSuchMethodException, InvocationTargetException, NoSuchFieldException {
        Class TrackSelector = forName("com.google.android.exoplayer2.trackselection.TrackSelector");
        Class RenderersFactory = forName("com.google.android.exoplayer2.RenderersFactory");
        final Class DrmSessionManager = forName("com.google.android.exoplayer2.drm.DrmSessionManager");
        final Class AudioRendererEventListener = forName("com.google.android.exoplayer2.audio.AudioRendererEventListener");
        final Class MediaCodecAudioRenderer = forName("com.google.android.exoplayer2.audio.MediaCodecAudioRenderer");
        final Class AudioSink = forName("com.google.android.exoplayer2.audio.AudioSink");
        final Class MediaCodecSelector = forName("com.google.android.exoplayer2.mediacodec.MediaCodecSelector");
        final Object DEFAULT = MediaCodecSelector.getField("DEFAULT").get(null);
        final Class Renderer = forName("com.google.android.exoplayer2.Renderer");
        Class C = forName("com.google.android.exoplayer2.C");
        final int ENCODING_PCM_16BIT = (int) C.getField("ENCODING_PCM_16BIT").get(null);
        final int ENCODING_PCM_FLOAT = (int) C.getField("ENCODING_PCM_FLOAT").get(null);
        InvocationHandler e = new InvocationHandler() {
            Object playbackParameters;
            long presentationTimeUs;
            Object listener;
            long audioSessionId;

            void setListener(Object listener) {
                this.listener = listener;
            }

            boolean isEncodingSupported(int encoding) {
                return encoding == ENCODING_PCM_16BIT || encoding == ENCODING_PCM_FLOAT;
            }

            long getCurrentPositionUs(boolean sourceEnded) {
                return presentationTimeUs;
            }

            void configure(int inputEncoding, int inputChannelCount, int inputSampleRate, int specifiedBufferSize, @Nullable int[] outputChannels, int trimStartSamples, int trimEndSamples) {
                int format;
                int bps;
                if (inputEncoding == ENCODING_PCM_16BIT) {
                    bps = 16;
                    format = AudioFormat.ENCODING_PCM_16BIT;
                } else if (inputEncoding == ENCODING_PCM_FLOAT) {
                    bps = 32;
                    format = AudioFormat.ENCODING_PCM_FLOAT;
                } else
                    throw new RuntimeException("Unknown encoding format");
                os.configure(new RawSamples.Info(format, inputSampleRate, inputChannelCount, bps));
            }

            void play() {
            }

            void handleDiscontinuity() {
            }

            boolean handleBuffer(ByteBuffer buffer, long presentationTimeUs) {
                this.presentationTimeUs = presentationTimeUs;
                try {
                    byte[] bb = new byte[buffer.remaining()];
                    buffer.asReadOnlyBuffer().get(bb);
                    os.write(bb, 0, bb.length);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                return true;
            }

            void playToEndOfStream() {
                os.end();
            }

            boolean isEnded() {
                return true;
            }

            boolean hasPendingData() {
                return false;
            }

            Object setPlaybackParameters(Object playbackParameters) {
                return this.playbackParameters = playbackParameters;
            }

            Object getPlaybackParameters() {
                return playbackParameters;
            }

            void setAudioAttributes(Object audioAttributes) {
            }

            void setAudioSessionId(int audioSessionId) {
                this.audioSessionId = audioSessionId;
            }

            void enableTunnelingV21(int tunnelingAudioSessionId) {
            }

            void disableTunneling() {
            }

            void setVolume(float volume) {
            }

            void pause() {
            }

            void reset() {
            }

            void release() {
            }

            @Override
            public Object invoke(Object proxy, Method method, Object[] args) {
                switch (method.getName()) {
                    case "setListener":
                        setListener(args[0]);
                        break;
                    case "isEncodingSupported":
                        return isEncodingSupported((int) args[0]);
                    case "getCurrentPositionUs":
                        return getCurrentPositionUs((boolean) args[0]);
                    case "configure":
                        configure((int) args[0], (int) args[1], (int) args[2], (int) args[3], (int[]) args[4], (int) args[5], (int) args[6]);
                        break;
                    case "play":
                        play();
                        break;
                    case "handleDiscontinuity":
                        handleDiscontinuity();
                        break;
                    case "handleBuffer":
                        return handleBuffer((ByteBuffer) args[0], (long) args[1]);
                    case "playToEndOfStream":
                        playToEndOfStream();
                        break;
                    case "isEnded":
                        return isEnded();
                    case "hasPendingData":
                        return hasPendingData();
                    case "setPlaybackParameters":
                        return setPlaybackParameters(args[0]);
                    case "getPlaybackParameters":
                        return getPlaybackParameters();
                    case "setAudioAttributes":
                        setAudioAttributes(args[0]);
                        break;
                    case "setAudioSessionId":
                        setAudioSessionId((int) args[0]);
                        break;
                    case "enableTunnelingV21":
                        enableTunnelingV21((int) args[0]);
                        break;
                    case "disableTunneling":
                        disableTunneling();
                        break;
                    case "setVolume":
                        setVolume((float) args[0]);
                        break;
                    case "pause":
                        pause();
                        break;
                    case "reset":
                        reset();
                        break;
                    case "release":
                        release();
                        break;
                }
                return null;
            }
        };
        final Object audioSink = Proxy.newProxyInstance(AudioSink.getClassLoader(), new Class[]{AudioSink}, e);
        e = new InvocationHandler() {
            Object createRenderers(Handler eventHandler, Object videoRendererEventListener, Object audioRendererEventListener, Object textRendererOutput, Object metadataRendererOutput) {
                try {
                    Object drmSessionManager = null;
                    ArrayList renderersList = new ArrayList();
                    renderersList.add(MediaCodecAudioRenderer.getConstructor(MediaCodecSelector, DrmSessionManager, boolean.class, Handler.class, AudioRendererEventListener, AudioSink).newInstance(DEFAULT, drmSessionManager, true, eventHandler, audioRendererEventListener, audioSink));
                    return renderersList.toArray((Object[]) Array.newInstance(Renderer, renderersList.size()));
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                } catch (InvocationTargetException e) {
                    throw new RuntimeException(e);
                } catch (NoSuchMethodException e) {
                    throw new RuntimeException(e);
                } catch (InstantiationException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public Object invoke(Object proxy, Method method, Object[] args) {
                switch (method.getName()) {
                    case "createRenderers":
                        return createRenderers((Handler) args[0], args[1], args[2], args[3], args[4]);
                }
                return null;
            }
        };
        Object renderersFactory = Proxy.newProxyInstance(RenderersFactory.getClassLoader(), new Class[]{RenderersFactory}, e);
        Object trackSelector = MediaPlayerCompat.createExoTrackSelector();
        return forName("com.google.android.exoplayer2.ExoPlayerFactory").getMethod("newSimpleInstance", RenderersFactory, TrackSelector).invoke(null, renderersFactory, trackSelector);
    }

    public static MediaPlayerCompat createExoDecoder25(Context context, Uri uri, ExoOutputStream os) { // 2.7 compatible
        try {
            Object player = createExoDecoder(os);
            Object source = MediaPlayerCompat.createExoSource(context, uri);
            return MediaPlayerCompat.createExoPlayer(context, player, source);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InstantiationException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    public final int getInteger(MediaFormat format, String name, int defaultValue) {
        try {
            return format.getInteger(name);
        } catch (NullPointerException e) { /* no such field */ } catch (ClassCastException e) { /* field of different type */ }
        return defaultValue;
    }

    @TargetApi(16)
    public static class MediaDecoder extends MediaDecoderCompat {
        public MediaExtractor m;
        public MediaCodec codec;
        public long duration;
        public RawSamples.Info info;

        public MediaDecoder(Context context, Uri uri) throws IOException {
            m = new MediaExtractor();
            ParcelFileDescriptor fd = MediaPlayerCompat.getFD(context, uri);
            m.setDataSource(fd.getFileDescriptor());
            for (int index = 0; index < m.getTrackCount(); index++) {
                MediaFormat format = m.getTrackFormat(index);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime.startsWith("audio/")) {
                    try {
                        m.selectTrack(index);
                        codec = MediaCodec.createDecoderByType(mime);
                        int encoding = getInteger(format, MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT);
                        int channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                        codec.configure(format, null, null, 0);
                        int hz = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                        duration = format.getLong(MediaFormat.KEY_DURATION) / 1000;
                        info = new RawSamples.Info(encoding, hz, channels); // mediaCodec.getOutputFormat() fails for <API19, return wrong bitrate for .ogg
                        break;
                    } catch (IllegalStateException e) { // CodecException for unsupported files
                        throw new IOException(e);
                    }
                }
            }
            if (codec == null)
                throw new IOException("Unsupported file");
        }

        @Override
        public long getDuration() {
            return duration;
        }

        @Override
        public RawSamples.Info getInfo() {
            return info;
        }

        @Override
        public void seekTo(long pos) {
            m.seekTo(pos * 1000, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
        }

        @Override
        public long getSampleTime() {
            return m.getSampleTime() / 1000;
        }

        @Override
        public void decode(OutputStream os) throws IOException {
            codec.start();

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

            while (encode())
                decode(info, os);

            while ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) == 0)
                decode(info, os);
        }

        public boolean encode() {
            int inputIndex = codec.dequeueInputBuffer(-1);
            if (inputIndex >= 0) {
                ByteBuffer buffer = codec.getInputBuffers()[inputIndex];
                buffer.clear();
                int sampleSize = m.readSampleData(buffer, 0);
                if (sampleSize < 0) {
                    codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    return false;
                } else {
                    codec.queueInputBuffer(inputIndex, 0, sampleSize, m.getSampleTime(), 0);
                    return m.advance();
                }
            }
            return true;
        }

        public void decode(MediaCodec.BufferInfo info, OutputStream os) throws IOException {
            int outputIndex = codec.dequeueOutputBuffer(info, 0);
            if (outputIndex >= 0) {
                ByteBuffer buffer = codec.getOutputBuffers()[outputIndex];
                buffer.position(info.offset);
                buffer.limit(info.offset + info.size);
                byte[] b = new byte[info.size];
                buffer.get(b); // MediaExtractor produces LITTLE_ENDIAN
                os.write(b, 0, b.length);
                codec.releaseOutputBuffer(outputIndex, false);
            }
        }

        @Override
        public void close() {
            if (codec != null) {
                codec.release();
                codec = null;
            }
            if (m != null) {
                m.release();
                m = null;
            }
        }
    }

    public static class ExoOutputStream extends OutputStream {
        RawSamples.Info info;

        public OutputStream os;

        public final AtomicBoolean lock = new AtomicBoolean(false); // decoding ready
        public final AtomicBoolean ready = new AtomicBoolean(false); // onReady lock
        public final AtomicBoolean conf = new AtomicBoolean(false); // info ready lock
        public final AtomicBoolean end = new AtomicBoolean(false); // eof ready

        public void configure(RawSamples.Info info) {
            this.info = info;
            synchronized (conf) {
                conf.set(true);
                conf.notifyAll();
            }
            synchronized (lock) {
                try {
                    if (!lock.get())
                        lock.wait(); // block further write() operations until 'os' get set
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        @Override
        public void write(int b) throws IOException {
            os.write(b);
        }

        @Override
        public void write(@NonNull byte[] b, int off, int len) throws IOException {
            os.write(b, off, len);
        }

        public void decode(OutputStream os) {
            synchronized (lock) {
                this.os = os;
                lock.set(true);
                lock.notifyAll();
            }
            synchronized (end) {
                try {
                    if (!end.get())
                        end.wait();
                } catch (InterruptedException ignore) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        public void end() {
            synchronized (end) {
                end.set(true);
                end.notifyAll();
            }
        }
    }

    public static class Wrapper extends MediaDecoderCompat {
        MediaDecoderCompat decoder;
        IOException error;

        public Wrapper(MediaDecoderCompat d) {
            decoder = d;
        }

        public Wrapper(final Context context, final Uri uri, final MediaPlayerCompat player, final ExoOutputStream os) throws IOException {
            decoder = new MediaDecoderCompat() {
                @Override
                public void decode(OutputStream os1) throws IOException {
                    os.decode(os1);
                }

                @Override
                public long getDuration() {
                    return player.getDuration();
                }

                @Override
                public RawSamples.Info getInfo() {
                    return os.info;
                }

                @Override
                public long getSampleTime() {
                    return player.getCurrentPosition();
                }

                @Override
                public void seekTo(long pos) {
                    player.seekTo(pos);
                }

                @Override
                public void close() {
                    player.release();
                }
            };

            player.addListener(new MediaPlayerCompat.ExoListener() {
                @Override
                public void onTimelineChanged() {
                    if (getDuration() != 0) {
                        synchronized (os.ready) {
                            os.ready.set(true);
                            os.ready.notifyAll();
                        }
                    }
                }

                @Override
                public void onTracksChanged() {
                }

                @Override
                public void onReady() { // onReady will never be called until handleBuffer released, but duration can be filled before
                    synchronized (os.ready) {
                        os.ready.set(true);
                        os.ready.notifyAll();
                    }
                }

                @Override
                public void onEnd() {
                }

                @Override
                public void onError(Exception e) {
                    if (e instanceof MediaPlayerCompat.UnrecognizedInputFormatException) {
                        player.release();
                        try {
                            decoder = new MediaDecoder(context, uri);
                        } catch (IOException ee) {
                            error = ee;
                        }
                    }
                    synchronized (os.ready) {
                        os.ready.set(true);
                        os.ready.notifyAll();
                    }
                    synchronized (os.conf) {
                        os.conf.set(true);
                        os.conf.notifyAll();
                    }
                }
            });
            player.prepare();
            player.setPlayWhenReady(true);

            synchronized (os.conf) {
                try {
                    if (!os.conf.get())
                        os.conf.wait();
                } catch (InterruptedException ignore) {
                    Thread.currentThread().interrupt();
                }
            }
            synchronized (os.ready) {
                try {
                    if (!os.ready.get())
                        os.ready.wait();
                } catch (InterruptedException ignore) {
                    Thread.currentThread().interrupt();
                }
            }
            if (error != null)
                throw error;
        }

        @Override
        public void decode(OutputStream os) throws IOException {
            decoder.decode(os);
        }

        @Override
        public long getDuration() {
            return decoder.getDuration();
        }

        @Override
        public RawSamples.Info getInfo() {
            return decoder.getInfo();
        }

        @Override
        public void seekTo(long pos) {
            decoder.seekTo(pos);
        }

        @Override
        public long getSampleTime() {
            return decoder.getSampleTime();
        }

        @Override
        public void close() {
            decoder.close();
        }
    }

    public long getDuration() {
        return 0;
    }

    public RawSamples.Info getInfo() {
        return null;
    }

    public void seekTo(long pos) {
    }

    public long getSampleTime() {
        return 0;
    }

    public void decode(OutputStream os) throws IOException {
    }

    public void close() {
    }
}
