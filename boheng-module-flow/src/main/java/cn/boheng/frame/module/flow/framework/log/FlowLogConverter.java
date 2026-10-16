package cn.boheng.frame.module.flow.framework.log;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * Logback 转换器：输出流程执行维度，用于日志里打 {@code [exe=xxx node=yyy]} 前缀。
 *
 * <p>配合 logback-spring.xml 里的 conversionRule 注册，pattern 中使用 {@code %flow}。
 * 非流程线程（无执行上下文）输出空串，日志行不出现该段，避免污染全系统日志。</p>
 *
 * @author yinan
 */
public class FlowLogConverter extends ClassicConverter {

    @Override
    public String convert(ILoggingEvent event) {
        String exe = FlowLogContextHolder.getExecutionNo();
        String node = FlowLogContextHolder.getNodeId();
        if (exe == null && node == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (exe != null) {
            sb.append("exe=").append(exe);
        }
        if (node != null) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append("node=").append(node);
        }
        // 自带前导空格 + 方括号，pattern 里无需再包一层
        return " [" + sb + "]";
    }

}
