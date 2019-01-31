package io.github.bjxytw.wordlens.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String TAG = "DBHelper";
    private static final String DB_SOURCE_NAME = "ejdict.sqlite3";
    private static final String DB_NAME = "dictionary.db";
    private static final int DB_VERSION = 1;
    private static final int COPY_BUFFER_SIZE = 1024;
    private Context context;
    private File databasePath;
    private boolean databaseExist = true;

    DatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
        this.context = context;
        databasePath = context.getDatabasePath(DB_NAME);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        super.onOpen(db);
        databaseExist = false;
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}

    @Override
    public SQLiteDatabase getReadableDatabase() {
        SQLiteDatabase database = super.getReadableDatabase();
        if (databaseExist) {
            Log.i(TAG, "Database exists.");
            return database;
        } else {
            database.close();
            try {
                return copyDatabase();
            } catch (IOException e) {
                Log.e(TAG, "Database copy failed.", e);
                return null;
            }
        }
    }

    private SQLiteDatabase copyDatabase() throws IOException{
        try (InputStream input = context.getAssets().open(DB_SOURCE_NAME);
             OutputStream output = new FileOutputStream(databasePath)) {
            byte[] buffer = new byte[COPY_BUFFER_SIZE];
            int size;
            while ((size = input.read(buffer)) > 0)
                output.write(buffer, 0, size);
            Log.i(TAG, "Copied database from assets.");
        }
        databaseExist = true;
        return super.getWritableDatabase();
    }
}
