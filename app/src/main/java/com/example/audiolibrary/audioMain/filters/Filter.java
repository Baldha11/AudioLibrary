package com.example.audiolibrary.audioMain.filters;


import com.example.audiolibrary.androidlibrary.sound.AudioTrack;

public class Filter {

    public static class Buffer {
        public AudioTrack.SamplesBuffer buf;
        public int pos;
        public int len;

        public Buffer(AudioTrack.SamplesBuffer buf, int pos, int len) {
            this.buf = buf;
            this.pos = pos;
            this.len = len;
        }
    }

    public void filter(Buffer buffer) {
    }

}
