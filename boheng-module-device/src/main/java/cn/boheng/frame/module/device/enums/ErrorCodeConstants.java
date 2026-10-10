package cn.boheng.frame.module.device.enums;

import cn.boheng.frame.framework.common.exception.ErrorCode;

/**
 * 设备域 错误码枚举类
 *
 * device 模块，使用 1-004-000-000 段
 * （infra 1-001 / system 1-002 / wms 1-003 已占用，设备域预留 1-004）
 *
 * @author yinan
 */
public interface ErrorCodeConstants {

    // ========== 设备台账 1-004-000-000 ==========
    ErrorCode DEVICE_NOT_EXISTS = new ErrorCode(1_004_000_000, "设备不存在");
    ErrorCode DEVICE_CODE_DUPLICATE = new ErrorCode(1_004_000_001, "已经存在设备编码为【{}】的设备");
    ErrorCode DEVICE_BUSY = new ErrorCode(1_004_000_002, "设备【{}】当前被占用，无法执行该操作");
    ErrorCode DEVICE_LOCKED = new ErrorCode(1_004_000_003, "设备【{}】已被锁定（类型【{}】，原因【{}】），无法执行该操作");
    ErrorCode DEVICE_OFFLINE = new ErrorCode(1_004_000_004, "设备【{}】当前离线，无法执行该操作");
    ErrorCode DEVICE_ENDPOINT_MISSING = new ErrorCode(1_004_000_005, "设备【{}】没有可访问的 HTTP 地址");
    ErrorCode DEVICE_HTTP_FAILED = new ErrorCode(1_004_000_006, "调用设备【{}】失败：{}");
    ErrorCode DEVICE_LOGIN_FAILED = new ErrorCode(1_004_000_007, "设备【{}】登录失败：{}");
    /** 设备维护中或故障，不能下发命令 */
    ErrorCode DEVICE_UNAVAILABLE = new ErrorCode(1_004_000_008, "设备【{}】当前处于{}状态，无法执行该操作");
    /** 设备还有未结束的命令，不能删除 */
    ErrorCode DEVICE_DELETE_RUNNING = new ErrorCode(1_004_000_009, "设备【{}】存在执行中的命令，请等待命令结束后再删除");

    // ========== 设备动作（配置）1-004-002-000 ==========
    ErrorCode DEVICE_ACTION_NOT_EXISTS = new ErrorCode(1_004_002_000, "设备动作不存在");
    ErrorCode DEVICE_ACTION_CODE_DUPLICATE = new ErrorCode(1_004_002_001, "设备类型【{}】下已存在动作编码为【{}】的设备动作");
    ErrorCode DEVICE_ACTION_PATH_MISSING = new ErrorCode(1_004_002_002, "设备动作【{}】未配置请求路径");
    /** 动作已停用 */
    ErrorCode DEVICE_ACTION_DISABLED = new ErrorCode(1_004_002_003, "设备动作【{}】已停用");

    // ========== 设备属性（配置）1-004-003-000 ==========
    ErrorCode DEVICE_PROPERTY_NOT_EXISTS = new ErrorCode(1_004_003_000, "设备属性不存在");
    ErrorCode DEVICE_PROPERTY_CODE_DUPLICATE = new ErrorCode(1_004_003_001, "设备类型【{}】下已存在属性编码为【{}】的设备属性");

    // ========== 参数集（配置）1-004-004-000 ==========
    ErrorCode DEVICE_PARAM_SET_NOT_EXISTS = new ErrorCode(1_004_004_000, "参数集不存在");
    ErrorCode DEVICE_PARAM_SET_CODE_DUPLICATE = new ErrorCode(1_004_004_001, "已经存在编码为【{}】的参数集");

    // ========== 设备命令 1-004-001-000 ==========
    ErrorCode DEVICE_COMMAND_NOT_EXISTS = new ErrorCode(1_004_001_000, "设备命令不存在");

    // ========== 解析规则 1-004-005-000 ==========
    ErrorCode DEVICE_CODEC_NOT_EXISTS = new ErrorCode(1_004_005_000, "解析规则不存在");
    ErrorCode DEVICE_CODEC_CODE_DUPLICATE = new ErrorCode(1_004_005_001, "已经存在编码为【{}】的解析规则");
    ErrorCode DEVICE_CODEC_PARSE_FAILED = new ErrorCode(1_004_005_002, "解析失败：{}");

    // ========== 测量 1-004-006-000 ==========
    ErrorCode DEVICE_MEASUREMENT_NOT_EXISTS = new ErrorCode(1_004_006_000, "测量记录不存在");

    // ========== 仿真 HTTP 1-004-007-000 ==========
    /** 仿真配置主键不存在，或这台设备的这个动作没有启用的配置 */
    ErrorCode DEVICE_SIMULATE_PROFILE_NOT_EXISTS = new ErrorCode(1_004_007_000, "仿真配置不存在");
    /** 同一设备、同一动作只能有一条仿真配置 */
    ErrorCode DEVICE_SIMULATE_PROFILE_DUPLICATE = new ErrorCode(1_004_007_001, "设备【{}】的动作【{}】已有仿真配置");
    /** 回收方式、等待时间或轮询次数不在允许范围内 */
    ErrorCode DEVICE_SIMULATE_INVALID = new ErrorCode(1_004_007_002, "仿真配置不合法：{}");
    /** 轮询次数用尽仍未拿到仿真设备的最终报文 */
    ErrorCode DEVICE_SIMULATE_POLL_TIMEOUT = new ErrorCode(1_004_007_003, "轮询仿真设备【{}】超时，已轮询 {} 次");

    // ========== 设备回调 1-004-008-000 ==========
    /** 等回调的时间到了，设备还没有回调 */
    ErrorCode DEVICE_CALLBACK_TIMEOUT = new ErrorCode(1_004_008_000, "设备【{}】在 {} 秒内未回调执行结果");
    /** 动作要等回调，但台账没开启回调 */
    ErrorCode DEVICE_CALLBACK_DISABLED = new ErrorCode(1_004_008_001, "设备【{}】未开启结果回调，无法以回调方式获取结果");
    /** 等待回调的线程被打断 */
    ErrorCode DEVICE_CALLBACK_INTERRUPTED = new ErrorCode(1_004_008_002, "等待设备【{}】回调时被中断");

}
