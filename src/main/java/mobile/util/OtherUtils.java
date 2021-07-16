package mobile.util;

import java.util.List;

public class OtherUtils {

    public static boolean isEmpty(Object obj) {
        try {
            if (obj == null) {
                return true;
            }
            String str = String.valueOf(obj);
            if (str.equals("null")) {
                return true;
            }
            if (str.equals("")) {
                return true;
            }
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return true;
        }
    }

    public static boolean isNotEmpty(Object obj) {
        try {
            if (obj == null) {
                return false;
            }
            String str = String.valueOf(obj).trim();
            if (str.equals("")) {
                return false;
            }
            if (str.equals("null")) {
                return false;
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static <T> boolean isListNotEmpty(List<T> list) {
        try {
            if (list == null) {
                return false;
            }
            if (list.size() <= 0) {
                return false;
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean isNotBlank(Object obj) {
        try {
            if (obj == null) {
                return false;
            }
            String str = String.valueOf(obj).trim();
            if (str.equals("")) {
                return false;
            }
            if (str.equals("null")) {
                return false;
            }
            if (str.equals("0")) {
                return false;
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

    }

}
