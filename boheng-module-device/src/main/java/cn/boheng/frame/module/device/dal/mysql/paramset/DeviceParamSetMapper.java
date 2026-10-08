package cn.boheng.frame.module.device.dal.mysql.paramset;

import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.framework.mybatis.core.mapper.BaseMapperX;
import cn.boheng.frame.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.boheng.frame.module.device.controller.admin.paramset.vo.DeviceParamSetPageReqVO;
import cn.boheng.frame.module.device.dal.dataobject.paramset.DeviceParamSetDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 参数集预设 Mapper
 *
 * @author yinan
 */
@Mapper
public interface DeviceParamSetMapper extends BaseMapperX<DeviceParamSetDO> {

    default PageResult<DeviceParamSetDO> selectPage(DeviceParamSetPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<DeviceParamSetDO>()
                .likeIfPresent(DeviceParamSetDO::getParamSetCode, reqVO.getParamSetCode())
                .likeIfPresent(DeviceParamSetDO::getParamSetName, reqVO.getParamSetName())
                .eqIfPresent(DeviceParamSetDO::getDeviceTypeCode, reqVO.getDeviceTypeCode())
                .eqIfPresent(DeviceParamSetDO::getActionCode, reqVO.getActionCode())
                .eqIfPresent(DeviceParamSetDO::getValidated, reqVO.getValidated())
                .eqIfPresent(DeviceParamSetDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(DeviceParamSetDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(DeviceParamSetDO::getId));
    }

    default DeviceParamSetDO selectByParamSetCode(String paramSetCode) {
        return selectOne(DeviceParamSetDO::getParamSetCode, paramSetCode);
    }

    /**
     * 按「类型 + 动作」查可选参数集（发起命令时下拉用）
     */
    default List<DeviceParamSetDO> selectListByTypeAndAction(String deviceTypeCode, String actionCode) {
        return selectList(new LambdaQueryWrapperX<DeviceParamSetDO>()
                .eq(DeviceParamSetDO::getDeviceTypeCode, deviceTypeCode)
                .eq(DeviceParamSetDO::getActionCode, actionCode));
    }

}
