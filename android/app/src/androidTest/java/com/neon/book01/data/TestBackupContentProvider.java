package com.neon.book01.data;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.regex.Pattern;

/** Exported only by the androidTest APK to exercise ContentResolver URI streams. */
public final class TestBackupContentProvider extends ContentProvider {
    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9._-]{1,80}");

    @Override public boolean onCreate() { return true; }

    @Override public String getType(Uri uri) {
        return "application/vnd.book01.encrypted-backup";
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) { return null; }

    @Override public Uri insert(Uri uri, ContentValues values) { return null; }

    @Override public int delete(Uri uri, String selection, String[] selectionArgs) {
        try {
            return backupFile(uri).delete() ? 1 : 0;
        } catch (FileNotFoundException ignored) {
            return 0;
        }
    }

    @Override public int update(Uri uri, ContentValues values, String selection,
            String[] selectionArgs) { return 0; }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode)
            throws FileNotFoundException {
        if (uri == null) throw new FileNotFoundException("Missing test document URI");
        if (mode == null) throw new FileNotFoundException("Missing test document mode");
        File file = backupFile(uri);
        File parent = file.getParentFile();
        if (parent != null) parent.mkdirs();
        final int flags;
        if ("r".equals(mode)) {
            flags = ParcelFileDescriptor.MODE_READ_ONLY;
        } else if ("w".equals(mode)) {
            flags = ParcelFileDescriptor.MODE_WRITE_ONLY
                    | ParcelFileDescriptor.MODE_CREATE
                    | ParcelFileDescriptor.MODE_TRUNCATE;
        } else {
            throw new FileNotFoundException("Unsupported test mode");
        }
        return ParcelFileDescriptor.open(file, flags);
    }

    private File backupFile(Uri uri) throws FileNotFoundException {
        if (uri == null) throw new FileNotFoundException("Missing test document URI");
        String name = uri.getLastPathSegment();
        if (name == null) throw new FileNotFoundException("Missing test document name");
        if (!SAFE_NAME.matcher(name).matches()) {
            throw new FileNotFoundException("Invalid test document name");
        }
        if (getContext() == null) throw new FileNotFoundException("Test provider has no context");
        return new File(getContext().getCacheDir(), "backup-document-" + name);
    }
}
