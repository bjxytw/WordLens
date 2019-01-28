package io.github.bjxytw.wordlens.db;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

public class DictionarySearch {
    private static final String TAG = "DicSearch";
    private static final String SYMBOLS = "[!-/:-@\\[-`{-~]";
    private static final String SQL_SEARCH = "SELECT * FROM items WHERE word COLLATE nocase=?";
    private static final String WORD_COL = "word";
    private static final String MEAN_COL = "mean";
    private SQLiteDatabase database;

    public DictionarySearch(Context context) {
        DatabaseHelper helper = new DatabaseHelper(context);
        database = helper.getReadableDatabase();
    }

    public void close() {
        if (database != null) database.close();
    }

    public DictionaryData search(String word)  {
        String searchWord = removeEndSymbol(word.toLowerCase());
        if (searchWord != null) {
            DictionaryData result = searchFromSql(searchWord);
            if (result == null) result = searchWithoutAbbreviation(searchWord);
            if (result == null) result = searchBaseForm(searchWord);
            return result;
        }
        return null;
    }

    private DictionaryData searchFromSql(String searchWord) {
        String wordText = null;
        StringBuilder meanText = new StringBuilder();
        Log.d(TAG, "Search: " + searchWord);
        Cursor dbCursor = database.rawQuery(SQL_SEARCH, new String[]{searchWord});
        while (dbCursor.moveToNext()) {
            if (wordText == null)
                wordText = dbCursor.getString(dbCursor.getColumnIndex(WORD_COL));
            meanText.append(dbCursor.getString(dbCursor.getColumnIndex(MEAN_COL))
                    .replace(" / ", "\n"));
            meanText.append("\n\n");
        }
        dbCursor.close();

        if (wordText == null || meanText.length() == 0)
            return null;
        return new DictionaryData(wordText, meanText.toString());
    }

    private DictionaryData searchWithoutAbbreviation(String word) {
        int slashIndex = word.indexOf("/");
        int abbreviationIndex = word.lastIndexOf("'");
        if (slashIndex != -1)
            return searchFromSql(word.substring(0, slashIndex));
        if (abbreviationIndex != -1)
            return searchFromSql(word.substring(0, abbreviationIndex));
        return null;
    }

    private DictionaryData searchBaseForm(String word) {
        SubStringBack sub = new SubStringBack(word);
        WordEnd wordEnd = detectWordEnd(sub);
        if (wordEnd != null) {
            StringBuilder searchWord = new StringBuilder();
            switch (wordEnd) {
                case IED:
                case IES:
                case IER:
                case IEST:
                    searchWord.append(sub.first(wordEnd.getSize()));
                    searchWord.append('y');
                    return searchFromSql(searchWord.toString());
                case ING:
                case ED:
                case ER:
                case ST:
                    Character consecutiveLetter =
                            sub.compareLetter(wordEnd.getSize() + 1, wordEnd.getSize() + 2);
                    if (consecutiveLetter != null) {
                        searchWord.append(sub.first(wordEnd.getSize() + 2));
                        searchWord.append(consecutiveLetter);
                        DictionaryData result = searchFromSql(searchWord.toString());
                        if (result != null) return result;
                        else searchWord.setLength(0);
                    }
                case ES:
                    searchWord.append(sub.first(wordEnd.getSize()));
                    searchWord.append('e');
                    DictionaryData result = searchFromSql(searchWord.toString());
                    if (result == null) {
                        searchWord.deleteCharAt(searchWord.length() - 1);
                        result = searchFromSql(searchWord.toString());
                    }
                    return result;
                case S:
                    searchWord.append(sub.first(wordEnd.getSize()));
                    return searchFromSql(searchWord.toString());
            }
        }
        return null;
    }

    private WordEnd detectWordEnd(SubStringBack sub) {
        String end;
        for (int i = WordEnd.getMaxSize(); i >= WordEnd.getMinSize(); i--) {
            end = sub.last(i);
            for (WordEnd wordEnd : WordEnd.values())
                if (end != null && end.equals(wordEnd.getText()))
                    return wordEnd;
        }
        return null;
    }

    private static String removeEndSymbol(String text) {
        int size = text.length();
        if (size > 1 && text.substring(size - 1).matches(SYMBOLS))
            return text.substring(0, size - 1);
        return text;
    }

    public class DictionaryData {
        private final String word, mean;
        DictionaryData(String word, String mean) {
            this.word = word;
            this.mean = mean;
        }
        public String wordText() { return word; }
        public String meanText() { return mean; }
    }

    private class SubStringBack {
        private final String word;
        private final int size;
        SubStringBack(String word) {
            this.word = word;
            size = word.length();
        }
        String first(int index) {
            if (index < size) return word.substring(0, size - index);
            return null;
        }
        String last(int index) {
            if (index < size) return word.substring(size - index);
            return null;
        }
        Character compareLetter(int index1, int index2) {
            if (index1 < size && index2 < size) {
                char char1 = word.charAt(size - index1);
                char char2 = word.charAt(size - index2);
                if (char1 == char2) return char1;
            }
            return null;
        }
    }

    private enum WordEnd {
        IED("ied"),
        IES("ies"),
        IER("ier"),
        IEST("iest"),
        ING("ing"),
        ED("ed"),
        ER("er"),
        ST("st"),
        ES("es"),
        S("s");
        private final String text;
        private final int size;
        WordEnd(final String text) {
            this.text = text;
            size = text.length();
        }
        private String getText() { return text; }

        private int getSize() { return size; }

        private static int getMaxSize() {
            int max = 0;
            for (WordEnd wordEnd : values())
                if (wordEnd.getSize() > max) max = wordEnd.size;
            return max;
        }
        private static int getMinSize() {
            int min = Integer.MAX_VALUE;
            for (WordEnd wordEnd : values())
                if (wordEnd.getSize() < min) min = wordEnd.size;
            return min;
        }
    }
}
