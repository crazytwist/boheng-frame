package cn.boheng.frame.module.device.dal.mysql.command;

import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.framework.mybatis.core.mapper.BaseMapperX;
import cn.boheng.frame.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.boheng.frame.module.device.controller.admin.command.vo.DeviceCommandPageReqVO;
import cn.boheng.frame.module.device.dal.dataobject.command.DeviceCommandDO;
import org.apache.ibatis.annotations.Mapper;
/**
 * 命令记录 Mapper
 */

@Mapper
public interface DeviceCommandMapper extends BaseMapperX<DeviceCommandDO> {

    /** 分页查询命令记录 */
    default PageResult<DeviceCommandDO> selectPage(DeviceCommandPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<DeviceCommandDO>()
                .likeIfPresent(DeviceCommandDO::getCommandNo, reqVO.getCommandNo())
                .likeIfPresent(DeviceCommandDO::getDeviceCode, reqVO.getDeviceCode())
                .eqIfPresent(DeviceCommandDO::getActionCode, reqVO.getActionCode())
                .eqIfPresent(DeviceCommandDO::getStatus, reqVO.getStatus())
                .orderByDesc(DeviceCommandDO::getId));
    }

}
