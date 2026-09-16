package allen.town.focus_common.util;

import android.annotation.SuppressLint;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.Pair;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.text.DecimalFormat;

public class FileUtils {

    private static final String THIS_FILE = "FileUtils";

    public static final String TMP_FILE_SUFFIX = "TEMP";

    private static final float MEM_WARNING_RATIO = 0.10f;
    private static final int MEM_WARNING_MB = 50;// MB
    private static final float SDCARD_WARNING_RATIO = 0.05f;
    private static final int SDCARD_WARNING_MB = 50;// MB

    static final long ONE_KB = 1024;
    static final long ONE_MB = 1024 * ONE_KB;
    static final long ONE_GB = 1024 * ONE_MB;
    static final long TEN_GB = 10 * 1024 * ONE_MB;
    static final String SIZE_FORMAT = "%s%s";

    static final String PREFIX_FILE = "file:";
    static final String PREFIX_CONTENT = "content:";
    static final String CONTAIN_IMAGES = "/images/";
    static final String CONTAIN_AUDIO = "/audio/";
    static final String CONTAIN_VIDEO = "/video/";
    static final String CONTAIN_MEDIA = "/media/";
    static final String CONTAIN_STORAGE = "/storage/emulated/";
    static final String FILEPROVIDER = ".fileprovider/";

    public static boolean isSdCardMounted() {
        return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
    }

    /**
     * create data folder if not exist,such as record,image folder
     */
    public static void checkDataFolder(String path) {
        String sdStatus = Environment.getExternalStorageState();
        if (!sdStatus.equals(Environment.MEDIA_MOUNTED)) {// check SD card
            // whether
            // avaiable/writeable
            Timber.v("SD card is not avaiable/writeable right now.");
            return;
        }

        if (path == null) {
            return;
        }

        File fileDir = new File(path);
        if (!fileDir.exists()) {
            if (!fileDir.mkdirs()) {
                Timber.e("Unable to create record directory");
            }
        }
    }


    /**
     * 获取手机的总内存
     *
     * @param context
     * @return
     */


    /**
     * rename file or move file to another place
     * 从data目录拷贝到sd上不能使用该方法，应使用copyfile
     *
     * @param originalPath
     * @param targetPath
     * @return
     */
    public static boolean renameFile(String originalPath, String targetPath) {
        String dir = targetPath.substring(0, targetPath.lastIndexOf(File.separator));
        File file = new File(dir);
        if (!file.exists()) {
            file.mkdirs();
        }
        Timber.d("renameFile from originalPath " + originalPath + " to " + targetPath);
        boolean flag = new File(originalPath).renameTo(new File(targetPath));
        if (!flag) {
            Timber.e("rename file failed ");
        }
        return flag;
    }

    /**
     * delete a file or folder
     *
     * @param fileName
     * @return
     */
    public static boolean delete(String fileName) {
        if (fileName == null) {
            return false;
        }
        File file = new File(fileName);
        if (!file.exists()) {
            return false;
        } else {
            if (file.isFile()) {
                return deleteFile(fileName);
            } else {
                return deleteDirectory(fileName);
            }
        }
    }

    /**
     * delete a file or folder
     *
     * @param file
     * @return
     */
    public static boolean delete(File file) {
        if (file != null) {
            return delete(file.getPath());
        }
        return false;
    }

    /**
     * 删除单个文件
     * 视频和图片的缩率图不删除
     *
     * @param fileName 被删除文件的文件名
     * @return 单个文件删除成功返回true, 否则返回false
     */
    private static boolean deleteFile(String fileName) {
        if (fileName == null) {
            return false;
        }
        File file = new File(fileName);
        if (file.isFile() && file.exists()) {
            return file.delete();
        } else {
            return false;
        }
    }

    /**
     * 删除目录（文件夹）以及目录下的文件
     *
     * @param dir 被删除目录的文件路径
     * @return 目录删除成功返回true, 否则返回false
     */
    public static boolean deleteDirectory(String dir) {
        if (dir == null) {
            return false;
        }
        // 如果dir不以文件分隔符结尾，自动添加文件分隔符
        if (!dir.endsWith(File.separator)) {
            dir = dir + File.separator;
        }
        File dirFile = new File(dir);
        // 如果dir对应的文件不存在，或者不是一个目录，则退出
        if (!dirFile.exists() || !dirFile.isDirectory()) {
            return false;
        }
        // 删除文件夹下的所有文件(包括子目录)
        File[] files = dirFile.listFiles();
        if (files != null) {
            for (int i = 0; i < files.length; i++) {
                // 删除子文件
                if (files[i].isFile()) {
                    deleteFile(files[i].getAbsolutePath());
                }
                // 删除子目录
                else {
                    deleteDirectory(files[i].getAbsolutePath());
                }
            }
        }

        // 删除当前目录
        return dirFile.delete();

    }

