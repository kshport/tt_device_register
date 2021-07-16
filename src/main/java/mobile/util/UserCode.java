package mobile.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import lombok.Data;

@Data
public class UserCode {
    private int code;
    private int count;
    private String msg;
    private Object data;

    public UserCode(int code, String msg, int count, Object data) {
        this.code = code;
        this.msg = msg;
        this.count = count;
        this.data = data;
    }

    public String toJsonString() {
        JSONObject js = new JSONObject();
        js.put("code", code);
        js.put("msg", msg);
        js.put("count", count);
        js.put("data", data);
        return JSON.toJSONString(js, SerializerFeature.WriteMapNullValue);
    }

    public void set(int code, String msg, int count, Object data) {
        this.code = code;
        this.msg = msg;
        this.count = count;
        this.data = data;
    }

    public void setCode(boolean result) {
        if (result) {
            this.code = 0;
        } else {
            this.code = -1;
        }
    }

    public void setCode(int code) {
        this.code = code;

    }

    public static String get(int code, String msg, int count, String data) {

        JSONObject js = new JSONObject();
        js.put("code", code);
        js.put("msg", msg);
        js.put("count", count);
        js.put("data", data);
        return JSON.toJSONString(js, SerializerFeature.WriteMapNullValue);
    }

    public static String get(int code, String msg, JSONArray data, int count) {

        JSONObject js = new JSONObject();
        js.put("code", code);
        js.put("msg", msg);
        js.put("count", count);
        js.put("data", data);
        return JSON.toJSONString(js, SerializerFeature.WriteMapNullValue);
    }

    public static String get(int code, String msg, int count, Object data) {

        JSONObject js = new JSONObject();
        js.put("code", code);
        js.put("msg", msg);
        js.put("count", count);
        js.put("data", data);
        return JSON.toJSONString(js, SerializerFeature.WriteMapNullValue);
    }

}
