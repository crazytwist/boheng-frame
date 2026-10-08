package cn.boheng.frame.module.wms.service.zone;

import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.module.wms.controller.admin.zone.vo.ZonePageReqVO;
import cn.boheng.frame.module.wms.controller.admin.zone.vo.ZoneSaveReqVO;
import cn.boheng.frame.module.wms.dal.dataobject.zone.ZoneDO;

import jakarta.validation.Valid;
import java.util.List;

/**
 * 区域树 Service 接口
 *
 * @author yinan
 */
public interface ZoneService {


    /**
     * 创建区域树
     *
     * @param createReqVO 创建信息
     * @return 编号
     */
    Long createZone(@Valid ZoneSaveReqVO createReqVO);


    /**
     * 更新区域树
     *
     * @param updateReqVO 更新信息
     */
    void updateZone(@Valid ZoneSaveReqVO updateReqVO);

    /**
     * 删除区域树
     *
     * @param id 编号
     */
    void deleteZone(Long id);

    /**
     * 批量删除区域树
     *
     * @param ids 编号列表
     */
    void deleteZoneList(List<Long> ids);


    /**
     * 获得区域树
     *
     * @param id 编号
     * @return 区域树
     */
    ZoneDO getZone(Long id);

    /**
     * 获得区域树分页
     *
     * @param pageReqVO 分页查询
     * @return 区域树分页
     */
    PageResult<ZoneDO> getZonePage(ZonePageReqVO pageReqVO);


    /**
     * 获得区域树列表
     *
     * @return 区域树列表
     */
    List<ZoneDO> getZoneList();
}
