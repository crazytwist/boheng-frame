package cn.boheng.frame.module.wms.enums;

import cn.hutool.core.util.StrUtil;

import java.util.Set;

/**
 * 物料实例状态枚举（实例六态，对应 wms_material_instance.instance_status）
 *
 * <p>实例状态表达「实物处于什么物理状态」，由物理操作（上架 / 消耗 / 废弃）驱动，
 * 与样本状态八态（{@link SampleStatusEnum}）正交、互不联动。</p>
 *
 * <p>取值与库中字符串一致（枚举名即库值），DO / VO 仍用 String 承载，
 * 业务代码比较 / 赋值一律经本枚举，不允许再写裸字面量。</p>
 *
 * @author yinan
 */
public enum InstanceStatusEnum {

    /** 可用（在库可领用 / 可消耗） */
    AVAILABLE("可用"),

    /** 已预留（被某次操作预留，默认不可被其他操作抢占消耗） */
    RESERVED("已预留"),

    /** 使用中（已开封 / 部分消耗，仍有剩余） */
    IN_USE("使用中"),

    /** 已用完（剩余量归零，终态） */
    USED("已用完"),

    /** 已过期（终态） */
    EXPIRED("已过期"),

    /** 已废弃（人工废弃，终态） */
    DISCARDED("已废弃");

    /**
     * 状态中文名（用于日志与提示文案，不落库）
     */
    private final String label;

    InstanceStatusEnum(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 允许被消耗的状态集合：非终态即可消耗。
     * 终态（USED / EXPIRED / DISCARDED）不可再消耗。
     */
    public static final Set<InstanceStatusEnum> CONSUMABLE = Set.of(AVAILABLE, RESERVED, IN_USE);

    /**
     * 判断某个库中状态值是否允许被消耗
     *
     * @param status 实例状态库值（空视为不可消耗）
     * @return 是否可消耗
     */
    public static boolean isConsumable(String status) {
        InstanceStatusEnum value = of(status);
        return value != null && CONSUMABLE.contains(value);
    }

    /**
     * 库值 → 枚举（容错：未知 / 空值返回 null，由调用方决定语义）
     *
     * @param status 实例状态库值
     * @return 枚举；未知或为空时 null
     */
    public static InstanceStatusEnum of(String status) {
        if (StrUtil.isBlank(status)) {
            return null;
        }
        for (InstanceStatusEnum value : values()) {
            if (value.name().equals(status)) {
                return value;
            }
        }
        return null;
    }

}
