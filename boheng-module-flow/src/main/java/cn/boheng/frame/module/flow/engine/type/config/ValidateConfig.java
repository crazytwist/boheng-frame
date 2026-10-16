package cn.boheng.frame.module.flow.engine.type.config;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * validate 校验门的类型配置。
 *
 * <p>决策与执行分离：执行器只调校验 Bean 算出「通过 / 不通过」，
 * 拿结果查 branches 找目标节点、激活与裁剪全部归调度器 BranchResolver（同 route）。</p>
 */
@Data
public class ValidateConfig {

    /**
     * 校验 Bean 名，须实现 FlowValidateAction 且注册进白名单
     */
    private String beanName;

    /**
     * 固定参数 JSON。解析后作为 args 传给校验 Bean
     */
    private String argsJson;

    /**
     * 出口映射：pass（通过）与 fail（不通过）各自的目标节点编码列表。
     * pass 必填；fail 必填——不通过也要有明确去向（终止 / 通知 / 补偿）。
     */
    private Map<String, List<String>> branches = new LinkedHashMap<>();

}
