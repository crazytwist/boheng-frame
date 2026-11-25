package cn.boheng.frame.framework.apilog.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 控制台请求日志的配置。
 *
 * <p>这里配的不是落库的访问日志（那个由 {@code boheng.access-log.enable} 控制），
 * 而是每次请求都会往控制台打三行的那种「开始请求 / Controller 方法路径 / 完成请求」。
 * 排查问题时有用，但前端轮询的接口每 3 秒刷一遍，把真正的业务日志全冲走了。</p>
 *
 * @author yinan
 */
@Data
@ConfigurationProperties(prefix = "boheng.access-log.request-log")
public class ApiRequestLogProperties {

    /**
     * 总开关。false 时任何接口都不打印控制台请求日志
     */
    private boolean enabled = true;

    /**
     * 不打印的 URL，Ant 风格（{@code /admin-api/flow/execution/get}、{@code /admin-api/xyz/**}）。
     *
     * <p>只影响控制台打印，不影响 {@code infra_api_access_log} 落库。</p>
     */
    private List<String> skipUrls = new ArrayList<>();

}
