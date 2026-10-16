package cn.boheng.frame.module.flow.controller.admin.steptype.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 步骤类型元数据 - 可调用 Bean 白名单 Response VO。
 * 供前端把 typeConfig 里的 beanName 从手输 JSON 升级成下拉选择。
 */
@Schema(description = "管理后台 - 步骤类型 Bean 白名单 Response VO")
@Data
public class FlowStepTypeBeansRespVO {

    @Schema(description = "bean.invoke 可调用动作 Bean 名列表")
    private List<String> beanActions;

    @Schema(description = "route 可用路由决策器 Bean 名列表")
    private List<String> routers;

    @Schema(description = "validate 可用校验动作 Bean 名列表")
    private List<String> validators;

}
