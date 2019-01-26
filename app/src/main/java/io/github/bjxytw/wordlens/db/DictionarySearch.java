package io.github.bjxytw.wordlens.db;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

public class DictionarySearch {
    private static final String SQL_SEARCH = "SELECT mean FROM items WHERE word=?";
    private static final String MEAN_COL = "mean";
    private SQLiteDatabase database;

    public DictionarySearch(Context context) {
        DatabaseHelper helper = new DatabaseHelper(context);
        database = helper.getReadableDatabase();
    }

    public String search(String word) {
        StringBuilder meanText = new StringBuilder();
        Cursor dbCursor = database.rawQuery(SQL_SEARCH, new String[]{word});
        while (dbCursor.moveToNext()) {
            meanText.append(dbCursor.getString(dbCursor.getColumnIndex(MEAN_COL))
                    .replace(" / ", "\n"));
            meanText.append("\n\n");
        }
        dbCursor.close();

        if (meanText.length() > 0)
            return meanText.toString();
        return null;
    }

    public void close() {
        if (database != null) database.close();
    }
}
