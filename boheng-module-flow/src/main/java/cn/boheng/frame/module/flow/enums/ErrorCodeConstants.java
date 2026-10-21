package cn.boheng.frame.module.flow.enums;

import cn.boheng.frame.framework.common.exception.ErrorCode;

/**
 * 实验流程错误码。使用 1-005-000-000 段。设备域占用 1-004。
 */
public interface ErrorCodeConstants {

    /** 流程版本主键不存在 */
    ErrorCode FLOW_DEFINITION_NOT_EXISTS = new ErrorCode(1_005_000_000, "流程不存在");

    /** 同一租户里流程编码已被占用 */
    ErrorCode FLOW_DEFINITION_KEY_DUPLICATE = new ErrorCode(1_005_000_001, "已经存在编码为【{}】的流程");

    /** 已发布或已停用的版本不能改节点 */
    ErrorCode FLOW_DEFINITION_LOCKED = new ErrorCode(1_005_000_002, "流程【{}】第 {} 版已{}，不能修改");

    /** 一个编码同时只保留一个草稿 */
    ErrorCode FLOW_DEFINITION_DRAFT_EXISTS = new ErrorCode(1_005_000_003, "流程【{}】已有草稿第 {} 版，请先编辑或删除它");

    /** 发布或保存时图不合法，参数是拼接后的原因 */
    ErrorCode FLOW_DEFINITION_INVALID = new ErrorCode(1_005_000_004, "流程还不能保存：{}");

    /** 已发布和已停用的版本留着当历史 */
    ErrorCode FLOW_DEFINITION_DELETE_LOCKED = new ErrorCode(1_005_000_005, "只能删除草稿");

    /** 修订只能从已经定稿的版本复制 */
    ErrorCode FLOW_DEFINITION_REVISE_STATUS = new ErrorCode(1_005_000_006, "只有已发布或已停用的版本可以修订");

    /** 停用只作用于已发布版本 */
    ErrorCode FLOW_DEFINITION_RETIRE_STATUS = new ErrorCode(1_005_000_007, "只有已发布的版本可以停用");

    /** 步骤主键不存在 */
    ErrorCode FLOW_STEP_NOT_EXISTS = new ErrorCode(1_005_001_000, "步骤不存在");

    /** 同一租户里步骤编码已被占用 */
    ErrorCode FLOW_STEP_KEY_DUPLICATE = new ErrorCode(1_005_001_001, "已经存在编码为【{}】的步骤");

    /** 已发布或已停用的流程还在引用，步骤内容不能变 */
    ErrorCode FLOW_STEP_LOCKED = new ErrorCode(1_005_001_002, "步骤【{}】已被已发布或已停用的流程引用，不能修改");

    /** 还有关联时不能删步骤 */
    ErrorCode FLOW_STEP_DELETE_USED = new ErrorCode(1_005_001_003, "步骤【{}】还被流程引用，请先在流程步骤里解除关联");

    /** 控制类类型不能作为能力目录条目，只能在流程节点上直接配置 */
    ErrorCode FLOW_STEP_CONTROL_TYPE = new ErrorCode(1_005_001_004, "类型【{}】是编排控制类型，只能在流程节点上配置，不能保存为可复用步骤");

    /** 关联主键不存在 */
    ErrorCode FLOW_RELATION_NOT_EXISTS = new ErrorCode(1_005_002_000, "流程步骤不存在");

    /** 同一流程版本里节点编码重复 */
    ErrorCode FLOW_RELATION_NODE_DUPLICATE = new ErrorCode(1_005_002_001, "节点编码【{}】在这个流程版本里已经有了");

    /** 节点编码不符合规则 */
    ErrorCode FLOW_RELATION_NODE_INVALID = new ErrorCode(1_005_002_002, "节点编码须是小写字母开头，只含小写、数字和下划线");

    /** 停用的步骤不能再被新关联引用 */
    ErrorCode FLOW_RELATION_STEP_DISABLED = new ErrorCode(1_005_002_003, "步骤【{}】已停用，不能关联");

    /** 一条流程的步数上限 */
    ErrorCode FLOW_RELATION_LIMIT = new ErrorCode(1_005_002_004, "一条流程最多 {} 步");

    /** depends_on 引用了不存在的节点 */
    ErrorCode FLOW_NODE_DEPEND_NOT_EXISTS = new ErrorCode(1_005_002_005, "节点【{}】的前置【{}】不存在");

    /** depends_on 形成了环 */
    ErrorCode FLOW_NODE_DEPEND_CYCLE = new ErrorCode(1_005_002_006, "依赖关系有环，从【{}】绕回了自己");

    // ==================== 1-005-003 类型与图校验 ====================

    /** step_type 没有在注册表里 */
    ErrorCode FLOW_STEP_TYPE_NOT_REGISTERED = new ErrorCode(1_005_003_000, "步骤类型【{}】未注册");

    /** type_config 反序列化或校验不通过 */
    ErrorCode FLOW_STEP_TYPE_CONFIG_INVALID = new ErrorCode(1_005_003_001, "节点【{}】的类型配置不合法：{}");

    /** join_policy 与前置数量不一致 */
    ErrorCode FLOW_JOIN_POLICY_MISMATCH = new ErrorCode(1_005_003_002, "节点【{}】只有一个前置，不能用 ANY_SUCCESS");