    /**
     * 从data目录拷贝到sd上不能使用该方法，应使用copyfile
     *
     * @param originalPath
     * @param targetFolder
     * @param file
     * @return
     */
    public static boolean renameFile(String originalPath, String targetFolder, String file) {
        if (targetFolder == null) {
            return false;
        }

        File targetFile = new File(targetFolder);
        if (!targetFile.exists()) {
            if (!targetFile.mkdirs()) {
                Timber.e("Unable to create directory " + targetFolder);
            }
        }

        return renameFile(originalPath, targetFolder + File.separator + file);
    }

    public static boolean copyFile(String originalPath, String targetFolder, String file) {
        if (targetFolder == null) {
            return false;
        }

        File targetFile = new File(targetFolder);
        if (!targetFile.exists()) {
            if (!targetFile.mkdirs()) {
                Timber.e("Unable to create directory " + targetFolder);
            }
        }
        return copyFile(originalPath, targetFolder + File.separator + file);

    }

    public static boolean copyFile(InputStream inputStream, String targetPath) {
        BufferedInputStream inBuff = null;
        BufferedOutputStream outBuff = null;
        boolean isCopySuccess = true;
        File file = new File(targetPath);
        String dirPath = file.getParent();
        if (!TextUtils.isEmpty(dirPath)) {
            file = new File(dirPath);
            if (!file.exists()) {
                file.mkdirs();
            }
        }
        try {
            // 新建文件输入流并对它进行缓冲
            inBuff = new BufferedInputStream(inputStream);

            // 新建文件输出流并对它进行缓冲
            outBuff = new BufferedOutputStream(new FileOutputStream(targetPath));

            // 缓冲数组
            byte[] b = new byte[1024 * 5];
            int len;
            while ((len = inBuff.read(b)) != -1) {
                outBuff.write(b, 0, len);
            }
            // 刷新此缓冲的输出流
            outBuff.flush();
        } catch (Exception e) {
            Timber.e("copyFile failed cause " + e.toString());
            isCopySuccess = false;
        } finally {
            // 关闭流
            try {
                if (inBuff != null)
                    inBuff.close();
                if (outBuff != null)
                    outBuff.close();
            } catch (IOException e) {
                Timber.e("close file stream failed cause " + e.toString());
            }
        }
        return isCopySuccess;
    }

    public static boolean copyFile(String originalPath, String targetPath) {
        Timber.d("copyFile，originalPath：" + originalPath
                + ",targetPath:" + targetPath);
        BufferedInputStream inBuff = null;
        BufferedOutputStream outBuff = null;
        boolean isCopySuccess = true;
        File file = new File(targetPath);
        String dirPath = file.getParent();
        if (!TextUtils.isEmpty(dirPath)) {
            file = new File(dirPath);
            if (!file.exists()) {
                file.mkdirs();
            }
        }
        try {
            // 新建文件输入流并对它进行缓冲
            inBuff = new BufferedInputStream(new FileInputStream(originalPath));

            // 新建文件输出流并对它进行缓冲
            outBuff = new BufferedOutputStream(new FileOutputStream(targetPath));

            // 缓冲数组
            byte[] b = new byte[1024 * 5];
            int len;
            while ((len = inBuff.read(b)) != -1) {
                outBuff.write(b, 0, len);
            }
            // 刷新此缓冲的输出流
            outBuff.flush();
        } catch (Exception e) {
            Timber.e("copyFile failed cause " + e.toString());
            isCopySuccess = false;
        } finally {
            // 关闭流
            try {
                if (inBuff != null)
                    inBuff.close();
                if (outBuff != null)
                    outBuff.close();
            } catch (IOException e) {
                Timber.e("close file stream failed cause " + e.toString());
            }
        }
        return isCopySuccess;
    }

    public static boolean isFileExist(String path) {
        return (path != null && new File(path).exists());
    }

    public static long getFileSizes(String filename) {
        return filename == null ? 0L : getFileSizes(new File(filename));
    }

