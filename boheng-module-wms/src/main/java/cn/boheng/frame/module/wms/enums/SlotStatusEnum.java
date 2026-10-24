package cn.boheng.frame.module.wms.enums;

import cn.hutool.core.util.StrUtil;

/**
 * 槽位使用状态枚举（对应 wms_slot_info.slot_status）
 *
 * <p>占用 / 空闲由物料域以 {@code wms_material_instance.root_slot_id} 为真相源自动重算；
 * LOCKED / CHECKING 属于调度 / 盘点域的保留语义，物料域重算时会绕开不覆盖。</p>
 *
 * <p>取值与库中字符串一致（枚举名即库值），DO / VO 仍用 String 承载，
 * 业务代码比较 / 赋值一律经本枚举，不允许再写裸字面量。</p>
 *
 * @author yinan
 */
public enum SlotStatusEnum {

    /** 空闲（无实例落位） */
    FREE("空闲"),

    /** 占用（有实例落位，occupied_qty &gt; 0） */
    OCCUPIED("占用"),

    /** 锁定（调度域锁定，暂不可落位；物料域不自动写入 / 释放） */
    LOCKED("锁定"),

    /** 盘点中（盘点域锁定；物料域不自动写入 / 释放） */
    CHECKING("盘点中");

    /**
     * 状态中文名（用于日志与提示文案，不落库）
     */
    private final String label;

    SlotStatusEnum(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 库值 → 枚举（容错：未知 / 空值返回 null，由调用方决定语义）
     *
     * @param status 槽位使用状态库值
     * @return 枚举；未知或为空时 null
     */
    public static SlotStatusEnum of(String status) {
        if (StrUtil.isBlank(status)) {
            return null;
        }
        for (SlotStatusEnum value : values()) {
            if (value.name().equals(status)) {
                return value;
            }
        }
        return null;
    }

}
