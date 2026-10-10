package cn.boheng.frame.module.flow.engine.dispatch;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 扇出分发决策器注册表。与路由注册表同一套白名单思路：
 * 只有显式实现 FlowDispatchAction 的 Bean 能被 FANOUT 节点引用。
 */
@Component
public class FlowDispatchRegistry {

    /**
     * 全部实现。SPI 允许暂时一个实现都没有（空名单也是合法状态），
     * 因此用 ObjectProvider 注入——集合直注入在零实现时容器会按
     * java.util.List 找 bean 并启动失败
     */
    @Autowired
    private ObjectProvider<FlowDispatchAction> actionsProvider;

    /**
     * Bean 名 -> 实现
     */
    private final Map<String, FlowDispatchAction> actionMap = new HashMap<>();

    /**
     * 启动时按 Bean 名建索引，重名直接拒绝启动
     */
    @PostConstruct
    public void init() {
        for (FlowDispatchAction action : actionsProvider.orderedStream().toList()) {
            String name = action.beanName();
            FlowDispatchAction previous = actionMap.put(name, action);
            if (previous != null) {
                throw new IllegalStateException("扇出决策器重名: " + name);
            }
        }
    }

    /**
     * Bean 是否在白名单
     */
    public boolean isRegistered(String beanName) {
        return beanName != null && actionMap.containsKey(beanName);
    }

    /**
     * 取决策器。不在名单里返回 null，由调用方报错
     */
    public FlowDispatchAction get(String beanName) {
        return beanName == null ? null : actionMap.get(beanName);
    }

}
