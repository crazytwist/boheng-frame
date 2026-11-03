package cn.boheng.frame.module.flow.service.error;

import cn.boheng.frame.module.flow.enums.error.FlowErrorCategory;
import cn.boheng.frame.module.flow.enums.error.FlowErrorCertainty;
import cn.boheng.frame.module.flow.enums.error.FlowErrorRetryMode;
import cn.boheng.frame.module.flow.enums.error.FlowErrorSeverity;
import cn.boheng.frame.module.flow.enums.error.FlowErrorSourceType;
import cn.hutool.core.util.StrUtil;

/**
 * 内部异常 → 统一错误语义（E-1）。设备侧映射属 E-4，此处只覆盖 Flow/WMS/系统。
 */
public final class FlowErrorClassifier {

    private FlowErrorClassifier() {
    }

    public record Classified(
            FlowErrorSourceType sourceType,
            String errorCode,
            FlowErrorCategory category,
            FlowErrorSeverity severity,
            FlowErrorCertainty certainty,
            FlowErrorRetryMode retryMode
    ) {
    }

    public static Classified classifyStepFailure(String rawMessage) {
        String msg = StrUtil.nullToEmpty(rawMessage);
        String lower = msg.toLowerCase();
        if (containsAny(lower, "校验", "参数", "不合法", "validation", "illegal argument")) {
            return new Classified(FlowErrorSourceType.FLOW_STEP, "FLOW_STEP_VALIDATION",
                    FlowErrorCategory.VALIDATION, FlowErrorSeverity.ERROR,
                    FlowErrorCertainty.CONFIRMED, FlowErrorRetryMode.NEVER);
        }
        // 依赖/超时优先于业务关键词（如 "WMS connection timeout" 不应归业务拒绝）
        if (containsAny(lower, "timeout", "超时", "连接", "unavailable", "暂时")) {
            return new Classified(FlowErrorSourceType.SYSTEM, "FLOW_DEPENDENCY_UNAVAILABLE",
                    FlowErrorCategory.DEPENDENCY, FlowErrorSeverity.WARNING,
                    FlowErrorCertainty.UNKNOWN, FlowErrorRetryMode.IDEMPOTENT);
        }
        if (containsAny(lower, "库存", "不足", "样本", "不允许", "wms", "物料")) {
            return new Classified(FlowErrorSourceType.WMS, "FLOW_WMS_BUSINESS",
                    FlowErrorCategory.BUSINESS_RULE, FlowErrorSeverity.ERROR,
                    FlowErrorCertainty.CONFIRMED, FlowErrorRetryMode.NEVER);
        }
        if (containsAny(lower, "nullpointer", "npe", "bug", "assertion")) {
            return new Classified(FlowErrorSourceType.FLOW_ENGINE, "FLOW_INTERNAL_BUG",
                    FlowErrorCategory.INTERNAL_BUG, FlowErrorSeverity.CRITICAL,
                    FlowErrorCertainty.CONFIRMED, FlowErrorRetryMode.NEVER);
        }
        return new Classified(FlowErrorSourceType.FLOW_STEP, "FLOW_STEP_FAILED",
                FlowErrorCategory.UNKNOWN, FlowErrorSeverity.ERROR,
                FlowErrorCertainty.CONFIRMED, FlowErrorRetryMode.NEVER);
    }

    private static boolean containsAny(String haystack, String... needles) {
        for (String n : needles) {
            if (haystack.contains(n.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}