    /** 参数映射引用的节点不是直接前驱 */
    ErrorCode FLOW_MAPPING_NOT_DEPENDENCY = new ErrorCode(1_005_003_003, "节点【{}】的参数【{}】只能取直接前置的输出，【{}】不是前置");

    /** loop 容器区间不合法 */
    ErrorCode FLOW_LOOP_RANGE_INVALID = new ErrorCode(1_005_003_004, "循环节点【{}】的区间不合法：{}");

    /** loop.maxIterations 超限 */
    ErrorCode FLOW_LOOP_ITERATIONS_INVALID = new ErrorCode(1_005_003_005, "循环节点【{}】的轮次上限须在 1 到 {} 之间");

    /** subflow 目标不是已发布版本 */
    ErrorCode FLOW_SUBFLOW_TARGET_NOT_PUBLISHED = new ErrorCode(1_005_003_006, "子流程节点【{}】引用的【{}】不是已发布版本");

    /** subflow 引用链成环 */
    ErrorCode FLOW_SUBFLOW_CYCLE = new ErrorCode(1_005_003_007, "子流程引用链有环：{}");

    /** subflow 嵌套过深 */
    ErrorCode FLOW_SUBFLOW_DEPTH = new ErrorCode(1_005_003_008, "子流程嵌套超过 {} 层：{}");

    /** route 分支出口配置不合法 */
    ErrorCode FLOW_ROUTE_BRANCH_INVALID = new ErrorCode(1_005_003_009, "路由节点【{}】的分支配置不合法：{}");

    /** route 缺少 default 出口 */
    ErrorCode FLOW_ROUTE_DEFAULT_MISSING = new ErrorCode(1_005_003_010, "路由节点【{}】必须配置 default 出口");

    /** route/loop/subflow/timer.wait 不应指向能力目录里的步骤 */
    ErrorCode FLOW_CONTROL_NODE_HAS_STEP = new ErrorCode(1_005_003_011, "控制类节点【{}】不能关联步骤表里的能力");

    /** FANOUT 分发 Bean 不在白名单 */
    ErrorCode FLOW_DISPATCH_BEAN_INVALID = new ErrorCode(1_005_003_012, "扇出节点【{}】的分发 Bean【{}】未注册");

    /** FANOUT 完成策略不合法 */
    ErrorCode FLOW_FANOUT_POLICY_INVALID = new ErrorCode(1_005_003_013, "扇出节点【{}】的完成策略只能是 ALL_SUCCESS 或 ANY_SUCCESS");

    /** 表达式编译失败 */
    ErrorCode FLOW_EXPRESSION_INVALID = new ErrorCode(1_005_003_014, "节点【{}】的表达式不合法：{}");

    // ==================== 1-005-004 运行层 ====================

    /** 运行不存在 */
    ErrorCode FLOW_EXECUTION_NOT_EXISTS = new ErrorCode(1_005_004_000, "运行不存在");

    /** 只有 DRAFT→PUBLISHED 的定义能启动 */
    ErrorCode FLOW_EXECUTION_DEFINITION_NOT_PUBLISHED = new ErrorCode(1_005_004_001, "流程【{}】第 {} 版不是已发布状态，不能启动");

    /** 定义在启动后被改动（指纹不一致） */
    ErrorCode FLOW_EXECUTION_CHECKSUM_DRIFT = new ErrorCode(1_005_004_002, "流程【{}】的定义已变化，请重新确认后再启动");

    /** 运行已在终态 */
    ErrorCode FLOW_EXECUTION_FINISHED = new ErrorCode(1_005_004_003, "运行【{}】已结束，不能再操作");

    /** 只有 RUNNING 能取消 */
    ErrorCode FLOW_EXECUTION_CANCEL_STATUS = new ErrorCode(1_005_004_004, "只有进行中的运行才能取消");

    /** 步骤执行行不存在 */
    ErrorCode FLOW_STEP_EXECUTION_NOT_EXISTS = new ErrorCode(1_005_004_005, "步骤执行不存在");

    /** 人工确认的节点不在等待人工 */
    ErrorCode FLOW_SIGNAL_NOT_WAITING_MANUAL = new ErrorCode(1_005_004_006, "节点【{}】不在等待人工确认");

    /** 白名单外的 Bean 调用 */
    ErrorCode FLOW_BEAN_NOT_REGISTERED = new ErrorCode(1_005_004_007, "Bean【{}】没有注册为流程可调用动作");

    /** 设备任务不存在或 taskId 无法匹配 */
    ErrorCode FLOW_DEVICE_TASK_NOT_EXISTS = new ErrorCode(1_005_004_008, "设备任务不存在");

    /** 设备任务状态不允许本次操作 */
    ErrorCode FLOW_DEVICE_TASK_STATUS_INVALID = new ErrorCode(1_005_004_009, "设备任务状态不允许本次操作");

    /** 外部状态码不合法 */
    ErrorCode FLOW_DEVICE_TASK_EXTERNAL_STATUS_INVALID = new ErrorCode(1_005_004_010, "任务状态码不合法");

    /** 任务完成信号未被接受（节点不在等待或已回报） */
    ErrorCode FLOW_DEVICE_TASK_SIGNAL_REJECTED = new ErrorCode(1_005_004_011, "任务状态更新失败，节点不在等待或已结束");

}
