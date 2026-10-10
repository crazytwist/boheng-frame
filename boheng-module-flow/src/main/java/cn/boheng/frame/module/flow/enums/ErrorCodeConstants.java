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

    /** 关联主键不存在 */
    ErrorCode FLOW_RELATION_NOT_EXISTS = new ErrorCode(1_005_002_000, "流程步骤不存在");

    /** 同一流程版本里节点编码重复 */
    ErrorCode FLOW_RELATION_NODE_DUPLICATE = new ErrorCode(1_005_002_001, "节点编码【{}】在这个流程版本里已经有了");

    /** 节点编码不符合规则 */
    ErrorCode FLOW_RELATION_NODE_INVALID = new ErrorCode(1_005_002_002, "节点编码须是小写字母开头，只含小写、数字和下划线");

    /** 停用的步骤不能再被新关联引用 */
    ErrorCode FLOW_RELATION_STEP_DISABLED = new ErrorCode(1_005_002_003, "步骤【{}】已停用，不能关联");

    /** 一条直线流程的步数上限 */
    ErrorCode FLOW_RELATION_LIMIT = new ErrorCode(1_005_002_004, "一条流程最多 {} 步");

}
