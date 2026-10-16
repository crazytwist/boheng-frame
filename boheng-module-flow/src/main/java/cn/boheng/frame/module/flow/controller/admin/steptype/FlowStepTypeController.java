package cn.boheng.frame.module.flow.controller.admin.steptype;

import cn.boheng.frame.framework.common.pojo.CommonResult;
import cn.boheng.frame.module.flow.controller.admin.steptype.vo.FlowStepTypeBeansRespVO;
import cn.boheng.frame.module.flow.engine.route.FlowRouteAction;
import cn.boheng.frame.module.flow.engine.type.builtin.FlowBeanAction;
import cn.boheng.frame.module.flow.engine.type.builtin.FlowValidateAction;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.boheng.frame.framework.common.pojo.CommonResult.success;

/**
 * 步骤类型元数据。给编排页提供「能力类型下可配置字段的取值来源」，
 * 让 typeConfig 里的 beanName 从手输 JSON 变成白名单下拉。
 */
@Tag(name = "管理后台 - 步骤类型元数据")
@RestController
@RequestMapping("/flow/step-type")
@Validated
public class FlowStepTypeController {

    /**
     * bean.invoke 可调用动作。SPI 允许零实现，用 ObjectProvider 注入
     */
    @Autowired
    private ObjectProvider<FlowBeanAction> beanActions;

    /**
     * route 可用决策器
     */
    @Autowired
    private ObjectProvider<FlowRouteAction> routeActions;

    /**
     * validate 可用校验动作
     */
    @Autowired
    private ObjectProvider<FlowValidateAction> validateActions;

    @GetMapping("/beans")
    @Operation(summary = "获得可调用 Bean 白名单")
    @PreAuthorize("@ss.hasPermission('flow:step:query')")
    public CommonResult<FlowStepTypeBeansRespVO> beans() {
        FlowStepTypeBeansRespVO resp = new FlowStepTypeBeansRespVO();
        resp.setBeanActions(beanActions.orderedStream().map(FlowBeanAction::beanName).toList());
        resp.setRouters(routeActions.orderedStream().map(FlowRouteAction::beanName).toList());
        resp.setValidators(validateActions.orderedStream().map(FlowValidateAction::beanName).toList());
        return success(resp);
    }

}
