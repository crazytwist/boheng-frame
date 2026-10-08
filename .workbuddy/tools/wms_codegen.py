#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Lab-WMS 模块代码生成器（boheng-boot-mini 底座）

输入：TABLES 元数据（由 .workbuddy/reference/lab-wms-ddl-v1.0-original.sql 对齐底座后而来）
输出：boheng-module-wms 下的 DAL / Service / Controller / VO 全套 Java 代码

生成的目录结构（以 zone 为例）：
  dal/dataobject/zone/ZoneDO.java
  dal/mysql/zone/ZoneMapper.java
  service/zone/ZoneService.java
  service/zone/ZoneServiceImpl.java
  controller/admin/zone/ZoneController.java
  controller/admin/zone/vo/zone/ZoneSaveReqVO.java
  controller/admin/zone/vo/zone/ZonePageReqVO.java
  controller/admin/zone/vo/zone/ZoneRespVO.java
"""
import os
import re
from string import Template

PROJECT = "/Volumes/External_1TB/Projects/boheng-frame"
MODULE = "{}/boheng-module-wms".format(PROJECT)
PKG = "cn.boheng.frame.module.wms"
SRC = "{}/src/main/java/{}".format(MODULE, PKG.replace(".", "/"))

TYPE_IMPORTS = {
    "BigDecimal": "java.math.BigDecimal",
    "LocalDate": "java.time.LocalDate",
    "LocalDateTime": "java.time.LocalDateTime",
}


def f(name, type_, comment, ex=None, size=None, required=False,
      save=True, resp=True, page=None):
    """构造一个字段元数据"""
    return {"name": name, "type": type_, "comment": comment, "ex": ex,
            "size": size, "required": required, "save": save, "resp": resp,
            "page": page}


TABLES = [
    # ------------------------------------------------------------------ 空间域
    {
        "table": "wms_zone_info", "prefix": "Zone", "domain": "zone",
        "biz": "wms:zone", "comment": "区域树", "tree": True, "list": True,
        "unique": [{"fields": ["zoneCode"], "label": "区域编码", "err": "ZONE_CODE_DUPLICATE"}],
        "fields": [
            f("zoneCode", "String", "区域编码", ex="ZONE_LAB_01", size=64, required=True, page="like"),
            f("parentZoneCode", "String", "父区域编码（为空表示根节点）", ex="ZONE_LAB", size=64, page="eq"),
            f("zoneName", "String", "区域名称", ex="一楼实验室", size=128, required=True, page="like"),
            f("zoneType", "String", "区域类型：LAB 实验室/ROOM 房间/FUNCTION 功能区/TEMP 温控区/SAFETY 安全区/RACK 料架/BENCH 台面/DEVICE 设备工位/CUSTOM 自定义",
              ex="RACK", size=32, required=True, page="eq"),
            f("zoneLevel", "Integer", "树深度（根为 1）", ex="1"),
            f("tempMin", "BigDecimal", "温区下限（℃）", ex="2.00"),
            f("tempMax", "BigDecimal", "温区上限（℃）", ex="8.00"),
            f("biosafetyLevel", "Integer", "生物安全等级（1-4）", ex="2"),
            f("extData", "String", "扩展数据（JSON，如 rack 模板参数与物理地址）",
              ex='JSON 对象，如 rackRows/rackCols/slotCapacity/address', size=1024),
            f("sortNo", "Integer", "显示顺序", ex="0", required=True),
            f("status", "Integer", "状态", ex="0", required=True, page="eq"),
            f("description", "String", "备注", ex="冷藏区", size=512),
        ],
    },
    {
        "table": "wms_slot_info", "prefix": "Slot", "domain": "slot",
        "biz": "wms:slot", "comment": "槽位", "list": True,
        "unique": [{"fields": ["slotCode"], "label": "槽位编码", "err": "SLOT_CODE_DUPLICATE"}],
        "fields": [
            f("slotCode", "String", "槽位编码", ex="SLOT_RACK_A01_R1C1", size=64, required=True, page="like"),
            f("zoneCode", "String", "挂载区域编码", ex="RACK_A01", size=64, required=True, page="eq"),
            f("slotType", "String", "槽位类型：STORAGE 存储/DEVICE 设备器位/BUFFER 暂存/WASTE 废弃",
              ex="STORAGE", size=32, required=True, page="eq"),
            f("status", "Integer", "可用状态", ex="0", required=True, page="eq"),
            f("slotStatus", "String", "使用状态：FREE 空闲/OCCUPIED 占用/LOCKED 锁定/CHECKING 盘点中",
              ex="FREE", size=16, required=True, page="eq", save=False),
            f("capacity", "Integer", "容量（标准位）", ex="1", required=True),
            f("uniqueBatch", "Boolean", "是否一位一批", ex="false", required=True),
            f("extData", "String", "扩展数据（JSON，坐标描述 row/col/layer）",
              ex='JSON 对象，如 rowNo/colNo/layerNo', size=512),
            f("deviceCode", "String", "所属设备编码", ex="TECAN_F200_01", size=64, page="eq"),
            f("devicePositionNo", "String", "设备侧位置标识", ex="STACK-3", size=64),
            f("occupiedQty", "Integer", "已占标准位数（=0 即 FREE）", ex="0", required=True, save=False),
            f("occupiedTime", "LocalDateTime", "转为占用态时间", ex="2026-09-25 10:00:00", save=False),
            f("version", "Integer", "乐观锁版本号", ex="0", save=False),
            f("description", "String", "备注", ex="样本位", size=512),
        ],
    },
    # ------------------------------------------------------------------ 实例域
    {
        "table": "wms_container_type", "prefix": "ContainerType", "domain": "container",
        "biz": "wms:container-type", "comment": "容器类型", "list": True,
        "unique": [{"fields": ["typeCode"], "label": "容器类型编码", "err": "CONTAINER_TYPE_CODE_DUPLICATE"}],
        "fields": [
            f("typeCode", "String", "容器类型编码", ex="PLATE_96_WELL", size=64, required=True, page="like"),
            f("typeName", "String", "容器类型名称", ex="96 孔板", size=128, required=True, page="like"),
            f("category", "String", "物理形态：PLATE 孔板/TUBE 试管/BOTTLE 瓶/RACK 托盘架/VIAL 小瓶/TIP 吸头/CHIP 芯片/FILTER 滤膜/BOX 盒/BAG 袋/OTHER 其他",
              ex="PLATE", size=32, required=True, page="eq"),
            f("hierarchyRole", "String", "层级角色：CARRIER 载体/CONTAINER 直接容器/WELL 孔位",
              ex="CARRIER", size=32, required=True, page="eq"),
            f("maxVolUl", "BigDecimal", "最大容积（μL）", ex="300.00"),
            f("wellCount", "Integer", "位数（仅 CARRIER）", ex="96"),
            f("wellRows", "Integer", "行数（仅 CARRIER）", ex="8"),
            f("wellCols", "Integer", "列数（仅 CARRIER）", ex="12"),
            f("positionNaming", "String", "位置命名规则：ROW_COL/SEQ/ROW_COL_LAYER/NONE", ex="ROW_COL", size=16, required=True),
            f("childTypeCode", "String", "承载的子单元类型编码（仅 CARRIER）", ex="WELL_STANDARD", size=64),
            f("specJson", "String", "其他规格参数（JSON）", ex='JSON 对象，如 color', size=1024),
            f("nestable", "Boolean", "是否允许被装入其他容器", ex="true", required=True),
            f("usageType", "String", "使用类型：REUSABLE 周转复用/DISPOSABLE 一次性", ex="REUSABLE", size=16, required=True, page="eq"),
            f("lifeCycles", "Integer", "复用次数上限（为空不限）", ex="100"),
            f("status", "Integer", "状态", ex="0", required=True, page="eq"),
            f("description", "String", "备注", ex="标准 96 孔板", size=512),
        ],
    },
    {
        "table": "wms_content_def", "prefix": "ContentDef", "domain": "content",
        "biz": "wms:content-def", "comment": "内容物定义", "list": True,
        "unique": [{"fields": ["contentCode"], "label": "内容物编码", "err": "CONTENT_DEF_CODE_DUPLICATE"}],
        "fields": [
            f("contentCode", "String", "内容物编码", ex="PH-BUFFER-7", size=64, required=True, page="like"),
            f("contentName", "String", "内容物名称", ex="pH7 标准缓冲液", size=128, required=True, page="like"),
            f("contentType", "String", "内容物类型：REAGENT 试剂/STANDARD 标准品/BUFFER 缓冲液/SAMPLE 样本/WASTE 废液/MEDIA 培养基/SOLVENT 溶剂/OTHER 其他",
              ex="BUFFER", size=32, required=True, page="eq"),
            f("unit", "String", "计量单位：UL/ML/MG/G/COUNT", ex="ML", size=16, required=True),
            f("supplier", "String", "供应商名称", ex="某生物科技", size=128),
            f("catalogNo", "String", "供应商货号", ex="CAT-0001", size=64),
            f("casNo", "String", "CAS 号", ex="9002-93-1", size=32, page="eq"),
            f("concentration", "String", "标准浓度描述", ex="pH7.0", size=64),
            f("storageCond", "String", "存储条件：RT 常温/C2_8 2-8℃/F20 -20℃/Ultra80 -80℃/FROZTHAW 冻融",
              ex="RT", size=32, page="eq"),
            f("shelfLifeDays", "Integer", "保质期（天，为空不限）", ex="365"),
            f("openLifeDays", "Integer", "开封后有效期（天，为空不限）", ex="30"),
            f("hazardLevel", "String", "危险品等级：NONE/LOW/MEDIUM/HIGH/FLAMMABLE/TOXIC", ex="NONE", size=16, page="eq"),
            f("fefo", "Boolean", "是否 FEFO 出库", ex="true", required=True),
            f("specJson", "String", "其他规格参数（JSON）", ex='JSON 对象，如 purity', size=1024),
            f("status", "Integer", "状态", ex="0", required=True, page="eq"),
            f("description", "String", "备注", ex="常规缓冲液", size=512),
        ],
    },
    {
        "table": "wms_material_instance", "prefix": "MaterialInstance", "domain": "instance",
        "biz": "wms:material-instance", "comment": "物料实例", "list": True,
        "unique": [{"fields": ["instanceCode"], "label": "实例编码", "err": "MATERIAL_INSTANCE_CODE_DUPLICATE"}],
        "fields": [
            f("instanceCode", "String", "实例编码", ex="PLATE96-000123", size=64, required=True, page="like"),
            f("instanceName", "String", "实例名称", ex="第 1 块 96 孔板", size=128, page="like"),
            f("barcode", "String", "条码", ex="6901234567890", size=128, page="like"),
            f("containerTypeId", "Long", "容器类型编号", ex="1024", required=True, page="eq"),
            f("containerTypeCode", "String", "容器类型编码（冗余）", ex="PLATE_96_WELL", size=64, required=True, page="eq"),
            f("parentInstanceId", "Long", "父实例编号（为空表示顶层）", ex="512", page="eq"),
            f("parentPositionCode", "String", "在父容器中的位置", ex="A1", size=32, page="eq"),
            f("instancePath", "String", "物化路径", ex="/512/1024", size=512, page="like"),
            f("rootInstanceId", "Long", "根实例编号", ex="512", page="eq"),
            f("rootSlotId", "Long", "落位槽位编号（仅顶层实例）", ex="2048", page="eq"),
            f("rootSlotCode", "String", "落位槽位编码（冗余）", ex="SLOT_RACK_A01_R1C1", size=64, page="eq"),
            f("contentDefId", "Long", "内容物定义编号（CARRIER 与空容器为空）", ex="2048", page="eq"),
            f("contentDefCode", "String", "内容物编码（冗余）", ex="PH-BUFFER-7", size=64, page="eq"),
            f("contentType", "String", "内容物类型快照（CARRIER 为空；空容器为 EMPTY）", ex="BUFFER", size=32, page="eq"),
            f("currentVolUl", "BigDecimal", "当前体积（μL）", ex="200.00"),
            f("currentCount", "Integer", "当前数量（个）", ex="1"),
            f("extData", "String", "扩展数据（JSON，实例级覆盖描述）", ex='JSON 对象，如 concentration', size=512),
            f("instanceStatus", "String", "实例状态：AVAILABLE 可用/RESERVED 已预留/IN_USE 使用中/USED 已用完/EXPIRED 已过期/DISCARDED 已废弃",
              ex="AVAILABLE", size=32, required=True, page="eq"),
            f("description", "String", "备注", ex="第 1 块", size=512),
        ],
    },
]


def snake_upper(name):
    return re.sub(r"(?<!^)(?=[A-Z])", "_", name).upper()


# 错误码文案里对「实体」的称呼（比表的注释更自然，例如「区域」而非「区域树」）
ENTITY = {
    "Zone": "区域", "Slot": "槽位",
    "ContainerType": "容器类型", "ContentDef": "内容物定义",
    "MaterialInstance": "物料实例",
}


def entity_of(t):
    return ENTITY.get(t["prefix"], t["comment"])


def cap(name):
    return name[0].upper() + name[1:]


def write(path, content):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as fp:
        fp.write(content)
    return path


def type_imports(fields):
    used = sorted({TYPE_IMPORTS[x["type"]] for x in fields if x["type"] in TYPE_IMPORTS})
    return "".join("import {};\n".format(i) for i in used), ("" if not used else "\n")


# ---------------------------------------------------------------- DO
T_DO_FIELD = Template("""    /**
     * $comment
     */
    private $type $name;

