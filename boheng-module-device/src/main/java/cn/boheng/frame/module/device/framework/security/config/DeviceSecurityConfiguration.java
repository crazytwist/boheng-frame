package cn.boheng.frame.module.device.framework.security.config;

import cn.boheng.frame.framework.security.config.AuthorizeRequestsCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;

/**
 * 设备模块的访问规则。仿真设备的 HTTP 和设备回调都由设备直接调用，不走登录令牌。
 */
@Configuration(proxyBeanMethods = false, value = "deviceSecurityConfiguration")
public class DeviceSecurityConfiguration {

    /**
     * 放行仿真设备的下发、轮询地址和设备回调地址
     */
    @Bean("deviceAuthorizeRequestsCustomizer")
    public AuthorizeRequestsCustomizer authorizeRequestsCustomizer() {
        return new AuthorizeRequestsCustomizer() {

            /**
             * 仿真 HTTP 和设备回调免登录，回调靠命令令牌校验。配置维护接口仍要登录。
             */
            @Override
            public void customize(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry registry) {
                registry.requestMatchers(buildAdminApi("/device/simulate/http/**")).permitAll();
                registry.requestMatchers(buildAdminApi("/device/callback/**")).permitAll();
                registry.requestMatchers(buildAdminApi("/device/callback")).permitAll();
            }

        };
    }

}
