package cn.boheng.frame.module.flow.engine.type.config;

import lombok.Data;

/**
 * notification.send 的类型配置。一期通知出口走 SPI
 * （日志 + WebSocket 兜底），真实渠道后续接。
 */
@Data
public class NotificationConfig {

    /**
     * 通知标题
     */
    private String title;

    /**
     * 通知内容。支持表达式占位
     */
    private String content;

    /**
     * 接收人，逗号分隔的用户编号。空则通知发起人
     */
    private String receiverIds;

}
