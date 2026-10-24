package cn.boheng.frame.module.flow.engine.type.config;

import lombok.Data;

/**
 * timer.wait 的类型配置：只等待一段固定时长。
 */
@Data
public class TimerWaitConfig {

    /**
     * 等待秒数，范围 1 秒到 24 小时
     */
    private Long waitSeconds;

}
