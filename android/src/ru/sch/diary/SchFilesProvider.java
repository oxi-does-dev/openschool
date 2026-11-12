package ru.sch.diary;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/** Отдаёт скачанные файлы другим приложениям (PDF-просмотрщику) по content://. */
public final class SchFilesProvider extends ContentProvider {

    static final String AUTHORITY = "ru.sch.diary.files";

    @Override
    public boolean onCreate() {
        return true;
    }

    private File fileFor(Uri uri) {
        String name = uri.getLastPathSegment();
        if (name == null || name.isEmpty()) name = "file";
        return new File(getContext().getCacheDir(), name);
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode)
            throws FileNotFoundException {
        return ParcelFileDescriptor.open(fileFor(uri),
                ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        File f = fileFor(uri);
        String[] cols = projection == null
                ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}
                : projection;
        MatrixCursor c = new MatrixCursor(cols);
        Object[] row = new Object[cols.length];
        for (int i = 0; i < cols.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(cols[i])) {
                row[i] = f.getName();
            } else if (OpenableColumns.SIZE.equals(cols[i])) {
                row[i] = f.length();
            } else {
                row[i] = null;
            }
        }
        c.addRow(row);
        return c;
    }

    @Override
    public String getType(Uri uri) {
        String n = uri.getLastPathSegment();
        n = n == null ? "" : n.toLowerCase(java.util.Locale.ROOT);
        if (n.endsWith(".pdf")) return "application/pdf";
        if (n.endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }
        if (n.endsWith(".doc")) return "application/msword";
        if (n.endsWith(".xlsx")) {
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        }
        if (n.endsWith(".xls")) return "application/vnd.ms-excel";
        if (n.endsWith(".pptx")) {
            return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        }
        if (n.endsWith(".ppt")) return "application/vnd.ms-powerpoint";
        return "*/*";
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
    public int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        return 0;
    }
}
