package mobile;

import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import mobile.util.*;
import org.apache.commons.codec.binary.Base64;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.util.Base64Utils;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
@RestController
@SpringBootApplication

@RequestMapping(value = "/")
@Slf4j(topic = "PublicController")
public class PublicController {
    @Autowired
    private HttpServletRequest request;
    @Autowired
    private HttpServletResponse response;

    @RequestMapping(value = "/", method = RequestMethod.GET)
    @ResponseBody
    public void getIndex() throws IOException {
        // response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Content-type", "text/html;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.getOutputStream().write("Welcome into api host".getBytes());
    }


    @RequestMapping(value = "/api/device_log/so", method = RequestMethod.POST)
    @ResponseBody
    public void postDeviceLogSo(@RequestBody String data) throws IOException {
        response.setHeader("Content-type", "text/html;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        String ipAddress = IpUtil.getIpAddr(request);
        log.info("[device_log]So From IP：" + ipAddress);
        if (OtherUtils.isEmpty(data)) {
            response.getOutputStream().write(UserCode.get(-1, "data can be null", 0, null).getBytes());
            return;
        }
        byte[] gzipBytes = GZIPUtil.compress(data);
        byte[] result = TTEncrypt.getEn(gzipBytes);
        response.getOutputStream().write(result);
    }

    @RequestMapping(value = "/api/device_log/en", method = RequestMethod.POST)
    @ResponseBody
    public void postDeviceLogEn(@RequestBody String data) throws IOException {
        response.setHeader("Content-type", "text/html;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        String ipAddress = IpUtil.getIpAddr(request);
        log.info("[device_log]En From IP：" + ipAddress);
        if (OtherUtils.isEmpty(data)) {
            response.getOutputStream().write(UserCode.get(-1, "data can be null", 0, null).getBytes());
            return;
        }
        byte[] gzipBytes = GZIPUtil.compress(data);
        byte[] result = TT2Encrypt.getEn(gzipBytes);
        response.getOutputStream().write(result);
    }

    @RequestMapping(value = "/api/device_log/en-no-gz", method = RequestMethod.POST)
    @ResponseBody
    public void postDeviceLogEnNoGz(@RequestBody String data) throws IOException {
        response.setHeader("Content-type", "text/html;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        String ipAddress = IpUtil.getIpAddr(request);
        log.info("[device_log]En From IP：" + ipAddress);
        if (OtherUtils.isEmpty(data)) {
            response.getOutputStream().write(UserCode.get(-1, "data can be null", 0, null).getBytes());
            return;
        }
        //byte[] gzipBytes = GZIPUtil.compress(data);
        byte[] result = TT2Encrypt.getEn(data.getBytes());
        response.getOutputStream().write(result);
    }

    @RequestMapping(value = "/api/device_log/de", method = RequestMethod.POST)
    @ResponseBody
    public void postDeviceLogDe(@RequestBody byte[] bdata) throws IOException {
        response.setHeader("Content-type", "text/html;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        String ipAddress = IpUtil.getIpAddr(request);
        log.info("[device_log]De From IP：" + ipAddress);
        if (OtherUtils.isEmpty(bdata)) {
            response.getOutputStream().write(UserCode.get(-1, "data can be null", 0, null).getBytes());
            return;
        }
        String result = TT2Decrypt.decryptEn(bdata);
        response.getOutputStream().write(result.getBytes());
    }

    @RequestMapping(value = "/api/device_log/de-no-gz", method = RequestMethod.POST)
    @ResponseBody
    public void postDeviceLogDeNoGz(@RequestBody byte[] bdata) throws IOException {
        response.setHeader("Content-type", "text/html;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        String ipAddress = IpUtil.getIpAddr(request);
        log.info("[device_log]De From IP：" + ipAddress);
        if (OtherUtils.isEmpty(bdata)) {
            response.getOutputStream().write(UserCode.get(-1, "data can be null", 0, null).getBytes());
            return;
        }
        String result = TT2Decrypt.decryptEnNoGz(bdata);
        response.getOutputStream().write(result.getBytes());
    }


}
