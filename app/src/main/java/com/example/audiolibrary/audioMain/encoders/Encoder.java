package com.example.audiolibrary.audioMain.encoders;


import com.example.audiolibrary.androidlibrary.sound.AudioTrack;

public interface Encoder {

    void encode(AudioTrack.SamplesBuffer buf, int pos, int len);

    void close();

}
