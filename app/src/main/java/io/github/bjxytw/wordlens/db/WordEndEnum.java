package io.github.bjxytw.wordlens.db;

enum WordEndEnum {
    IED("ied"),
    IES("ies"),
    IER("ier"),
    IEST("iest"),
    ING("ing"),
    ED("ed"),
    ER("er"),
    EST("est"),
    ES("es"),
    S("s");
    private final String text;
    private final int size;
    WordEndEnum(final String text) {
        this.text = text;
        size = text.length();
    }
    String getText() { return text; }
    int getSize() { return size; }
}
