package cn.boheng.frame.module.transport.enums;

import cn.boheng.frame.framework.common.exception.ErrorCode;

/**
 * Transport 运输域错误码。
 *
 * <p>Transport 使用 1-006-000-000 段，避免与 Flow 的 1-005 段冲突。</p>
 */
public interface ErrorCodeConstants {

    /** 路线不存在或未启用。 */
    ErrorCode TRANSPORT_ROUTE_NOT_FOUND =
            new ErrorCode(1_006_000_000, "Transport 路线【{}】不存在或未启用");
}
