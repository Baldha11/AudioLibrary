package com.example.audiolibrary.audioMain.encoders;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;

import com.example.audiolibrary.androidlibrary.app.AssetsDexLoader;
import com.example.audiolibrary.audioMain.app.Storage;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;

@TargetApi(18)
public class MediaMuxerCompat {
    public static String TAG = MediaMuxerCompat.class.getSimpleName();

    public MediaMuxer muxer;
    public FileDescriptor fd;
    public File out; // temporary file

    public MediaMuxerCompat(MediaMuxer muxer) {
        this.muxer = muxer;
    }

    @SuppressWarnings("unchecked")
    public MediaMuxerCompat(Context context, FileDescriptor fd, int format) throws IOException {
        try { // API26+
            Constructor<?> k = MediaMuxer.class.getConstructor(FileDescriptor.class, int.class);
            muxer = (MediaMuxer) k.newInstance(fd, format);
        } catch (Exception ignore) {
            try {
                muxer = (MediaMuxer) AssetsDexLoader.newInstance(MediaMuxer.class);
                Field mLastTrackIndex = AssetsDexLoader.getPrivateField(MediaMuxer.class, "mLastTrackIndex");
                mLastTrackIndex.set(muxer, -1);
                Method nativeSetup = AssetsDexLoader.getPrivateMethod(MediaMuxer.class, "nativeSetup", FileDescriptor.class, int.class);
                Field mNativeObject = AssetsDexLoader.getPrivateField(MediaMuxer.class, "mNativeObject");
                mNativeObject.set(muxer, nativeSetup.invoke(muxer, fd, format));
                int MUXER_STATE_INITIALIZED = (int) AssetsDexLoader.getPrivateField(MediaMuxer.class, "MUXER_STATE_INITIALIZED").get(null);
                Field mState = AssetsDexLoader.getPrivateField(MediaMuxer.class, "mState");
                mState.set(muxer, MUXER_STATE_INITIALIZED);
                Class CloseGuard = Class.forName("dalvik.system.CloseGuard");
                Field mCloseGuard = AssetsDexLoader.getPrivateField(MediaMuxer.class, "mCloseGuard");
                Method get = CloseGuard.getDeclaredMethod("get");
                mCloseGuard.set(muxer, get.invoke(null));
                Method open = CloseGuard.getDeclaredMethod("open", String.class);
                open.invoke(mCloseGuard.get(muxer), "release");
            } catch (Exception ignore1) {
                create(context, fd);
                muxer = new MediaMuxer(out.getAbsolutePath(), format);
            }
        }
    }

    public void create(Context context, FileDescriptor fd) {
        this.fd = fd;

        Storage storage = new Storage(context);
        out = storage.getTempRecording();

        File parent = out.getParentFile();

        if (!Storage.mkdirs(parent)) // in case if it was manually deleted
            throw new RuntimeException("Unable to create: " + parent);
    }

    public void start() {
        muxer.start();
    }

    public void stop() {
        muxer.stop();
    }

    public int addTrack(MediaFormat f) {
        return muxer.addTrack(f);
    }

    public void writeSampleData(int trackIndex, ByteBuffer byteBuf, MediaCodec.BufferInfo bufferInfo) {
        muxer.writeSampleData(trackIndex, byteBuf, bufferInfo);
    }

    public void release() {
        if (out != null && out.exists() && out.length() > 0) {
            try {
                FileInputStream fis = new FileInputStream(out);
                FileOutputStream fos = new FileOutputStream(fd);
                IOUtils.copy(fis, fos);
                fos.close();
                fis.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                Storage.delete(out); // delete tmp encoding file
                out = null;
            }
        }
        muxer.release();
    }

}
