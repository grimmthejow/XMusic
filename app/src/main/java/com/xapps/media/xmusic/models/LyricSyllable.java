package com.xapps.media.xmusic.models;

public class LyricSyllable {
    public int startTime;
    public int endTime;
    public int nextStartTime;

    public final String text;
    public final int relStart;

    public LyricSyllable(int startTime, String text, int relStart) {
        this.startTime = startTime;
        this.text = text;
        this.relStart = relStart;
    }
}