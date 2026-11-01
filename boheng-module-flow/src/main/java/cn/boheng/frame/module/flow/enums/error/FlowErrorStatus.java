package cn.boheng.frame.module.flow.enums.error;

public enum FlowErrorStatus {
    OPEN,
    WAITING_RETRY,
    WAITING_EXTERNAL_CONFIRMATION,
    WAITING_MANUAL,
    RESOLVED,
    IGNORED
}
