package io.github.bjxytw.wordlens.data;

public class LinkTextData {
    private int start, end;
    private String text;

    public LinkTextData(int start, int second, String text) {
        this.start = start;
        this.end = second;
        this.text = text;
    }

    public int getStart() {
        return start;
    }

    public int getEnd() {
        return end;
    }

    public String getText() {
        return text;
    }
}
