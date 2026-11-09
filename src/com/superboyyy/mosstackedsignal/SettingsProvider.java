package com.superboyyy.mosstackedsignal;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

/**
 * 只读的设置接口：SystemUI 里的 hook 通过它读模块设置（key / value 两列，布尔值用 "1" / "0"）。
 * 设置存在设备加密存储里，开机后还没解锁时 SystemUI 也能读到。
 */
public class SettingsProvider extends ContentProvider {

    static SharedPreferences prefs(Context c) {
        return c.createDeviceProtectedStorageContext().getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE);
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        SharedPreferences p = prefs(getContext());
        MatrixCursor c = new MatrixCursor(new String[] {"key", "value"});
        c.addRow(new Object[] {Prefs.KEY_FIVE_A, p.getBoolean(Prefs.KEY_FIVE_A, false) ? "1" : "0"});
        c.addRow(new Object[] {Prefs.KEY_WIFI, p.getBoolean(Prefs.KEY_WIFI, true) ? "1" : "0"});
        c.addRow(new Object[] {Prefs.KEY_STYLE, String.valueOf(p.getInt(Prefs.KEY_STYLE, 0))});
        c.addRow(new Object[] {Prefs.KEY_WIFI_STYLE, String.valueOf(p.getInt(Prefs.KEY_WIFI_STYLE, 0))});
        return c;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
