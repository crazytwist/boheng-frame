package cn.boheng.frame.module.device.dal.mysql.action;

import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.framework.mybatis.core.mapper.BaseMapperX;
import cn.boheng.frame.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.boheng.frame.module.device.controller.admin.action.vo.DeviceActionPageReqVO;
import cn.boheng.frame.module.device.dal.dataobject.action.DeviceActionDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 设备动作定义 Mapper
 *
 * @author yinan
 */
@Mapper
public interface DeviceActionMapper extends BaseMapperX<DeviceActionDO> {

    /** 分页查询动作 */
    default PageResult<DeviceActionDO> selectPage(DeviceActionPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<DeviceActionDO>()
                .eqIfPresent(DeviceActionDO::getDeviceTypeCode, reqVO.getDeviceTypeCode())
                .likeIfPresent(DeviceActionDO::getActionCode, reqVO.getActionCode())
                .likeIfPresent(DeviceActionDO::getActionName, reqVO.getActionName())
                .likeIfPresent(DeviceActionDO::getStandardFeature, reqVO.getStandardFeature())
                .eqIfPresent(DeviceActionDO::getSource, reqVO.getSource())
                .eqIfPresent(DeviceActionDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(DeviceActionDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(DeviceActionDO::getId));
    }

    /**
     * 按「类型 + 动作」查（Service 层唯一性校验用）
     */
    default DeviceActionDO selectByTypeAndAction(String deviceTypeCode, String actionCode) {
        return selectOne(new LambdaQueryWrapperX<DeviceActionDO>()
                .eq(DeviceActionDO::getDeviceTypeCode, deviceTypeCode)
                .eq(DeviceActionDO::getActionCode, actionCode));
    }

    /**
     * 按设备类型查动作列表（能力自描述 / 前端按钮渲染用）
     */
    default List<DeviceActionDO> selectListByTypeCode(String deviceTypeCode) {
        return selectList(DeviceActionDO::getDeviceTypeCode, deviceTypeCode);
    }

}
