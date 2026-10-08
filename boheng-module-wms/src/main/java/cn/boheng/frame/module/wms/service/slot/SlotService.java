package cn.boheng.frame.module.wms.service.slot;

import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.module.wms.controller.admin.slot.vo.SlotPageReqVO;
import cn.boheng.frame.module.wms.controller.admin.slot.vo.SlotSaveReqVO;
import cn.boheng.frame.module.wms.dal.dataobject.slot.SlotDO;

import jakarta.validation.Valid;
import java.util.List;

/**
 * 槽位 Service 接口
 *
 * @author yinan
 */
public interface SlotService {


    /**
     * 创建槽位
     *
     * @param createReqVO 创建信息
     * @return 编号
     */
    Long createSlot(@Valid SlotSaveReqVO createReqVO);


    /**
     * 更新槽位
     *
     * @param updateReqVO 更新信息
     */
    void updateSlot(@Valid SlotSaveReqVO updateReqVO);

    /**
     * 删除槽位
     *
     * @param id 编号
     */
    void deleteSlot(Long id);

    /**
     * 批量删除槽位
     *
     * @param ids 编号列表
     */
    void deleteSlotList(List<Long> ids);


    /**
     * 获得槽位
     *
     * @param id 编号
     * @return 槽位
     */
    SlotDO getSlot(Long id);

    /**
     * 获得槽位分页
     *
     * @param pageReqVO 分页查询
     * @return 槽位分页
     */
    PageResult<SlotDO> getSlotPage(SlotPageReqVO pageReqVO);


    /**
     * 获得槽位列表
     *
     * @param zoneCode 区域编码（可空，空则查全部）
     * @return 槽位列表
     */
    List<SlotDO> getSlotList(String zoneCode);

    /**
     * 按区域布局批量生成槽位
     *
     * 读取该区域 ext_data 的货架尺寸（rackRows/rackCols），
     * 按 rowNo=1..rackRows、colNo=1..rackCols 生成 rackRows×rackCols 条槽位。
     * 槽位编码规则：{zoneCode}_R{row}C{col}；坐标写入 ext_data 的 rowNo/colNo。
     *
     * @param zoneCode 区域编码（须为 RACK 类型且已配置货架尺寸）
     * @return 生成的槽位数量
     */
    Integer generateSlotsByZone(String zoneCode);
}
