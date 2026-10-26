package cn.boheng.frame.module.device.dal.mysql.command;

import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.framework.mybatis.core.mapper.BaseMapperX;
import cn.boheng.frame.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.boheng.frame.module.device.controller.admin.command.vo.DeviceCommandPageReqVO;
import cn.boheng.frame.module.device.dal.dataobject.command.DeviceCommandDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

import static cn.boheng.frame.module.device.enums.DeviceEnums.CommandStatusEnum;
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

    /** 按对外命令号查命令 */
    default DeviceCommandDO selectByCommandNo(String commandNo) {
        return selectOne(DeviceCommandDO::getCommandNo, commandNo);
    }

    /** 这台设备上还没结束的命令数 */
    default long countRunning(Long deviceId) {
        return selectCount(new LambdaQueryWrapperX<DeviceCommandDO>()
                .eq(DeviceCommandDO::getDeviceId, deviceId)
                .in(DeviceCommandDO::getStatus, CommandStatusEnum.ACQUIRED.getStatus(),
                        CommandStatusEnum.SENT.getStatus(), CommandStatusEnum.WAITING.getStatus()));
    }

    /**
     * 查询已经超过回调截止时间的等待命令，由服务逐条收口并释放设备占用。
     */
    default List<DeviceCommandDO> selectExpiredWaiting(LocalDateTime before) {
        return selectList(new LambdaQueryWrapperX<DeviceCommandDO>()
                .eq(DeviceCommandDO::getStatus, CommandStatusEnum.WAITING.getStatus())
                .lt(DeviceCommandDO::getTimeoutAt, before));
    }

    /**
     * 长时间没有结果、也没有截止时刻的命令标成失败（已占用、已下发、轮询中），通常是调用中途服务重启
     */
    default int failStaleAcquired(LocalDateTime before) {
        return update(null, new LambdaUpdateWrapper<DeviceCommandDO>()
                .set(DeviceCommandDO::getStatus, CommandStatusEnum.FAILED.getStatus())
                .set(DeviceCommandDO::getWaitingFor, "")
                .set(DeviceCommandDO::getFinishedAt, LocalDateTime.now())
                .set(DeviceCommandDO::getErrorMsg, "调用中断，未拿到结果")
                .and(w -> w.in(DeviceCommandDO::getStatus, CommandStatusEnum.ACQUIRED.getStatus(),
                                CommandStatusEnum.SENT.getStatus())
                        .or(o -> o.eq(DeviceCommandDO::getStatus, CommandStatusEnum.WAITING.getStatus())
                                .isNull(DeviceCommandDO::getTimeoutAt)))
                .lt(DeviceCommandDO::getAcquiredAt, before));
    }

    /**
     * 只在命令还停在指定状态时改成新状态，返回是否改成功。回调和超时同时到达时靠它只认先到的那个。
     */
    default boolean updateIfStatus(DeviceCommandDO update, String expectStatus) {
        return update(update, new LambdaQueryWrapperX<DeviceCommandDO>()
                .eq(DeviceCommandDO::getId, update.getId())
                .eq(DeviceCommandDO::getStatus, expectStatus)) > 0;
    }

}
