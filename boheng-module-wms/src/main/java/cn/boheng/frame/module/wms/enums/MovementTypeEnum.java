package cn.boheng.frame.module.wms.enums;

import cn.hutool.core.util.StrUtil;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 物料流水类型枚举
 *
 * @author yinan
 */
@Getter
@AllArgsConstructor
public enum MovementTypeEnum {

    CREATE("CREATE", "建账"),
    PUT_IN("PUT_IN", "上架"),
    TAKE_OUT("TAKE_OUT", "下架"),
    MOVE("MOVE", "转移"),
    CONSUME("CONSUME", "消耗"),
    STATUS_CHANGE("STATUS_CHANGE", "状态变更"),
    RESERVE("RESERVE", "预留"),
    RELEASE("RELEASE", "释放预留"),
    ADJUST("ADJUST", "冲正");

    /**
     * 类型编码
     */
    private final String type;

    /**
     * 类型名称
     */
    private final String name;

    public static String getNameByType(String type) {
        if (StrUtil.isBlank(type)) {
            return "";
        }
        for (MovementTypeEnum value : values()) {
            if (value.type.equals(type)) {
                return value.name;
            }
        }
        return type;
    }
}
