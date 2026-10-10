package cn.boheng.frame.module.device.service.callback;

import cn.boheng.frame.framework.web.config.WebProperties;
import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 平台自己的访问地址。仿真设备在本进程里，用本机地址；真设备回调要用它能访问到的地址。
 */
@Component
public class DevicePlatformUrl {

    /**
     * 真设备回调平台时用的根地址，如 http://10.0.0.5:48080。没配时退回本机地址，只适合仿真和本机联调。
     */
    public static final String CALLBACK_BASE_PROPERTY = "boheng.device.callback-base-url";

    /**
     * 运行环境。端口在请求时再读，避免启动阶段还没有 local.server.port
     */
    @Resource
    private Environment environment;

    /**
     * 管理端接口前缀
     */
    @Resource
    private WebProperties webProperties;

    /**
     * 本机管理端根地址，带上下文路径和 /admin-api 前缀
     */
    public String localAdminBase() {
        return adminBase("http://127.0.0.1:" + port());
    }

    /**
     * 设备回调平台用的管理端根地址
     */
    public String callbackAdminBase() {
        String root = StrUtil.trimToNull(environment.getProperty(CALLBACK_BASE_PROPERTY));
        return root == null ? localAdminBase() : adminBase(root);
    }

    /**
     * 路径段编码，避免编码里的特殊字符把地址拆开
     */
    public static String encode(String raw) {
        return URLEncoder.encode(raw, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /**
     * 在根地址后面接上下文路径和管理端前缀
     */
    private String adminBase(String root) {
        String host = StrUtil.removeSuffix(root.trim(), "/");
        String context = StrUtil.blankToDefault(environment.getProperty("server.servlet.context-path"), "");
        if ("/".equals(context)) {
            context = "";
        }
        return host + context + webProperties.getAdminApi().getPrefix();
    }

    /**
     * 当前 HTTP 端口。优先用实际监听端口。
     */
    private int port() {
        String local = environment.getProperty("local.server.port");
        if (StrUtil.isNotBlank(local)) {
            return Integer.parseInt(local);
        }
        return Integer.parseInt(environment.getProperty("server.port", "48080"));
    }

}
