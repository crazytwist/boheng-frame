package cn.boheng.frame.module.device.enums;

import cn.hutool.core.util.StrUtil;

/**
 * 仿真 HTTP 的结果回收方式。
 *
 * 三种都走真实 HTTP，区别只在结果怎么回到平台。
 */
public enum SimulateReturnModeEnum {

    /**
     * 同步：仿真设备立刻返回配置的响应报文
     */
    SYNC,

    /**
     * 回调：仿真设备先回受理报文，等待指定毫秒后再把响应报文 POST 到平台的回调地址
     */
    CALLBACK,

    /**
     * 轮询：先返回受理报文，服务端按间隔反复查询，直到结果就绪
     */
    POLL;

    /**
     * 把配置里的回收方式转成枚举。空白或无法识别时返回 null，旧值 ASYNC 当作回调。
     */
    public static SimulateReturnModeEnum of(String raw) {
        if (StrUtil.isBlank(raw)) {
            return null;
        }
        String value = raw.trim().toUpperCase();
        if ("ASYNC".equals(value)) {
            return CALLBACK;
        }
        try {
            return valueOf(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

}
