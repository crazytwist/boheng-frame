package cn.boheng.frame.module.device.service.result;

/**
 * 一次设备应答的判定结论。
 *
 * <p>只描述「发生了什么」，不决定「接下来怎么办」：是否重试、重试几次属于流程域。</p>
 *
 * @param success 这次调用是否成功
 * @param errorType 失败分类，见 DeviceEnums.ErrorTypeEnum；成功时为 null
 * @param retryable 设备声明的可重试性；null 表示设备未声明，由流程按默认处理
 * @param message 失败说明，成功时为 null
 * @author yinan
 */
public record ResultVerdict(boolean success, String errorType, Boolean retryable, String message) {

    /**
     * 成功
     */
    public static ResultVerdict ok() {
        return new ResultVerdict(true, null, null, null);
    }

    /**
     * 成功，但设备顺带声明了可重试性（少见，一般用于部分成功）
     */
    public static ResultVerdict ok(Boolean retryable) {
        return new ResultVerdict(true, null, retryable, null);
    }

    /**
     * 失败
     *
     * @param errorType 失败分类
     * @param retryable 设备声明的可重试性，null 表示未声明
     * @param message 失败说明
     */
    public static ResultVerdict failed(String errorType, Boolean retryable, String message) {
        return new ResultVerdict(false, errorType, retryable, message);
    }

}
