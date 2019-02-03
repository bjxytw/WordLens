package io.github.bjxytw.wordlens.db;

public class DictionaryData {
    private final String word, mean;
    DictionaryData(String word, String mean) {
        this.word = word;
        this.mean = mean;
    }

    public String wordText() {
        return word;
    }

    public String meanText() {
        return mean;
    }
}