    public static String read(Uri uri) {
        File file = new File(uri.getPath());
        if (!file.exists()) {
            return "";
        }

        FileInputStream fis = null;
        InputStreamReader isr = null;
        BufferedReader br = null;

        StringBuffer sb = new StringBuffer();
        try {
            fis = new FileInputStream(file);//通过字节流获取
            isr = new InputStreamReader(fis);
            br = new BufferedReader(isr);

            String line;
            sb.append(br.readLine());
            while ((line = br.readLine()) != null) {
                sb.append("\n" + line);
            }

            br.close();
            isr.close();
            fis.close();
        } catch (FileNotFoundException e) {
            Timber.d("read", e);
        } catch (IOException e) {
            Timber.d("read", e);
        } finally {
            try {
                if (br != null) {
                    br.close();
                }
                if (isr != null) {
                    isr.close();
                }
                if (fis != null) {
                    fis.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return sb.toString();
    }

    /**
     * 获取文件大小，不是文件夹
     *
     * @param f
     * @return
     */
    public static long getFileSizes(File f) {
        long s = 0;
        if (f.exists()) {
            FileInputStream fis = null;
            try {
                fis = new FileInputStream(f);
                s = fis.available();
            } catch (Exception e) {
                Timber.e(e, "get file size failed");
            } finally {
                if (fis != null) {
                    try {
                        fis.close();
                    } catch (IOException e) {
                        Timber.e(e, "");
                    }
                }
            }
        }
        return s;
    }

    public static double formatFileSizeKB(long fileS) {
        double size = (double) fileS / 1024;
        return size;
    }

    public static String getReadableFileSize(String filePath) {
        String res = "0B";
        if (filePath != null) {
            res = getReadableFileSize(getFileSizes(new File(filePath)));
        }
        return res;
    }

//    public static String getNewReadableFileSize(long s) {
//        String res;
//        if (s < ONE_KB) {
//            res = s + "B";
//        } else if (s < ONE_MB) {
//            res = String.format(SIZE_FORMAT, ((float) s) / ONE_KB, "K");
//        } else if (s < ONE_GB) {
//            res = String.format(SIZE_FORMAT, ((float) s) / ONE_MB, "M");
//        } else {
//            res = String.format(SIZE_FORMAT, ((float) s) / ONE_GB, "G");
//        }
//        return res;
//    }

    public static String getReadableFileSize(long s) {
        DecimalFormat decimalFormat = new DecimalFormat("0.00");
        String res;
        if (s < ONE_KB) {
            res = s + "B";
        } else if (s < ONE_MB) {
            res = String.format(SIZE_FORMAT, decimalFormat.format((float) s / ONE_KB), "K");
        } else if (s < ONE_GB) {
            res = String.format(SIZE_FORMAT, decimalFormat.format((float) s / ONE_MB), "M");
        } else {
            res = String.format(SIZE_FORMAT, decimalFormat.format((float) s / ONE_GB), "G");
        }
        return res;
    }

    public static Pair<String, String> getReadableFileSizeInt(long s) {
        DecimalFormat decimalFormat = new DecimalFormat("0.00");
        if (s < ONE_KB) {
            return new Pair<>(String.valueOf(s), "B");
        } else if (s < ONE_MB) {
            return new Pair<>(decimalFormat.format((float) s / ONE_KB), "K");
        } else if (s < ONE_GB) {
            return new Pair<>(decimalFormat.format((float) s / ONE_MB), "M");
        } else {
            return new Pair<>(decimalFormat.format((float) s / ONE_GB), "G");
        }
    }

    public static FileInfo getFileInfo(File filePath) {
        return getFileInfo(filePath.getAbsolutePath());
    }

    public static FileInfo getFileInfo(String filePath) {
        if (TextUtils.isEmpty(filePath))
            return null;
        File file = new File(filePath);

        FileInfo args = new FileInfo();
        args.filePath = file.getAbsolutePath();
        args.fileFolder = file.getParent();

        String fileName = file.getName();
        int idx = fileName.lastIndexOf(".");
        if (idx > 0) {
            args.fileName = file.getName().substring(0, idx);
        } else {
            args.fileName = fileName;
        }

        args.fileSuffix = getFileSuffix(file);

        return args;
    }

    public static String getFileSuffix(File file) {
        String fileName = file.getName();
        int idx = fileName.lastIndexOf(".");
        if (idx > 0) {
            return fileName.substring(idx + 1);
        }
        return "";
    }

    public static String getFileSuffix(String filename) {
        if (filename != null) {
            int idx = filename.lastIndexOf(".");
            if (idx > 0) {
                return filename.substring(idx + 1);
            }
        }
        return null;
    }

    public static final class FileInfo {
        public String filePath;
        public String fileName;
        public String fileSuffix;
        public String fileFolder;
    }

    /**
     * query the absolute path from MediaStore by uri.
     *
     * @param context context
     * @param uri     the uri such as content://...
     * @return the absolute path of the file identified by uri
     */
    public static String queryImagePath(final Context context, final Uri uri) {
        String path = null;
        if (uri != null) {
            //这里可能大部分场景都没有用了，Permission Denial: opening provider android.support.v4.content.FileProvider from ProcessRecord
            //8.0以后都是通过FileProvider提供的该Uri，但是提供该Uri的app如果没有给我们的app授权我们是无法访问此Uri的
            Cursor cursor = context.getContentResolver().query(uri, new String[]{MediaStore.Images.Media.DATA}, null, null, null);
            if (cursor != null) {
                int index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                if (cursor.moveToFirst()) {
                    path = cursor.getString(index);
                }
                cursor.close();
            }
        }
        return path;
    }

    /**
     * query the absolute path from MediaStore by uri.
     *
     * @param context context
     * @param uri     the uri such as content://...
     * @return the absolute path of the file identified by uri
     */
    private static String queryVideoPath(final Context context, final Uri uri) {
        String path = null;
        if (context != null && uri != null) {
            Cursor cursor = context.getContentResolver().query(uri, new String[]{MediaStore.Video.Media.DATA}, null, null, null);
            if (cursor != null) {
                int index = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA);
                if (cursor.moveToFirst()) {
                    path = cursor.getString(index);
                }
                cursor.close();
            }
        }
        return path;
    }

    private static String queryAudioPath(final Context context, final Uri uri) {
        String path = null;
        if (context != null && uri != null) {
            Cursor cursor = context.getContentResolver().query(uri, new String[]{MediaStore.Audio.Media.DATA}, null, null, null);
            if (cursor != null) {
                int index = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
                if (cursor.moveToFirst()) {
                    path = cursor.getString(index);
                }
                cursor.close();
            }
        }
        return path;
    }


    private static String queryMediaPath(final Context context, final Uri uri) {
        String path = null;
        if (context != null && uri != null) {
//            context.getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            Cursor cursor = context.getContentResolver().query(uri, new String[]{MediaStore.MediaColumns.DATA}, null, null, null);
            if (cursor != null) {
                int index = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA);
                if (cursor.moveToFirst()) {
                    path = cursor.getString(index);
                }
                cursor.close();
            }
        }
        return path;
    }


    public static String parseFilename(String filePath) {
        if (filePath != null) {
            int i = filePath.lastIndexOf('/');
            if (i >= 0) {
                return filePath.substring(i + 1);
            }
        }
        return filePath;
    }

    /**
     * 获取文件夹或者文件大小，都可以
     *
     * @param file
     * @return
     */
    public static long getOrFilesSize(File file) {
        return getOrFilesSize(file.getAbsolutePath());
    }

    /**
     * 获取文件夹或者文件大小，都可以
     *
     * @param filePath
     * @return
     */
    public static long getOrFilesSize(String filePath) {
        File file = new File(filePath);
        long blockSize = 0;
        try {
            if (file.isDirectory()) {
                blockSize = getFileSs(file);
            } else {
                blockSize = getFileSizes(file);
            }
        } catch (Exception e) {
            Timber.e(e, "");
            Timber.e("获取文件大小", "获取失败!");
        }
        return blockSize;
    }


    /**
     * 获取指定文件夹
     *
     * @param f
     * @return
     * @throws
     */
    private static long getFileSs(File f) throws Exception {
        long size = 0;
        File flist[] = f.listFiles();
        if (flist != null) {
            for (int i = 0; i < flist.length; i++) {
                if (flist[i].isDirectory()) {
                    size = size + getFileSs(flist[i]);
                } else {
                    size = size + getFileSizes(flist[i]);
                }
            }
        }
        return size;
    }

    /**
     * 判断视频是否小于100M，大于100M不能上传
     *
     * @param size
     * @return
     */
    public static boolean videoCanUpload(int size) {
        return size <= 100 * 1024 * 1024;
    }

    /**
     * 视频需要需要压缩uploadFileToShare
     *
     * @return
     */
    public static boolean videoNeedCompress(String path) {
        File file = new File(path);
        if (file.exists()) {
            return file.length() > 10 * 1024 * 1024;
        }
        return false;
    }

    /**
     * 转换文件大小
     *
     * @param fileS
     * @return
     */
    public static String formetFileSize(long fileS) {
        DecimalFormat df = new DecimalFormat("#.00");
        String fileSizeString = "";
        String wrongSize = "0B";
        if (fileS == 0) {
            return wrongSize;
        }
        if (fileS < 1024) {
            fileSizeString = df.format((double) fileS) + "B";
        } else if (fileS < 1048576) {
            fileSizeString = df.format((double) fileS / 1024) + "KB";
        } else if (fileS < 1073741824) {
            fileSizeString = df.format((double) fileS / 1048576) + "MB";
        } else {
            fileSizeString = df.format((double) fileS / 1073741824) + "GB";
        }
        return fileSizeString;
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is ExternalStorageProvider.
     */
    public static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is DownloadsProvider.
     */
    public static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is MediaProvider.
     */
    public static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }

    /**
     * Get the value of the data column for this Uri. This is useful for
     * MediaStore Uris, and other file-based ContentProviders.
     *
     * @param context       The context.
     * @param uri           The Uri to query.
     * @param selection     (Optional) Filter used in the query.
     * @param selectionArgs (Optional) Selection arguments used in the query.
     * @return The value of the _data column, which is typically a file path.
     */
    public static String getDataColumn(Context context, Uri uri, String selection,
                                       String[] selectionArgs) {

        Cursor cursor = null;
        final String column = "_data";
        final String[] projection = {
                column
        };

        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs,
                    null);
            if (cursor != null && cursor.moveToFirst()) {
                final int column_index = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(column_index);
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return null;
    }

    /**
     * Get a file path from a Uri. This will get the the path for Storage Access
     * Framework Documents, as well as the _data field for the MediaStore and
     * other file-based ContentProviders.
     *
     * @param context The context.
     * @param uri     The Uri to query.
     * @author paulburke
     */
    @SuppressLint("NewApi")
    public static String getPath(final Context context, final Uri uri) {

        final boolean isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;

        // DocumentProvider
        if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
            // ExternalStorageProvider
            if (isExternalStorageDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                if ("primary".equalsIgnoreCase(type)) {
                    return context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) + "/" + split[1];
                }

                // TODO handle non-primary volumes
            }
            // DownloadsProvider
            else if (isDownloadsDocument(uri)) {

                final String id = DocumentsContract.getDocumentId(uri);
                if (id.contains("raw:")) {
                    final String[] idArray = id.split("raw:");
                    return idArray[1];
                } else {
                    final Uri contentUri = ContentUris.withAppendedId(
                            Uri.parse("content://downloads/public_downloads"), Long.parseLong(id));

                    return getDataColumn(context, contentUri, null, null);
                }
            }
            // MediaProvider
            else if (isMediaDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                Uri contentUri = null;
                if ("image".equals(type)) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }

                final String selection = "_id=?";
                final String[] selectionArgs = new String[]{
                        split[1]
                };

                return getDataColumn(context, contentUri, selection, selectionArgs);
            }
        }
        // MediaStore (and general)
        else if ("content".equalsIgnoreCase(uri.getScheme())) {
            return getDataColumn(context, uri, null, null);
        }
        // File
        else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }

        return null;
    }

