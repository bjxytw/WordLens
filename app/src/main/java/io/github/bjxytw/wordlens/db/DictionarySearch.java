package io.github.bjxytw.wordlens.db;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

public class DictionarySearch {
    private static final String SQL_SEARCH = "SELECT * FROM items WHERE word COLLATE nocase=?";
    private static final String WORD_COL = "word";
    private static final String MEAN_COL = "mean";
    private SQLiteDatabase database;

    public DictionarySearch(Context context) {
        DatabaseHelper helper = new DatabaseHelper(context);
        database = helper.getReadableDatabase();
    }

    public DictionaryData search(String word) {
        String wordText = null;
        StringBuilder meanText = new StringBuilder();
        Cursor dbCursor = database.rawQuery(SQL_SEARCH, new String[]{word});
        while (dbCursor.moveToNext()) {
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
