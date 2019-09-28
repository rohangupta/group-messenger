package edu.buffalo.cse.cse486586.groupmessenger2;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;

/**
 * GroupMessengerProvider is a key-value table. Once again, please note that we do not implement
 * full support for SQL as a usual ContentProvider does. We re-purpose ContentProvider's interface
 * to use it as a key-value table.
 * 
 * Please read:
 * 
 * http://developer.android.com/guide/topics/providers/content-providers.html
 * http://developer.android.com/reference/android/content/ContentProvider.html
 * 
 * before you start to get yourself familiarized with ContentProvider.
 * 
 * There are two methods you need to implement---insert() and query(). Others are optional and
 * will not be tested.
 * 
 * @author stevko
 *
 */
public class GroupMessengerProvider extends ContentProvider {

    static final String TAG = GroupMessengerProvider.class.getSimpleName();
    private static final String KEY_FIELD = "key";
    private static final String VALUE_FIELD = "value";

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        // You do not need to implement this.
        return 0;
    }

    @Override
    public String getType(Uri uri) {
        // You do not need to implement this.
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        /*
         * TODO: You need to implement this method. Note that values will have two columns (a key
         * column and a value column) and one row that contains the actual (key, value) pair to be
         * inserted.
         * 
         * For actual storage, you can use any option. If you know how to use SQL, then you can use
         * SQLite. But this is not a requirement. You can use other storage options, such as the
         * internal storage option that we used in PA1. If you want to use that option, please
         * take a look at the code for PA1.
         */
        Log.v("insert", values.toString());

        /*
            References:
            1. Content Provider Android Documentation
            https://developer.android.com/guide/topics/providers/content-provider-creating
            2. File storage Android Documentation
            https://developer.android.com/training/data-storage/files#java
            3. Reading and writing string from a file on Android
            https://stackoverflow.com/questions/14376807/how-to-read-write-string-from-a-file-in-android
            4. Overwriting a file in internal storage on Android
            https://stackoverflow.com/questions/36740254/how-to-overwrite-a-file-in-sdcard-in-android
         */

        String filename = values.getAsString(KEY_FIELD);
        String fileContents = values.getAsString(VALUE_FIELD) + "\n";
        FileOutputStream outputStream;

        try {
            File file = new File(getContext().getFilesDir(), filename);
            if (file.exists()) {
                outputStream = new FileOutputStream(file, false);
            } else {
                outputStream = new FileOutputStream(file);
            }
            outputStream.write(fileContents.getBytes());
            outputStream.close();
        } catch (Exception e) {
            Log.e(TAG, "Failed to write file.");
        }

        return uri;
    }

    @Override
    public boolean onCreate() {
        // If you need to perform any one-time initialization task, please do it here.
        return false;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        // You do not need to implement this.
        return 0;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        /*
         * TODO: You need to implement this method. Note that you need to return a Cursor object
         * with the right format. If the formatting is not correct, then it is not going to work.
         *
         * If you use SQLite, whatever is returned from SQLite is a Cursor object. However, you
         * still need to be careful because the formatting might still be incorrect.
         *
         * If you use a file storage option, then it is your job to build a Cursor * object. I
         * recommend building a MatrixCursor described at:
         * http://developer.android.com/reference/android/database/MatrixCursor.html
         */
        Log.v("query", selection);

        String value = "";
        try {
            /*
            References:
             1. Content Provider Android Documentation
            https://developer.android.com/guide/topics/providers/content-provider-creating
            2. Reading file contents from internal storage on Android
            https://stackoverflow.com/questions/14768191/how-do-i-read-the-file-content-from-the-internal-storage-android-app
             */
            if (getContext().openFileInput(selection) == null) {
                Log.e(TAG, "NULL");
            }
            FileInputStream inputStream = getContext().openFileInput(selection);
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(inputStream));
            value = in.readLine();
            inputStream.close();
        } catch (Exception e) {
            Log.e(TAG, "Failed to read file.");
            //Log.e(TAG, e.getMessage());
        }

        MatrixCursor cursor = new MatrixCursor(new String[]{KEY_FIELD, VALUE_FIELD});
        cursor.addRow(new String[]{selection, value});
        return cursor;
    }
}
