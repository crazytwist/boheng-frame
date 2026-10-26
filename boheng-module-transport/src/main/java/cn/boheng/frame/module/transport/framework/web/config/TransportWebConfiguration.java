package cn.boheng.frame.module.transport.framework.web.config;

import cn.boheng.frame.framework.swagger.config.BohengSwaggerAutoConfiguration;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Transport 模块 Web 配置。
 *
 * <p>通过独立分组限制 Swagger 只扫描 Transport 模块接口，避免把内部实现
 * 和其他领域接口混入本模块文档。</p>
 */
@Configuration(proxyBeanMethods = false)
public class TransportWebConfiguration {

    /** 注册 Transport 模块的 Swagger API 分组。 */
    @Bean
    public GroupedOpenApi transportGroupedOpenApi() {
        return BohengSwaggerAutoConfiguration.buildGroupedOpenApi("transport");
    }
}
