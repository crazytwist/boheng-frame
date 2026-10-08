package cn.boheng.frame.module.wms.enums;

import cn.boheng.frame.framework.common.exception.ErrorCode;

/**
 * WMS 错误码枚举类
 *
 * wms 模块，使用 1-003-000-000 段
 *
 * @author yinan
 */
public interface ErrorCodeConstants {

    // ========== 区域树 1-003-000-000 ==========
    ErrorCode ZONE_NOT_EXISTS = new ErrorCode(1_003_000_000, "区域不存在");
    ErrorCode ZONE_CODE_DUPLICATE = new ErrorCode(1_003_000_001, "已经存在区域编码为【{}】的区域");
    ErrorCode ZONE_HAS_CHILDREN = new ErrorCode(1_003_000_002, "区域【{}】下仍有 {} 个子区域，不允许删除");
    ErrorCode ZONE_HAS_SLOTS = new ErrorCode(1_003_000_003, "区域【{}】下仍有 {} 个槽位，不允许删除");

    // ========== 槽位 1-003-001-000 ==========
    ErrorCode SLOT_NOT_EXISTS = new ErrorCode(1_003_001_000, "槽位不存在");
    ErrorCode SLOT_CODE_DUPLICATE = new ErrorCode(1_003_001_001, "已经存在槽位编码为【{}】的槽位");
    ErrorCode SLOT_GENERATE_ZONE_NOT_RACK = new ErrorCode(1_003_001_002, "区域【{}】不是料架类型，无法按布局生成槽位");
    ErrorCode SLOT_GENERATE_NO_LAYOUT = new ErrorCode(1_003_001_003, "区域【{}】未配置货架布局尺寸，无法生成槽位");
    ErrorCode SLOT_DISABLED = new ErrorCode(1_003_001_004, "槽位【{}】已停用，不允许上架");
    ErrorCode SLOT_ALREADY_OCCUPIED = new ErrorCode(1_003_001_005, "槽位【{}】已被占用，不允许重复上架");

    // ========== 容器类型 1-003-002-000 ==========
    ErrorCode CONTAINER_TYPE_NOT_EXISTS = new ErrorCode(1_003_002_000, "容器类型不存在");
    ErrorCode CONTAINER_TYPE_CODE_DUPLICATE = new ErrorCode(1_003_002_001, "已经存在容器类型编码为【{}】的容器类型");

    // ========== 内容物定义 1-003-003-000 ==========
    ErrorCode CONTENT_DEF_NOT_EXISTS = new ErrorCode(1_003_003_000, "内容物定义不存在");
    ErrorCode CONTENT_DEF_CODE_DUPLICATE = new ErrorCode(1_003_003_001, "已经存在内容物编码为【{}】的内容物定义");

    // ========== 物料实例 1-003-004-000 ==========
    ErrorCode MATERIAL_INSTANCE_NOT_EXISTS = new ErrorCode(1_003_004_000, "物料实例不存在");
    ErrorCode MATERIAL_INSTANCE_CODE_DUPLICATE = new ErrorCode(1_003_004_001, "已经存在实例编码为【{}】的物料实例");
    ErrorCode MATERIAL_INSTANCE_PARENT_NOT_EXISTS = new ErrorCode(1_003_004_002, "父实例【{}】不存在");
    ErrorCode MATERIAL_INSTANCE_HAS_CHILDREN = new ErrorCode(1_003_004_003, "物料实例【{}】仍有子实例，不允许删除");
    ErrorCode MATERIAL_INSTANCE_BATCH_EMPTY = new ErrorCode(1_003_004_004, "批量创建的物料实例列表不能为空");
    ErrorCode MATERIAL_INSTANCE_BATCH_SIZE_LIMIT = new ErrorCode(1_003_004_005, "批量创建的数量不能超过 {} 条");
    ErrorCode MATERIAL_INSTANCE_SLOT_ONLY_TOP = new ErrorCode(1_003_004_006, "仅顶层实例允许落位槽位，子实例不允许落位");
    ErrorCode MATERIAL_INSTANCE_SLOT_ONLY_VIA_OPERATION = new ErrorCode(1_003_004_007,
            "落位请使用上架 / 下架 / 转移接口，更新接口不接受槽位字段（否则会绕过物料流水）");

    // ========== 物料实例 · 上下架 / 转移 / 消耗 1-003-005-000 ==========
    ErrorCode INSTANCE_NOT_PLACED = new ErrorCode(1_003_005_000, "实例【{}】当前未落位，无法执行该操作");
    ErrorCode INSTANCE_ALREADY_PLACED = new ErrorCode(1_003_005_001, "实例【{}】已落位在槽位【{}】，请使用转移");
    ErrorCode INSTANCE_TRANSFER_SAME_SLOT = new ErrorCode(1_003_005_002, "目标槽位与当前槽位相同，无需转移");
    ErrorCode INSTANCE_CONSUME_EXCEED = new ErrorCode(1_003_005_004, "实例【{}】可消耗量不足：本次需要 {}，当前剩余 {}");
    ErrorCode INSTANCE_CONSUME_NOT_ENOUGH_CHILD = new ErrorCode(1_003_005_005, "容器【{}】可用子实例不足：需要 {} 个，实际可用 {} 个");
    ErrorCode INSTANCE_CONSUME_NO_CHILD = new ErrorCode(1_003_005_006, "容器【{}】没有子实例，无法按容器消耗");
    ErrorCode INSTANCE_CONSUME_STATUS_ILLEGAL = new ErrorCode(1_003_005_007, "实例【{}】当前状态为【{}】，不允许消耗");
    ErrorCode MOVEMENT_IDEMPOTENT_DUPLICATE = new ErrorCode(1_003_005_008, "重复的操作请求（幂等键【{}】已处理过）");

}
