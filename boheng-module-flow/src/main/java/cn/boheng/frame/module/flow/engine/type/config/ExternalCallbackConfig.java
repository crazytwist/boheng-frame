package cn.boheng.frame.module.flow.engine.type.config;

import lombok.Data;

/**
 * external.callback 的类型配置：暂停等待外部业务系统回报结果。
 *
 * <p>与 device.command 的分工在「对方认什么」：设备只认命令号，回调进设备域；
 * 认流程、能说清是哪一步的对方（LIMS / MES / 上游调度），直接回调节点。</p>
 */
@Data
public class ExternalCallbackConfig {

    /**
     * 对方系统名称。只用于日志与等待提示，不参与校验，可为空
     */
    private String counterparty;

}
