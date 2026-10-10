package cn.boheng.frame.module.flow.engine.dispatch;

import cn.boheng.frame.module.flow.engine.type.config.DispatchPlanEntry;

import java.util.List;
import java.util.Map;

/**
 * 扇出分发决策器。业务在这里算出「怎么分组」，只返回拆分计划，
 * 不启动子执行、不写库——计划先落库，再由引擎逐条创建子执行。
 * 这条边界保证：子执行带父子关系挂在图上（不是孤儿）、
 * Bean 中途崩溃时恢复逻辑能按落库的计划补建。
 */
public interface FlowDispatchAction {

    /**
     * 命名空间说明：env 里有 input.xxx / node.xxx.yyy / sys.xxx
     *
     * @return 拆分计划，每组一条。空列表表示无组可拆，节点直接成功
     */
    List<DispatchPlanEntry> dispatch(Map<String, Object> env);

    /**
     * Bean 名。默认取类名首字母小写，与 Spring 注入名对齐
     */
    default String beanName() {
        String simple = getClass().getSimpleName();
        return Character.toLowerCase(simple.charAt(0)) + simple.substring(1);
    }

}
