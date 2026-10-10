package cn.boheng.frame.module.device.service.callback;

import java.time.LocalDateTime;

/**
 * 一次等回调的凭据：发给设备的回调地址和令牌，以及平台最多等多久。
 *
 * @param commandNo 命令号
 * @param token 回调令牌明文，只放进回调地址和请求头
 * @param tokenHash 令牌的 SHA-256 摘要，落库用
 * @param url 设备回调平台的完整地址
 * @param timeoutSec 最多等的秒数
 * @param timeoutAt 截止时刻
 */
public record DeviceCallbackTicket(String commandNo, String token, String tokenHash, String url,
                                   int timeoutSec, LocalDateTime timeoutAt) {
}
