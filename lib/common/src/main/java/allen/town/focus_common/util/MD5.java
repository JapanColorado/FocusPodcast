

package allen.town.focus_common.util;

import android.text.TextUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class MD5 {

    public static boolean checkMD5(String md5, File updateFile)
            throws IOException {
        if (TextUtils.isEmpty(md5) || updateFile == null) {
            return false;
        }

        String calculatedDigest = calculateMD5(updateFile);

        if (calculatedDigest == null) {
            return false;
        }
        return calculatedDigest.equalsIgnoreCase(md5);
    }

    private static String calculateMD5(File updateFile) {
//		MessageDigest digest = null;
//		try {
//			digest = MessageDigest.getInstance("MD5");
//		} catch (NoSuchAlgorithmException e) {
//			return null;
//		}
        InputStream is = null;
        try {
            is = new FileInputStream(updateFile);
        } catch (FileNotFoundException e) {
            return null;
        }
        return calculateMD5(is);
//		byte[] buffer = new byte[8192];
//		int read = 0;
//		try {
//			while ((read = is.read(buffer)) > 0) {
//				digest.update(buffer, 0, read);
//			}
//			byte[] md5sum = digest.digest();
//			BigInteger bigInt = new BigInteger(1, md5sum);
//			String output = bigInt.toString(16);
//			// Fill to 32 chars
//			output = String.format("%32s", output).replace(' ', '0');
//			return output;
//		} catch (IOException e) {
//			throw new RuntimeException("Unable to process file for MD5", e);
//		} finally {
//			try {
//				is.close();
//			} catch (IOException e) {
//				throw new RuntimeException(
//						"Unable to close input stream for MD5 calculation", e);
//			}
//		}
    }

    public static String calculateMD5(InputStream is) {
        if (is == null) {
            return null;
        }
        MessageDigest digest = null;
        try {
            digest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
        byte[] buffer = new byte[8192];
        int read = 0;
        try {
            while ((read = is.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
            byte[] md5sum = digest.digest();
            BigInteger bigInt = new BigInteger(1, md5sum);
            String output = bigInt.toString(16);
            // Fill to 32 chars
            output = String.format("%32s", output).replace(' ', '0');
            return output;
        } catch (IOException e) {
            throw new RuntimeException("Unable to process file for MD5", e);
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                throw new RuntimeException(
                        "Unable to close input stream for MD5 calculation", e);
            }
        }
    }


    public static String MD5Hash(String s) {
        MessageDigest m = null;

        try {
            m = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            Timber.e(e, "");
            return "";
        }

        try {
            m.update(s.getBytes(Constants.DECODE), 0, s.length());
        } catch (UnsupportedEncodingException e) {
            Timber.e(e, "");
            return "";
        }
        String hash = new BigInteger(1, m.digest()).toString(16);
        return hash;
    }

    public static String md5PassWord(String inputStr) {
        byte[] input = new byte[0];
        try {
            input = inputStr.getBytes(Constants.DECODE);
        } catch (UnsupportedEncodingException e) {
            Timber.e(e, "");
        }

        byte[] md5Bytes = md5(input);

        StringBuffer hexValue = new StringBuffer();
        for (int i = 0; i < md5Bytes.length; i++) {
            int val = ((int) md5Bytes[i]) & 0xff;
            if (val < 16)
                hexValue.append("0");
            hexValue.append(Integer.toHexString(val));
        }

        return hexValue.toString().toLowerCase();

    }

    private static final String MD5 = "MD5";

    /**
     * 对输入字符串进行md5散列.
     */
    private static byte[] md5(byte[] input) {
        return digest(input, MD5, null, 1);
    }

    /**
     * 对文件进行md5散列.
     */
    private static byte[] md5(InputStream input) throws IOException {
        return digest(input, MD5);
    }

    /**
     * 对字符串进行散列, 支持md5与sha1算法.
     */
    private static byte[] digest(byte[] input, String algorithm, byte[] salt, int iterations) {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);

            if (salt != null) {
                digest.update(salt);
            }

            byte[] result = digest.digest(input);

            for (int i = 1; i < iterations; i++) {
                digest.reset();
                result = digest.digest(result);
            }
            return result;
        } catch (GeneralSecurityException e) {
            Timber.e(e, "");
            return null;
        }
    }

    private static byte[] digest(InputStream input, String algorithm) throws IOException {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance(algorithm);
            int bufferLength = 8 * 1024;
            byte[] buffer = new byte[bufferLength];
            int read = input.read(buffer, 0, bufferLength);

            while (read > -1) {
                messageDigest.update(buffer, 0, read);
                read = input.read(buffer, 0, bufferLength);
            }

            return messageDigest.digest();
        } catch (GeneralSecurityException e) {
            Timber.e(e, "");
            return null;
        }
    }

}
