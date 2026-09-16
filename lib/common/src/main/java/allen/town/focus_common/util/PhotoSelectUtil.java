package allen.town.focus_common.util;


import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import java.io.File;
import java.io.FileNotFoundException;

public class PhotoSelectUtil {
    public static final int SELECT_PHOTO = 1;
    private FragmentActivity activity;

    public PhotoSelectUtil(FragmentActivity fragmentActivity) {
        this.activity = fragmentActivity;
    }

    public void pickPhoto() {
        try {
            if (Build.VERSION.SDK_INT <= 19) {
                Intent intent = new Intent("android.intent.action.GET_CONTENT");
                intent.setType("image/*");
                this.activity.startActivityForResult(intent, 1);
                return;
            }
            Intent intent2 = new Intent("android.intent.action.OPEN_DOCUMENT");
            intent2.addCategory("android.intent.category.OPENABLE");
            intent2.setType("image/*");
            intent2.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*");
            this.activity.startActivityForResult(intent2, 1);
        } catch (Exception unused) {
            this.activity.finish();
        }
    }

    public void pickPhoto(Fragment fragment) {
        try {
            Intent intent = new Intent("android.intent.action.OPEN_DOCUMENT");
            intent.addCategory("android.intent.category.OPENABLE");
            intent.setType("image/*");
            intent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*");
            fragment.startActivityForResult(intent, 1);
        } catch (Exception unused) {
            this.activity.finish();
        }
    }

    public Uri handleActivityResult(int i, int i2, Intent intent) {
        if (i2 == -1 && i == 1) {
            return intent.getData();
        }
        return null;
    }

    private void refreshSystemAlbum(File file) {
        try {
            MediaStore.Images.Media.insertImage(this.activity.getContentResolver(), file.getAbsolutePath(), "CropImage.jpeg", (String) null);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        this.activity.sendBroadcast(new Intent("android.intent.action.MEDIA_SCANNER_SCAN_FILE", Uri.fromFile(file)));
    }

    private String getPicturePath(Uri uri) {
        if (!uri.toString().contains("file")) {
            return UriUtil.getPath(this.activity, uri);
        }
        return uri.getPath();
    }
}