package cn.boheng.frame.module.wms.enums;

/**
 * 样本状态枚举（样本业务流转状态，与实例状态 instance_status 六态正交）
 *
 * <p>生命周期主线：REGISTERED → LOADED → IN_PROCESS → COMPLETED → ARCHIVED；
 * 分支：IN_PROCESS 可派生 SPLIT（子样本继续）、REJECTED（不合格）、LOST（丢失）。</p>
 *
 * @author yinan
 */
public enum SampleStatusEnum {

    /** 已登记（样本已建档，尚未进入流程/装载实物） */
    REGISTERED,

    /** 已装载（样本已实例化挂到 material_instance，进入流程） */
    LOADED,

    /** 处理中（样本正在某个流程步骤执行） */
    IN_PROCESS,

    /** 已拆分（母样本拆分，由子样本继续流转） */
    SPLIT,

    /** 已完成（样本走完流程） */
    COMPLETED,

    /** 已归档 */
    ARCHIVED,

    /** 不合格 */
    REJECTED,

    /** 丢失 */
    LOST;

}
