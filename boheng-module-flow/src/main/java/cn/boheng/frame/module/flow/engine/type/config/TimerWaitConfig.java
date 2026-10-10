package cn.boheng.frame.module.flow.engine.type.config;

import lombok.Data;

/**
 * timer.wait 的类型配置。
 */
@Data
public class TimerWaitConfig {

    /**
     * 等待秒数。与 waitUntil 二选一
     */
    private Long waitSeconds;

    /**
     * 等到指定时刻，格式 yyyy-MM-dd HH:mm:ss。与 waitSeconds 二选一
     */
    private String waitUntil;

}
