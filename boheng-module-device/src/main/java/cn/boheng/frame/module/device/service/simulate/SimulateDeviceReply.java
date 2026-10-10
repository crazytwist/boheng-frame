package cn.boheng.frame.module.device.service.simulate;

/**
 * 仿真设备一次 HTTP 应答。
 *
 * @param httpStatus HTTP 状态码
 * @param phase 阶段：ACCEPTED 已受理，PENDING 未完成，DONE 已有最终报文，FAILED 拒绝
 * @param jobId 轮询作业号。同步和回调为空
 * @param body 响应正文
 * @param contentType 响应类型
 */
public record SimulateDeviceReply(int httpStatus, String phase, String jobId, String body, String contentType) {

    /**
     * 受理，调用方接着轮询
     */
    public static final String ACCEPTED = "ACCEPTED";

    /**
     * 结果还没准备好
     */
    public static final String PENDING = "PENDING";

    /**
     * 最终报文已经返回
     */
    public static final String DONE = "DONE";

    /**
     * 仿真设备拒绝了这次调用
     */
    public static final String FAILED = "FAILED";

}
