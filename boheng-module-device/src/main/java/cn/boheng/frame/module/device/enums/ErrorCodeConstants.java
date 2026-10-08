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

    // ========== 设备动作（配置）1-004-002-000 ==========
    ErrorCode DEVICE_ACTION_NOT_EXISTS = new ErrorCode(1_004_002_000, "设备动作不存在");
    ErrorCode DEVICE_ACTION_CODE_DUPLICATE = new ErrorCode(1_004_002_001, "设备类型【{}】下已存在动作编码为【{}】的设备动作");
    ErrorCode DEVICE_ACTION_PATH_MISSING = new ErrorCode(1_004_002_002, "动作【{}】没有接口路径");

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

}
