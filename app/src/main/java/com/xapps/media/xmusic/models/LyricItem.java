package com.xapps.media.xmusic.models;

public class LyricItem {
    public LyricLine mainLine;
    public int mainIndex;
    public LyricLine romajiLine;
    public int romajiIndex = -1;
    public LyricLine linkedBgLine;
    public int linkedBgIndex = -1;

    public LyricItem(LyricLine main, int idx) {
        this.mainLine = main;
        this.mainIndex = idx;
    }
}