""")


def gen_do(t):
    field_src = "".join(T_DO_FIELD.substitute(comment=x["comment"], type=x["type"], name=x["name"])
                        for x in t["fields"])
    timp, _ = type_imports(t["fields"])
    content = """package $pkg.dal.dataobject.$domain;

import cn.boheng.frame.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;
$timp
/**
 * $comment DO
 *
 * 对应表 $table，继承 TenantBaseDO，由租户拦截器自动隔离
 *
 * @author yinan
 */
@TableName("$table")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class $prefixDO extends TenantBaseDO {

    /**
     * 主键（雪花算法，应用侧生成）
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

$fields}
""".replace("$pkg", PKG).replace("$domain", t["domain"]).replace("$table", t["table"]) \
        .replace("$comment", t["comment"]).replace("$prefix", t["prefix"]) \
        .replace("$timp", timp).replace("$fields", field_src)
    return write("{}/dal/dataobject/{}/{}DO.java".format(SRC, t["domain"], t["prefix"]), content)


# ---------------------------------------------------------------- Mapper
def mapper_conditions(t):
    lines = []
    for x in t["fields"]:
        if x["page"] == "like":
            lines.append("                .likeIfPresent({p}DO::get{n}, reqVO.get{n}())".format(p=t["prefix"], n=cap(x["name"])))
        elif x["page"] == "eq":
            lines.append("                .eqIfPresent({p}DO::get{n}, reqVO.get{n}())".format(p=t["prefix"], n=cap(x["name"])))
    lines.append("                .betweenIfPresent({}DO::getCreateTime, reqVO.getCreateTime())".format(t["prefix"]))
    lines.append("                .orderByDesc({}DO::getId));".format(t["prefix"]))
    return "\n".join(lines)


def gen_mapper(t):
    select_helpers = ""
    for u in t["unique"]:
        fs = u["fields"]
        params = ", ".join("{} {}".format(field_type(t, x), x) for x in fs)
        called = ", ".join("{}DO::get{}, {}".format(t["prefix"], cap(x), x) for x in fs)
        select_helpers += """
    default {p}DO selectBy{fcap}({params}) {{
        return selectOne({called});
    }}
""".format(p=t["prefix"], fcap="And".join(cap(x) for x in fs), params=params, called=called)
    content = """package $pkg.dal.mysql.$domain;

import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.framework.mybatis.core.mapper.BaseMapperX;
import cn.boheng.frame.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.boheng.frame.module.wms.controller.admin.$domain.vo.${prefix}PageReqVO;
import cn.boheng.frame.module.wms.dal.dataobject.$domain.${prefix}DO;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;
$timp
/**
 * $comment Mapper
 *
 * @author yinan
 */
@Mapper
public interface ${prefix}Mapper extends BaseMapperX<${prefix}DO> {

    default PageResult<${prefix}DO> selectPage(${prefix}PageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<${prefix}DO>()
$conditions
    }
$helpers$mapper_extra}
""".replace("$pkg", PKG).replace("$domain", t["domain"]) \
        .replace("$comment", t["comment"]).replace("${prefix}", t["prefix"]) \
        .replace("$conditions", mapper_conditions(t)).replace("$helpers", select_helpers) \
        .replace("$timp", "" if not t["unique"] else "") \
        .replace("$mapper_extra", t.get("mapper_extra", ""))
    return write("{}/dal/mysql/{}/{}Mapper.java".format(SRC, t["domain"], t["prefix"]), content)


def field_type(t, fname):
    for x in t["fields"]:
        if x["name"] == fname:
            return x["type"]
    raise KeyError(fname)


# ---------------------------------------------------------------- VO
def gen_save_vo(t):
    """创建/修改 Request VO"""
    lines = []
    for x in t["fields"]:
        if not x["save"]:
            continue
        ann = []
        if x["type"] == "String":
            if x["required"]:
                ann.append('    @NotEmpty(message = "{}不能为空")'.format(x["comment"]))
            if x["size"]:
                ann.append('    @Size(max = {}, message = "{}长度不能超过 {} 个字符")'.format(x["size"], x["comment"], x["size"]))
        elif x["required"]:
            ann.append('    @NotNull(message = "{}不能为空")'.format(x["comment"]))
        if x["name"] == "status":
            ann.append("    @InEnum(value = CommonStatusEnum.class, message = \"修改状态必须是 {value}\")")
        lines.append('    @Schema(description = "{}", {}example = "{}")'.format(
            x["comment"], "requiredMode = Schema.RequiredMode.REQUIRED, " if x["required"] else "",
            x["ex"] if x["ex"] is not None else ""))
        lines.extend(ann)
        lines.append("    private {} {};".format(x["type"], x["name"]))
        lines.append("")
    body = "\n".join(lines).rstrip()
    has_common_status = any(x["name"] == "status" and x["save"] for x in t["fields"])
    content = """package $pkg.controller.admin.$domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
$extra$timp
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * $comment 创建/修改 Request VO
 *
 * @author yinan
 */
@Schema(description = "管理后台 - $comment 创建/修改 Request VO")
@Data
public class ${prefix}SaveReqVO {

    @Schema(description = "主键", example = "1024")
    private Long id;

$body
}
""".replace("$pkg", PKG).replace("$domain", t["domain"]) \
        .replace("$comment", t["comment"]).replace("${prefix}", t["prefix"]) \
        .replace("$body", body) \
        .replace("$extra", ('import cn.boheng.frame.framework.common.enums.CommonStatusEnum;\n'
                            'import cn.boheng.frame.framework.common.validation.InEnum;\n') if has_common_status else "") \
        .replace("$timp", type_imports([x for x in t["fields"] if x["save"]])[0])
    return write("{}/controller/admin/{}/vo/{}SaveReqVO.java".format(SRC, t["domain"], t["prefix"]), content)


def gen_page_vo(t):
    lines = []
    for x in t["fields"]:
        if not x["page"]:
            continue
        lines.append('    @Schema(description = "{}", example = "{}")'.format(
            x["comment"], x["ex"] if x["ex"] is not None else ""))
        lines.append("    private {} {};".format(x["type"], x["name"]))
        lines.append("")
    lines.append('    @Schema(description = "创建时间")')
    lines.append("    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)")
    lines.append("    private LocalDateTime[] createTime;")
    body = "\n".join(lines)
    content = """package $pkg.controller.admin.$domain.vo;

import cn.boheng.frame.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDateTime;
$timp
import static cn.boheng.frame.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/**
 * $comment 分页 Request VO
 *
 * @author yinan
 */
@Schema(description = "管理后台 - $comment 分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ${prefix}PageReqVO extends PageParam {

$body
}
""".replace("$pkg", PKG).replace("$domain", t["domain"]) \
        .replace("$comment", t["comment"]).replace("${prefix}", t["prefix"]).replace("$body", body) \
        .replace("$timp", type_imports([x for x in t["fields"] if x["page"] and x["type"] != "LocalDateTime"])[0])
    return write("{}/controller/admin/{}/vo/{}PageReqVO.java".format(SRC, t["domain"], t["prefix"]), content)


def gen_resp_vo(t):
    lines = ['    @Schema(description = "主键", example = "1024")', "    private Long id;", ""]
    for x in t["fields"]:
        if not x["resp"]:
            continue
        lines.append('    @Schema(description = "{}", example = "{}")'.format(
            x["comment"], x["ex"] if x["ex"] is not None else ""))
        lines.append("    private {} {};".format(x["type"], x["name"]))
        lines.append("")
    if t.get("tree"):
        lines.append('    @Schema(description = "子区域")')
        lines.append("    private List<{}RespVO> children;".format(t["prefix"]))
        lines.append("")
    lines.append('    @Schema(description = "创建时间")')
    lines.append("    private LocalDateTime createTime;")
    body = "\n".join(lines)
    content = """package $pkg.controller.admin.$domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
$timp
/**
 * $comment Response VO
 *
 * @author yinan
 */
@Schema(description = "管理后台 - $comment Response VO")
@Data
public class ${prefix}RespVO {

$body
}
""".replace("$pkg", PKG).replace("$domain", t["domain"]) \
        .replace("$comment", t["comment"]).replace("${prefix}", t["prefix"]).replace("$body", body) \
        .replace("$timp", type_imports([x for x in t["fields"] if x["resp"]])[0])
    return write("{}/controller/admin/{}/vo/{}RespVO.java".format(SRC, t["domain"], t["prefix"]), content)


# ---------------------------------------------------------------- Service
def gen_service(t):
    p, c = t["prefix"], t["comment"]
    immutable = t.get("immutable", False)
    methods = ["""
    /**
     * 创建{comment}
     *
     * @param createReqVO 创建信息
     * @return 编号
     */
    Long create{prefix}(@Valid {prefix}SaveReqVO createReqVO);
""".format(comment=c, prefix=p)]
    if not immutable:
        methods.append("""
    /**
     * 更新{comment}
     *
     * @param updateReqVO 更新信息
     */
    void update{prefix}(@Valid {prefix}SaveReqVO updateReqVO);

    /**
     * 删除{comment}
     *
     * @param id 编号
     */
    void delete{prefix}(Long id);

    /**
     * 批量删除{comment}
     *
     * @param ids 编号列表
     */
    void delete{prefix}List(List<Long> ids);
""".format(comment=c, prefix=p))
    methods.append("""
    /**
     * 获得{comment}
     *
     * @param id 编号
     * @return {comment}
     */
    {prefix}DO get{prefix}(Long id);

    /**
     * 获得{comment}分页
     *
     * @param pageReqVO 分页查询
     * @return {comment}分页
     */
    PageResult<{prefix}DO> get{prefix}Page({prefix}PageReqVO pageReqVO);
""".format(comment=c, prefix=p))
    if t.get("list"):
        methods.append("""
    /**
     * 获得{comment}列表
     *
     * @return {comment}列表
     */
    List<{prefix}DO> get{prefix}List();
""".format(comment=c, prefix=p))
    extra_imports = "".join("import {};\n".format(i) for i in t.get("service_imports", []))
    content = """package $pkg.service.$domain;

import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.module.wms.controller.admin.$domain.vo.${prefix}PageReqVO;
import cn.boheng.frame.module.wms.controller.admin.$domain.vo.${prefix}SaveReqVO;
import cn.boheng.frame.module.wms.dal.dataobject.$domain.${prefix}DO;
$svcimports
import jakarta.validation.Valid;
import java.util.List;

/**
 * $comment Service 接口
 *
 * @author yinan
 */
public interface ${prefix}Service {

$methods$svcmethods}
""".replace("$pkg", PKG).replace("$domain", t["domain"]) \
        .replace("$comment", t["comment"]).replace("${prefix}", t["prefix"]) \
        .replace("$methods", "\n".join(methods)) \
        .replace("$svcimports", extra_imports) \
        .replace("$svcmethods", t.get("service_methods", ""))
    return write("{}/service/{}/{}Service.java".format(SRC, t["domain"], t["prefix"]), content)


def gen_service_impl(t):
    p, c, var = t["prefix"], t["comment"], t["prefix"][0].lower() + t["prefix"][1:]
    immutable = t.get("immutable", False)
    uppers = snake_upper(t["prefix"])

    impls = ["""
    @Override
    public Long create{prefix}({prefix}SaveReqVO createReqVO) {{
{checks}
        // 插入
        {prefix}DO {var} = BeanUtils.toBean(createReqVO, {prefix}DO.class);
        {var}Mapper.insert({var});
        return {var}.getId();
    }}
""".format(prefix=p, var=var, checks=unique_checks(t, "createReqVO"))]

    if not immutable:
        impls.append("""
    @Override
    public void update{prefix}({prefix}SaveReqVO updateReqVO) {{
        // 校验存在
        validate{prefix}Exists(updateReqVO.getId());
{checks}
        // 更新
        {prefix}DO updateObj = BeanUtils.toBean(updateReqVO, {prefix}DO.class);
        {var}Mapper.updateById(updateObj);
    }}

    @Override
    public void delete{prefix}(Long id) {{
        // 校验存在
        validate{prefix}Exists(id);
        // 删除
        {var}Mapper.deleteById(id);
    }}

    @Override
    public void delete{prefix}List(List<Long> ids) {{
        {var}Mapper.deleteBatch({prefix}DO::getId, ids);
    }}
""".format(prefix=p, var=var, checks=unique_checks(t, "updateReqVO", indent="        ")))

    impls.append("""
    @Override
    public {prefix}DO get{prefix}(Long id) {{
        return {var}Mapper.selectById(id);
    }}

    @Override
    public PageResult<{prefix}DO> get{prefix}Page({prefix}PageReqVO pageReqVO) {{
        return {var}Mapper.selectPage(pageReqVO);
    }}
""".format(prefix=p, var=var))

    if t.get("list"):
        impls.append("""
    @Override
    public List<{prefix}DO> get{prefix}List() {{
        return {var}Mapper.selectList();
    }}
""".format(prefix=p, var=var))

    extra = ["""
    @VisibleForTesting
    void validate{prefix}Exists(Long id) {{
        if ({var}Mapper.selectById(id) == null) {{
            throw exception({upper}_NOT_EXISTS);
        }}
    }}
""".format(prefix=p, var=var, upper=uppers)]

    for u in t["unique"]:
        fs = u["fields"]
        params = ", ".join("{} {}".format(field_type(t, x), x) for x in fs)
        call_args = ", ".join(fs)
        # 空值短路：字符串用 StrUtil.isBlank，数值等其他类型用 == null
        cond = " || ".join(
            "StrUtil.isBlank({})".format(x) if field_type(t, x) == "String" else "{} == null".format(x)
            for x in fs)
        dup_args = ", ".join(fs)
        extra.append("""
    @VisibleForTesting
    void validate{fcap}Unique(Long id, {params}) {{
        if ({cond}) {{
            return;
        }}
        {prefix}DO {var} = {var}Mapper.selectBy{fcap}({call_args});
        if ({var} == null) {{
            return;
        }}
        // 如果 id 为空，说明不用比较是否为相同 id 的记录
        if (id == null || !{var}.getId().equals(id)) {{
            throw exception({err}{throwargs});
        }}
    }}
""".format(fcap="And".join(cap(x) for x in fs), params=params, cond=cond, prefix=p, var=var,
           call_args=call_args, err=u["err"],
           throwargs=", " + dup_args if len(fs) == 1 else ""))

    extra_imports = "".join("import {};\n".format(i) for i in t.get("service_impl_imports", []))
    content = """package $pkg.service.$domain;

import cn.hutool.core.util.StrUtil;
import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.framework.common.util.object.BeanUtils;
import cn.boheng.frame.module.wms.controller.admin.$domain.vo.${prefix}PageReqVO;
import cn.boheng.frame.module.wms.controller.admin.$domain.vo.${prefix}SaveReqVO;
import cn.boheng.frame.module.wms.dal.dataobject.$domain.${prefix}DO;
import cn.boheng.frame.module.wms.dal.mysql.$domain.${prefix}Mapper;
import com.google.common.annotations.VisibleForTesting;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
$svcimports
import jakarta.annotation.Resource;
import java.util.List;

import static cn.boheng.frame.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.boheng.frame.module.wms.enums.ErrorCodeConstants.*;

/**
 * $comment Service 实现类
 *
 * @author yinan
 */
@Service
@Validated
public class ${prefix}ServiceImpl implements ${prefix}Service {

    @Resource
    private ${prefix}Mapper ${var}Mapper;
$inj
$impls$validators$extramembers}
""".replace("$pkg", PKG).replace("$domain", t["domain"]) \
        .replace("$comment", t["comment"]).replace("${prefix}", t["prefix"]).replace("${var}", var) \
        .replace("$impls", "\n".join(impls)) \
        .replace("$validators", "\n".join(extra)) \
        .replace("$svcimports", extra_imports) \
        .replace("$inj", t.get("service_impl_injections", "")) \
        .replace("$extramembers", t.get("service_impl_members", ""))
    return write("{}/service/{}/{}ServiceImpl.java".format(SRC, t["domain"], t["prefix"]), content)


def unique_checks(t, arg, indent="        "):
    """生成 Service 里的唯一性校验调用"""
    out = []
    for u in t["unique"]:
        label = u["label"]
        args = ", ".join("{}.get{}()".format(arg, cap(x)) for x in u["fields"])
        out.append("{}// 校验{}的唯一性".format(indent, label))
        out.append("{}validate{}Unique({}.getId(), {});".format(
            indent, "And".join(cap(x) for x in u["fields"]), arg, args))
    return ("\n".join(out) + "\n") if out else ""


# ---------------------------------------------------------------- Controller
# Controller 的 URL 映射（显式声明，避免用 replace 链推导出错误路径）
URL_BY_DOMAIN = {
    "zone": "/wms/zone",
    "slot": "/wms/slot",
    "container": "/wms/container-type",
    "content": "/wms/content-def",
    "instance": "/wms/material-instance",
}


def gen_controller(t):
    p, c = t["prefix"], t["comment"]
    var = t["prefix"][0].lower() + t["prefix"][1:]
    immutable = t.get("immutable", False)
    biz = t["biz"]
    urls = {'/create': 'create', '/update': 'update', '/delete': 'delete', '/delete-list': 'delete',
            '/page': 'query', '/get': 'query', '/list': 'query'}

    eps = ["""
    @PostMapping("/create")
    @Operation(summary = "创建{comment}")
    @PreAuthorize("@ss.hasPermission('{biz}:create')")
    public CommonResult<Long> create{prefix}(@Valid @RequestBody {prefix}SaveReqVO createReqVO) {{
        return success({var}Service.create{prefix}(createReqVO));
    }}
""".format(comment=c, prefix=p, biz=biz, var=var)]

    if not immutable:
        eps.append("""
    @PutMapping("/update")
    @Operation(summary = "更新{comment}")
    @PreAuthorize("@ss.hasPermission('{biz}:update')")
    public CommonResult<Boolean> update{prefix}(@Valid @RequestBody {prefix}SaveReqVO updateReqVO) {{
        {var}Service.update{prefix}(updateReqVO);
        return success(true);
    }}

    @DeleteMapping("/delete")
    @Operation(summary = "删除{comment}")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('{biz}:delete')")
    public CommonResult<Boolean> delete{prefix}(@RequestParam("id") Long id) {{
        {var}Service.delete{prefix}(id);
        return success(true);
    }}

    @DeleteMapping("/delete-list")
    @Operation(summary = "批量删除{comment}")
    @Parameter(name = "ids", description = "编号列表", required = true)
    @PreAuthorize("@ss.hasPermission('{biz}:delete')")
    public CommonResult<Boolean> delete{prefix}List(@RequestParam("ids") List<Long> ids) {{
        {var}Service.delete{prefix}List(ids);
        return success(true);
    }}
""".format(comment=c, prefix=p, biz=biz, var=var))

    eps.append("""
    @GetMapping("/page")
    @Operation(summary = "获得{comment}分页")
    @PreAuthorize("@ss.hasPermission('{biz}:query')")
    public CommonResult<PageResult<{prefix}RespVO>> get{prefix}Page(@Valid {prefix}PageReqVO pageReqVO) {{
        PageResult<{prefix}DO> pageResult = {var}Service.get{prefix}Page(pageReqVO);
        return success(BeanUtils.toBean(pageResult, {prefix}RespVO.class));
    }}

    @GetMapping("/get")
    @Operation(summary = "获得{comment}")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('{biz}:query')")
    public CommonResult<{prefix}RespVO> get{prefix}(@RequestParam("id") Long id) {{
        {prefix}DO {var} = {var}Service.get{prefix}(id);
        return success(BeanUtils.toBean({var}, {prefix}RespVO.class));
    }}
""".format(comment=c, prefix=p, biz=biz, var=var))

    if t.get("list"):
        eps.append("""
    @GetMapping("/list")
    @Operation(summary = "获得{comment}列表")
    @PreAuthorize("@ss.hasPermission('{biz}:query')")
    public CommonResult<List<{prefix}RespVO>> get{prefix}List() {{
        List<{prefix}DO> list = {var}Service.get{prefix}List();
        return success(BeanUtils.toBean(list, {prefix}RespVO.class));
    }}
""".format(comment=c, prefix=p, biz=biz, var=var))

    content = """package $pkg.controller.admin.$domain;

import cn.boheng.frame.framework.common.pojo.CommonResult;
import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.framework.common.util.object.BeanUtils;
import cn.boheng.frame.module.wms.controller.admin.$domain.vo.${prefix}PageReqVO;
import cn.boheng.frame.module.wms.controller.admin.$domain.vo.${prefix}RespVO;
import cn.boheng.frame.module.wms.controller.admin.$domain.vo.${prefix}SaveReqVO;
import cn.boheng.frame.module.wms.dal.dataobject.$domain.${prefix}DO;
import cn.boheng.frame.module.wms.service.$domain.${prefix}Service;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.util.List;

import static cn.boheng.frame.framework.common.pojo.CommonResult.success;

/**
 * $comment Controller
 *
 * @author yinan
 */
@Tag(name = "管理后台 - $comment")
@RestController
@RequestMapping("$url")
@Validated
public class ${prefix}Controller {

    @Resource
    private ${prefix}Service ${var}Service;

$episodes}
""".replace("$pkg", PKG).replace("$domain", t["domain"]) \
        .replace("$comment", t["comment"]).replace("${prefix}", t["prefix"]).replace("${var}", var) \
        .replace("$url", URL_BY_DOMAIN[t["domain"]]) \
        .replace("$episodes", "\n".join(eps))
    return write("{}/controller/admin/{}/{}Controller.java".format(SRC, t["domain"], t["prefix"]), content)


# ---------------------------------------------------------------- 错误码
def gen_error_codes():
    lines = ['package {}.enums;'.format(PKG), "",
             "import cn.boheng.frame.framework.common.exception.ErrorCode;", "",
             "/**",
             " * WMS 错误码枚举类",
             " *",
             " * wms 模块，使用 1-003-000-000 段",
             " *",
             " * @author yinan",
             " */",
             "public interface ErrorCodeConstants {", ""]
    for i, t in enumerate(TABLES):
        ent = entity_of(t)
        lines.append("    // ========== {} 1-003-{:03d}-000 ==========".format(t["comment"], i))
        lines.append('    ErrorCode {u}_NOT_EXISTS = new ErrorCode(1_003_{i:03d}_000, "{c}不存在");'.format(
            u=snake_upper(t["prefix"]), i=i, c=ent))
        for j, u in enumerate(t["unique"]):
            if len(u["fields"]) == 1:
                msg = "已经存在{}为【{}】的{}".format(u["label"], "{}", ent)
            else:
                msg = "已经存在相同{}的{}".format(u["label"], ent)
            lines.append('    ErrorCode {err} = new ErrorCode(1_003_{i:03d}_{j:03d}, "{msg}");'.format(
                err=u["err"], i=i, j=j + 1, msg=msg))
        lines.append("")
    lines.append("}")
    return write("{}/enums/ErrorCodeConstants.java".format(SRC), "\n".join(lines) + "\n")


def main():
    created = []
    for t in TABLES:
        created.append(gen_do(t))
        created.append(gen_mapper(t))
        created.append(gen_save_vo(t))
        created.append(gen_page_vo(t))
        created.append(gen_resp_vo(t))
        created.append(gen_service(t))
        created.append(gen_service_impl(t))
        created.append(gen_controller(t))
    created.append(gen_error_codes())
    for p in created:
        print(p.replace(MODULE + "/src/main/java/", ""))


if __name__ == "__main__":
    main()
