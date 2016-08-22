package com.sleepycatstudios.slowy;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

public class FileHelper {
    public static String getRealPathFromURI(Uri contentUri) {

        Cursor cursor = null;
        String str = null;
        try {
            Context context = App.getContext();
            String[] proj = { MediaStore.Images.Media.DATA };
            cursor = context.getContentResolver().query(contentUri,  proj, null, null, null);
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            cursor.moveToFirst();
            str = cursor.getString(column_index);
        } catch (Exception e) {
            e.printStackTrace();
            if (cursor != null) {
                cursor.close();
            }
        } finally {

        }

        if (cursor != null) {
            cursor.close();
        }
        return str;
    }
}
