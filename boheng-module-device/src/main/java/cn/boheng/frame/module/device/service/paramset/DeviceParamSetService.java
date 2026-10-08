package cn.boheng.frame.module.device.service.paramset;

import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.module.device.controller.admin.paramset.vo.DeviceParamSetPageReqVO;
import cn.boheng.frame.module.device.controller.admin.paramset.vo.DeviceParamSetSaveReqVO;
import cn.boheng.frame.module.device.dal.dataobject.paramset.DeviceParamSetDO;

import jakarta.validation.Valid;
import java.util.List;

/**
 * 参数集预设 Service 接口
 *
 * @author yinan
 */
public interface DeviceParamSetService {

    /**
     * 创建参数集
     *
     * @return 编号
     */
    Long createParamSet(@Valid DeviceParamSetSaveReqVO createReqVO);

    /**
     * 更新参数集
     */
    void updateParamSet(@Valid DeviceParamSetSaveReqVO updateReqVO);

    /**
     * 删除参数集
     */
    void deleteParamSet(Long id);

    /**
     * 批量删除参数集
     */
    void deleteParamSetList(List<Long> ids);

    /**
     * 获得参数集
     */
    DeviceParamSetDO getParamSet(Long id);

    /**
     * 获得参数集分页
     */
    PageResult<DeviceParamSetDO> getParamSetPage(DeviceParamSetPageReqVO pageReqVO);

    /**
     * 获得参数集列表（可按「类型 + 动作」过滤）
     */
    List<DeviceParamSetDO> getParamSetList(String deviceTypeCode, String actionCode);

    /**
     * 标记参数集验证状态（只有已验证的参数集才能被用于发起命令）
     *
     * @param validated 是否已验证
     * @param validatedBy 验证人
     */
    void updateValidated(Long id, Boolean validated, String validatedBy);

}
