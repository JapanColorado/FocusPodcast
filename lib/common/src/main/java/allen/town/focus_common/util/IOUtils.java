package allen.town.focus_common.util;
/*
 *
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.content.Context;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Scanner;
import java.util.Stack;



public class IOUtils {


    public static String getPath(Uri uri, Context context) {
        String[] filePathColumn = {MediaStore.Images.Media.DATA};
        String filePath = null;

        try {
            Cursor cursor = context.getContentResolver().query(
                    uri, filePathColumn, null, null, null);
            cursor.moveToFirst();

            int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
            filePath = cursor.getString(columnIndex);
            cursor.close();
        } catch (Exception e) {
            Timber.e("getpath failed " + e.toString());
            filePath = uri.getPath();
        }

        if (filePath == null) {
            filePath = uri.getPath();
            Timber.v("getPath " + filePath);
        }

        return filePath;
    }



    public static String readChangelog(Context context) {
        String ret = "";
        try {
            AssetManager assetManager = context.getAssets();
            Scanner in = new Scanner(assetManager.open("changelog.txt"));

            while (in.hasNextLine()) {
                ret += in.nextLine() + "\n";
            }
        } catch (FileNotFoundException e) {

        } catch (IOException e) {

        }

        return ret;
    }

    public static void trimCache(Context context) {
        try {
            File dir = context.getCacheDir();
            if (dir != null && dir.isDirectory()) {
                deleteDir(dir);
            }
        } catch (Exception e) {

        }
    }

    public static boolean deleteDir(File dir) {
        if (dir != null && dir.isDirectory()) {
            String[] children = dir.list();
            for (int i = 0; i < children.length; i++) {
                boolean success = deleteDir(new File(dir, children[i]));
                if (!success) {
                    return false;
                }
            }
        }

        // The directory is now empty so delete it
        return dir.delete();
    }


    public static long dirSize(File dir) {
        long result = 0;

        Stack<File> dirlist= new Stack<File>();
        dirlist.clear();

        dirlist.push(dir);

        while(!dirlist.isEmpty())
        {
            File dirCurrent = dirlist.pop();

            File[] fileList = dirCurrent.listFiles();
            if (fileList != null) {
                for (int i = 0; i < fileList.length; i++) {

                    if (fileList[i].isDirectory())
                        dirlist.push(fileList[i]);
                    else
                        result += fileList[i].length();
                }
            }
        }

        return result;
    }

    public static String readAsset(Context context, String assetTitle) {
        String ret = "";
        try {
            AssetManager assetManager = context.getAssets();
            Scanner in = new Scanner(assetManager.open(assetTitle));

            while (in.hasNextLine()) {
                ret += in.nextLine() + "\n";
            }
        } catch (FileNotFoundException e) {

        } catch (IOException e) {

        }

        return ret;
    }

    public byte[] convertToByteArray(Bitmap bitmap) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        return stream.toByteArray();
    }
}