//    public static void writeStr(String path, String content) {
//        FileWriter writer = null;
//        try {
//            String date = DateUtils.formatToDateAndTime(System.currentTimeMillis());
//            content += "\r\n";
//            content = date + "--" + content;
////            String path = CustomDistribution.LOGS_FOLDER + File.separator + "CallLog.txt";
//            File file = new File(path);
//            if (!file.exists()) {
//                file.createNewFile();
//            }
//            writer = new FileWriter(file, true);
//            writer.write(content);
//        } catch (IOException e) {
//            Log.e("","",e);
//        } finally {
//            if (writer != null) {
//                try {
//                    writer.close();
//                } catch (IOException e) {
//                    Log.e("","",e);
//                }
//            }
//        }
//    }

    private static int BUFFER_SIZE = 8 * 1024;

    /**
     * 文件留拷贝
     *
     * @param is
     * @param os
     * @return 拷贝的文件大小
     * @throws IOException
     */
    public static long copy(InputStream is, OutputStream os) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        long sum = 0;
        int len;
        while ((len = is.read(buffer)) != -1) {
            os.write(buffer, 0, len);
            sum += len;
        }
        return sum;
    }

    public static synchronized String readAssetFile(Context context, String path) {
        InputStream inputStream = null;
        try {
            inputStream = context.getAssets().open(path);
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, Constants.DECODE));
            String line;
            StringBuilder fileContent = new StringBuilder();
            do {
                line = bufferedReader.readLine();
                // ignore the comments string in file
                if (line != null && !line.matches("^\\s*//.*")) {
                    fileContent.append(line);
                }
            } while (line != null);
            bufferedReader.close();
            inputStream.close();
            return fileContent.toString();
        } catch (Exception e) {
            Timber.e(e, "");
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException exception) {
                    Timber.e(exception, "");
                }
            }
        }
        return null;
    }


    private static String getAbsolutePath(Context context, Uri uri) {
        String path = null;
        InputStream inputStream = null;
        FileOutputStream fos = null;
        Cursor cursor = null;

        if (context != null && uri != null) {
            try {
                cursor = context.getContentResolver().query(uri, null, null, null, null);
                if (cursor != null) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                    if (cursor.moveToFirst()) {
                        String fileName = cursor.getString(nameIndex);
                        long fileSize = cursor.getLong(sizeIndex);
                        path = context.getExternalCacheDir() + File.separator + MD5.MD5Hash(uri.toString()) + File.separator + fileName;
                        File file = new File(path);
                        //文件存在，直接返回
                        if (file.exists()) {
                            if (fileSize == file.length()) {
                                return path;
                            } else {
                                file.delete();
                            }
                        } else {
                            File parentFile = new File(file.getParent());
                            parentFile.mkdirs();
                        }

                        file.createNewFile();
                        inputStream = context.getContentResolver().openInputStream(uri);
                        if (file.exists() && null != inputStream) {
                            int count = -1;
                            byte[] buffer = new byte[1024];
                            fos = new FileOutputStream(file);
                            while ((count = inputStream.read(buffer)) != -1) {
                                fos.write(buffer, 0, count);
                            }
                        }

                    }
                }
            } catch (Exception e) {
                path = null;
                Timber.e("getAbsolutePath failed " + e.toString());
            } finally {
                try {
                    if (fos != null) {
                        fos.close();
                    }
                } catch (IOException e) {
                    Timber.e(e, "");
                }
                try {
                    if (inputStream != null) {
                        inputStream.close();
                    }
                } catch (IOException e) {
                    Timber.e(e, "");
                }

                if (cursor != null) {
                    cursor.close();
                }
            }
        }

        return path;
    }


    public static String queryAbsolutePathByUri(Context context, Uri uri) {
        Timber.i("try to queryAbsolutePathByUri");
        String path = null;
        try {
            if (context != null && uri != null) {
                String authority = uri.getAuthority();
                String uriStr = uri.toString();
                Timber.i("queryAbsolutePathByUri uriStr " + uriStr);
                if (uriStr.startsWith(PREFIX_CONTENT)) {
                    if (uriStr.contains(CONTAIN_AUDIO)) {
                        path = queryAudioPath(context, uri);
                    } else if (uriStr.contains(CONTAIN_IMAGES)) {
                        path = queryImagePath(context, uri);
                    } else if (uriStr.contains(CONTAIN_VIDEO)) {
                        path = queryVideoPath(context, uri);
                    } else if (uriStr.contains(CONTAIN_STORAGE)) {
                        path = uriStr.substring(uriStr.indexOf(CONTAIN_STORAGE));
                    } else if (uriStr.contains(CONTAIN_MEDIA)) {
                        path = queryMediaPath(context, uri);
                    } else if (!TextUtils.isEmpty(authority)/* && uriStr.contains(FILEPROVIDER)*/) {
                        path = getAbsolutePath(context, uri);
                    }

                } else if (uriStr.startsWith(PREFIX_FILE)) {
                    path = uri.getEncodedPath();
                } else if (uriStr.startsWith(Environment.getExternalStorageDirectory().getPath())) {
                    path = uriStr;
                }
            }
            if (!TextUtils.isEmpty(path)) {
                path = URLDecoder.decode(path);
            }
        } catch (Exception e) {
            Timber.e("queryAbsolutePathByUri " + e.toString());
        }
        Timber.i("queryAbsolutePathByUri path " + path);
        return path;
    }
}
