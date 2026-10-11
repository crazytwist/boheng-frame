package cn.boheng.frame.framework.tenant.core.log;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import cn.boheng.frame.framework.tenant.core.context.TenantContextHolder;

/**
 * Logback 转换器：输出当前租户编号，用于日志里打 {@code [tenant:xxx]} 字段。
 *
 * <p>配合 logback-spring.xml 里的 conversionRule 注册，pattern 中使用 {@code %tenantId}。
 * 非租户线程（无租户上下文）输出空串，日志行表现为 {@code [tenant:]}。</p>
 *
 * @author 伯珩
 */
public class TenantLogConverter extends ClassicConverter {

    @Override
    public String convert(ILoggingEvent event) {
        Long tenantId = TenantContextHolder.getTenantId();
        return tenantId == null ? "" : String.valueOf(tenantId);
    }

}
