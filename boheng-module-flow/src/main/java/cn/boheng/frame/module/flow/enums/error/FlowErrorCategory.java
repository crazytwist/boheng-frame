package cn.boheng.frame.module.flow.enums.error;

/** 错误分类（doc/07 §3.2） */
public enum FlowErrorCategory {
    VALIDATION,
    BUSINESS_RULE,
    RESOURCE_UNAVAILABLE,
    DEPENDENCY,
    COMMUNICATION,
    DEVICE_REJECTED,
    DEVICE_EXECUTION,
    DEVICE_SAFETY,
    INTERNAL_BUG,
    UNKNOWN
}
