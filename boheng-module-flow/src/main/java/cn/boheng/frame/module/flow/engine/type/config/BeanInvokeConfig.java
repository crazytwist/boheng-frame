package cn.boheng.frame.module.flow.engine.type.config;

import lombok.Data;

/**
 * bean.invoke 的类型配置。Bean 必须实现 FlowBeanAction 且显式注册，
 * 白名单外的 Bean 名在发布期就拒绝——流程里的 Bean 名不能变成任意代码执行入口。
 */
@Data
public class BeanInvokeConfig {

    /**
     * 注册进白名单的 Bean 名
     */
    private String beanName;

    /**
     * 调用参数 JSON。已解析好的入参会合并进来传给 Bean
     */
    private String argsJson;

}
