package cn.boheng.frame.module.device.service.callback;

/**
 * 回调接口给设备的答复。
 *
 * @param httpStatus HTTP 状态码：200 收下，403 令牌不对，404 命令不存在，409 命令已结束
 * @param message 说明
 * @param commandNo 命令号
 */
public record DeviceCallbackReceipt(int httpStatus, String message, String commandNo) {
}
