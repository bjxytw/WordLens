package io.github.bjxytw.wordlens.db;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

public class DictionarySearch {
    private static final String SYMBOLS = "[!?,.;:()\"]";
    private static final String SQL_SEARCH = "SELECT * FROM items WHERE word COLLATE nocase=?";
    private static final String WORD_COL = "word";
    private static final String MEAN_COL = "mean";
    private SQLiteDatabase database;

    public DictionarySearch(Context context) {
        DatabaseHelper helper = new DatabaseHelper(context);
        database = helper.getReadableDatabase();
    }

    public DictionaryData search(String word)  {
        String searchWord = removeSymbol(word.toLowerCase());
        if (searchWord != null) {
            DictionaryData result = searchFromSql(searchWord);
            if (result == null)
                result = searchBaseForm(searchWord);
            return result;
        }
        return null;
    }

    private DictionaryData searchFromSql(String searchWord) {
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

    private static String removeSymbol(String detectedText) {
        String text = detectedText.replaceAll(SYMBOLS, "");
        if (text.length() == 0) return null;
        return text;
    }

    private DictionaryData searchBaseForm(String word) {
        int size = word.length();
        StringBuilder searchWord = new StringBuilder();
        if (size >= 4 && (word.substring(size - 3).equals("ied")
                || word.substring(size - 3).equals("ies"))) {
            searchWord.append(word.substring(0, size - 3));
            searchWord.append("y");
            return searchFromSql(searchWord.toString());
        }
        return null;
    }

    public void close() {
        if (database != null) database.close();
    }

    public class DictionaryData {
        private String word, mean;
        DictionaryData(String word, String mean) {
            this.word = word;
            this.mean = mean;
        }
        public String wordText() {return word;}
        public String meanText() {return mean;}
    }
}
