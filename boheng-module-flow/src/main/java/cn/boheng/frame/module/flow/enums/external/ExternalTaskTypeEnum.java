package cn.boheng.frame.module.flow.enums.external;

/**
 * @ClassName ExternalTaskTypeEnum
 * @Author mengke
 * @create 2025/12/3 14:27
 * @Description
 */
public enum ExternalTaskTypeEnum {

    ST_Incubation(1, "细胞培养"),

    ST_Incubation_Jurkat1(111, "Jurkat细胞培养1"),
    ST_Incubation_Jurkat2(112, "Jurkat细胞培养2"),
    ST_Incubation_Jurkat3(113, "Jurkat细胞培养3"),
    ST_Incubation_Jurkat4(114, "Jurkat细胞培养4"),
    ST_Incubation_Jurkat5(115, "Jurkat细胞培养5"),

    ST_Incubation_293T1(116, "293T细胞培养1"),
    ST_Incubation_293T2(117, "293T细胞培养2"),
    ST_Incubation_293T3(118, "293T细胞培养3"),
    ST_Incubation_293T4(119, "293T细胞培养4"),
    ST_Incubation_293T5(120, "293T细胞培养5"),

    ST_Pre_CellPassage(20, "293T细胞传代前处理"),
    ST_Post_CellPassage(21, "293T细胞传代后处理"),

    ST_Pre_CellPassage2(220, "293T细胞二次传代前处理"),
    ST_Post_CellPassage2(221, "293T细胞二次传代后处理"),
    ST_Pre_Cell_Transfection(3, "293T细胞前处理"),
    ST_Cell_Transfection(4, "293T细胞转染"),
    ST_VirusConcentration(6, "293T病毒收获前处理"),
    ST_VirusCollectionPost(24, "293T病毒收获后处理"),
    ST_VirusCollection(8, "293T病毒浓缩"),


    ST_CellPassage(2, "细胞传代"),
    ST_CellCount(17, "细胞计数"),




    ST_JurkatHarvest(10, "Jurkat细胞收获前处理"),
    ST_JurkatHarvestPost(23, "Jurkat细胞收获后处理"),
    ST_JurkatCellPassage(9, "Jurkat细胞传代前处理"),
    ST_JurkatCellPassagePost(22, "Jurkat细胞传代后处理"),
    ST_JurkatCellTransfection(25, "Jurkat细胞转染"),
    ST_PreheadMedium(30, "培养基预热"),
    ST_JurkatCellFilter(26, "Jurkat细胞过滤"),
    ST_JarkatOrganicIncubation(27, "Jurkat类器官培养"),
    ST_JarkatOrganicHarvestPrev(28, "Jurkat类器官收获前处理"),
    ST_JarkatOrganicHarvestPost(29, "Jurkat类器官收获后处理"),


    ST_JurkatCentrifugeTransfectionPrev(31,"Jurkat细胞离心转染前处理"), //
    ST_JurkatCentrifugeTransfectionPost(32,"Jurkat细胞离心转染后处理"),

    ;

//    ST_CellExchange(5, "细胞交换"),
//    ST_VirusCollection(6, "病毒收集"),
//    ST_VirusFiltration(7, "病毒过滤"),
//    ST_VirusInfectionConfigration(11, "病毒感染配置"),
//    ST_MixedHydrogel(12, "混合水凝胶"),
//    ST_MixedHydrogelInoculation(13, "混合水凝胶接种"),
//    ST_OrganoidIncubation(14, "类器官孵育"),
//    ST_OrganoidCollection(15, "类器官收集"),
//    ST_OrganoidHarvest(16, "类器官收获"),

    private final int code;
    private final String description;

    ExternalTaskTypeEnum(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据code获取枚举实例
     *
     * @param code 任务类型编码
     * @return 对应的枚举实例
     */
    public static ExternalTaskTypeEnum fromCode(int code) {
        for (ExternalTaskTypeEnum type : ExternalTaskTypeEnum.values()) {
            if (type.getCode() == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("No matching task type for code: " + code);
    }

    /**
     * 根据枚举名称获取code
     *
     * @param name 枚举名称
     * @return 对应的code
     */
    public static int getCodeByName(String name) {
        try {
            return ExternalTaskTypeEnum.valueOf(name).getCode();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("No matching task type for name: " + name);
        }
    }

    /**
     * 根据枚举名称获取code
     *
     * @param name 枚举名称
     * @return 对应的code
     */
    public static ExternalTaskTypeEnum fromName(String name) {
        try {
            return ExternalTaskTypeEnum.valueOf(name);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("No matching task type for name: " + name);
        }
    }



    /**
     * 【新增】根据description（描述）查找枚举实例
     *
     * @param description 任务描述（如"孵育"、"细胞传代"）
     * @return 对应的枚举实例
     */
    public static ExternalTaskTypeEnum fromDescription(String description) {
        // 空值校验，避免空指针
        if (description == null || description.trim().isEmpty()) {
            throw new IllegalArgumentException("Description cannot be null or empty");
        }
        // 遍历所有枚举值，匹配描述
        for (ExternalTaskTypeEnum type : ExternalTaskTypeEnum.values()) {
            // 用equals做精确匹配，若需要模糊匹配可改为contains
            if (description.equals(type.getDescription())) {
                return type;
            }
        }
        // 未找到匹配项时抛出异常，明确提示
        throw new IllegalArgumentException("No matching task type for description: " + description);
    }

    /**
     * 【新增】根据description（描述）获取对应的code
     *
     * @param description 任务描述
     * @return 对应的code
     */
    public static int getCodeByDescription(String description) {
        // 复用上面的fromDescription方法，减少重复代码
        return fromDescription(description).getCode();
    }



    // ========== 核心方法：输入任意细分枚举，返回顶层ST_Incubation ==========
    public static ExternalTaskTypeEnum getTopIncubationEnum(ExternalTaskTypeEnum subEnum) {
        // 1. 空值校验，避免NPE
        if (subEnum == null) {
            throw new IllegalArgumentException("输入的枚举不能为空！");
        }
        // 2. 如果输入的是顶层枚举，直接返回
        if (subEnum == ST_Incubation) {
            return ST_Incubation;
        }
        // 3. 所有细分枚举都返回顶层ST_Incubation
        return ST_Incubation;
    }

    // ========== 重载方法：支持通过code/名称获取顶层枚举（扩展） ==========
    // 通过枚举code获取顶层枚举
    public static ExternalTaskTypeEnum getTopIncubationEnumByCode(int code) {
        // 先校验code是否存在
        ExternalTaskTypeEnum targetEnum = null;
        for (ExternalTaskTypeEnum e : ExternalTaskTypeEnum.values()) {
            if (e.getCode() == code) {
                targetEnum = e;
                break;
            }
        }
        if (targetEnum == null) {
            throw new IllegalArgumentException("无效的枚举code：" + code);
        }
        // 调用核心方法返回顶层枚举
        return getTopIncubationEnum(targetEnum);
    }

    // 通过枚举名称获取顶层枚举（如输入"ST_Incubation_Jurkat1"）
    public static ExternalTaskTypeEnum getTopIncubationEnumByName(String enumName) {
        try {
            ExternalTaskTypeEnum targetEnum = ExternalTaskTypeEnum.valueOf(enumName);
            return getTopIncubationEnum(targetEnum);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("无效的枚举名称：" + enumName, e);
        }
    }

}
