package io.github.bjxytw.wordlens.db;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import io.github.bjxytw.wordlens.data.DictionaryData;


public class DictionarySearch {
    private static final String TAG = "DicSearch";
    private static final String REGEX_SYMBOL = "[!-/:-@\\[-`{-~]";
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
        String searchWord = removeBothEndSymbol(word.toLowerCase());
        if (searchWord != null) {
            DictionaryData result = searchDirect(searchWord);
            if (result == null) result = searchWithoutAbbreviation(searchWord);
            if (result == null) result = searchBaseForm(searchWord);
            return result;
        }
        return null;
    }

    public DictionaryData searchDirect(String searchWord) {
        String wordText = null;
        StringBuilder meanText = new StringBuilder();
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
        int abbreviationIndex = word.lastIndexOf("'");
        if (abbreviationIndex != -1)
            return searchDirect(word.substring(0, abbreviationIndex));
        return null;
    }

    private DictionaryData searchBaseForm(String word) {
        WordEndEnum wordEnd = detectWordEnd(word);
        if (wordEnd != null) {
            StringBuilder searchWord = new StringBuilder();
            switch (wordEnd) {
                case IED:
                case IES:
                case IER:
                case IEST:
                    searchWord.append(subStringFirst(word, wordEnd.getSize()));
                    searchWord.append('y');
                    return searchDirect(searchWord.toString());
                case ING:
                case ED:
                case ER:
                case EST:
                    Character consecutiveLetter = compareLetter(word,
                            wordEnd.getSize() + 1, wordEnd.getSize() + 2);
                    if (consecutiveLetter != null) {
                        searchWord.append(subStringFirst(word, wordEnd.getSize() + 2));
                        searchWord.append(consecutiveLetter);
                        DictionaryData result = searchDirect(searchWord.toString());
                        if (result != null) return result;
                        else searchWord.setLength(0);
                    }
                case ES:
                    searchWord.append(subStringFirst(word, wordEnd.getSize()));
                    searchWord.append('e');
                    DictionaryData result = searchDirect(searchWord.toString());
                    if (result == null) {
                        searchWord.deleteCharAt(searchWord.length() - 1);
                        result = searchDirect(searchWord.toString());
                    }
                    return result;
                case S:
                    searchWord.append(subStringFirst(word, wordEnd.getSize()));
                    return searchDirect(searchWord.toString());
            }
        }
        return null;
    }

    private WordEndEnum detectWordEnd(String word) {
        for (WordEndEnum wordEnd : WordEndEnum.values()) {
            String end = subStringLast(word, wordEnd.getSize());
            if (end != null && end.equals(wordEnd.getText()))
                return wordEnd;
        }
        return null;
    }

    private static String subStringFirst(String word, int backWardIndex) {
        int size = word.length();
        if (backWardIndex < size)
            return word.substring(0, size - backWardIndex);
        return null;
    }

    private static String subStringLast(String word, int backWardIndex) {
        int size = word.length();
        if (backWardIndex < size)
            return word.substring(size - backWardIndex);
        return null;
    }

    private static Character compareLetter(String word, int backWardIndex1, int backWardIndex2) {
        int size = word.length();
        if (backWardIndex1 < size && backWardIndex2 < size) {
            char char1 = word.charAt(size - backWardIndex1);
            char char2 = word.charAt(size - backWardIndex2);
            if (char1 == char2) return char1;
        }
        return null;
    }

    public static String removeBothEndSymbol(String text) {
        int size = text.length();
        if (size > 0) {
            int begin = 0;
            int end = size;
            if (text.substring(0, 1).matches(REGEX_SYMBOL))
                begin = 1;
            if (text.substring(size - 1).matches(REGEX_SYMBOL))
                end = size - 1;

            if (end - begin > 0)
                return text.substring(begin, end);
        }
        return null;
    }

}
