package cn.boheng.frame.module.wms.api;

/**
 * 样本状态常量（契约层，供消费方 flow 引用，避免魔法字符串）
 *
 * <p>样本状态是「业务流转」八态，与实例状态（instance_status 六态）正交。
 * 实现侧枚举见 boheng-module-wms 的 SampleStatusEnum，取值与本常量一一对应。</p>
 *
 * @author yinan
 */
public final class SampleStatus {

    private SampleStatus() {
    }

    /** 已登记（样本已建档，尚未进入流程/装载实物） */
    public static final String REGISTERED = "REGISTERED";

    /** 已装载（样本已实例化挂到 material_instance，进入流程） */
    public static final String LOADED = "LOADED";

    /** 处理中（样本正在某个流程步骤执行） */
    public static final String IN_PROCESS = "IN_PROCESS";

    /** 已拆分（母样本拆分，由子样本继续流转） */
    public static final String SPLIT = "SPLIT";

    /** 已完成（样本走完流程） */
    public static final String COMPLETED = "COMPLETED";

    /** 已归档 */
    public static final String ARCHIVED = "ARCHIVED";

    /** 不合格 */
    public static final String REJECTED = "REJECTED";

    /** 丢失 */
    public static final String LOST = "LOST";

}
