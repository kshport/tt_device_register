package mobile.util;

import java.io.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class GZIPUtil {
    public static final String GZIP_ENCODE_UTF_8 = "UTF-8";

    public static byte[] compress(String str) {
        return compress(str, GZIP_ENCODE_UTF_8);
    }

    public static byte[] compress(String str, String encoding) {
        if (str == null || str.length() == 0) {
            return null;
        }

        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            compress(str.getBytes(encoding), out);
            return out.toByteArray();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        return null;

    }

    public static <T extends OutputStream> void compress(byte[] data, T out) {
        GZIPOutputStream gos = null;
        try {
            gos = new GZIPOutputStream(out);
            gos.write(data);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                gos.close();
            } catch (IOException e) {
            }
        }
    }

    public static byte[] uncompress(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        uncompress(new ByteArrayInputStream(bytes), out);
        if (null != out) {
            return out.toByteArray();
        }

        return null;
    }

    public static String uncompressToString(byte[] bytes) {
        return uncompressToString(bytes, GZIP_ENCODE_UTF_8);
    }

    public static String uncompressToString(byte[] bytes, String encoding) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        uncompress(new ByteArrayInputStream(bytes), out);
        if (null != out) {
            try {
                return out.toString(encoding);
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }

        return null;
    }


    public static <T extends InputStream, E extends OutputStream> void uncompress(T in, E out) {
        GZIPInputStream ungzip = null;
        try {
            ungzip = new GZIPInputStream(in);
            byte[] buffer = new byte[256];
            int n;
            while ((n = ungzip.read(buffer)) >= 0) {
                out.write(buffer, 0, n);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (null != ungzip) {
                try {
                    ungzip.close();
                } catch (IOException e) {
                }
            }
        }
    }

    public static void main(String[] args) {
    }
}
