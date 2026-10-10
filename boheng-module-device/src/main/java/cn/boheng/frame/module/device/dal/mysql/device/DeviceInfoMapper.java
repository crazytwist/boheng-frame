package cn.boheng.frame.module.device.dal.mysql.device;

import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.framework.mybatis.core.mapper.BaseMapperX;
import cn.boheng.frame.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.boheng.frame.module.device.controller.admin.device.vo.DevicePageReqVO;
import cn.boheng.frame.module.device.dal.dataobject.device.DeviceInfoDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 设备台账 Mapper
 *
 * @author yinan
 */
@Mapper
public interface DeviceInfoMapper extends BaseMapperX<DeviceInfoDO> {

    /** 分页查询设备台账 */
    default PageResult<DeviceInfoDO> selectPage(DevicePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<DeviceInfoDO>()
                .likeIfPresent(DeviceInfoDO::getDeviceCode, reqVO.getDeviceCode())
                .likeIfPresent(DeviceInfoDO::getDeviceName, reqVO.getDeviceName())
                .eqIfPresent(DeviceInfoDO::getDeviceTypeCode, reqVO.getDeviceTypeCode())
                .eqIfPresent(DeviceInfoDO::getVendor, reqVO.getVendor())
                .eqIfPresent(DeviceInfoDO::getDriverType, reqVO.getDriverType())
                .eqIfPresent(DeviceInfoDO::getStatus, reqVO.getStatus())
                .eqIfPresent(DeviceInfoDO::getConnectionType, reqVO.getConnectionType())
                .betweenIfPresent(DeviceInfoDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(DeviceInfoDO::getId));
    }

    /** 按设备编码查询 */
    default DeviceInfoDO selectByDeviceCode(String deviceCode) {
        return selectOne(DeviceInfoDO::getDeviceCode, deviceCode);
    }

    /**
     * 按设备类型查询（「选设备」契约的基础查询）
     */
    default List<DeviceInfoDO> selectListByTypeCode(String deviceTypeCode) {
        return selectList(DeviceInfoDO::getDeviceTypeCode, deviceTypeCode);
    }

    /**
     * 命令抢占设备。设备没有被占用，或者原来的占用已经过期时才抢得到；人工锁和维护锁未过期时抢不到。
     *
     * @return 是否抢到
     */
    default boolean tryOccupy(Long deviceId, Long commandId, String holder, LocalDateTime expireAt) {
        LocalDateTime now = LocalDateTime.now();
        return update(null, new LambdaUpdateWrapper<DeviceInfoDO>()
                .set(DeviceInfoDO::getCurrentCommandId, commandId)
                .set(DeviceInfoDO::getLockHolder, holder)
                .set(DeviceInfoDO::getLockType, "COMMAND")
                .set(DeviceInfoDO::getLockAcquiredTime, now)
                .set(DeviceInfoDO::getLockExpireTime, expireAt)
                .set(DeviceInfoDO::getLockReason, null)
                .eq(DeviceInfoDO::getId, deviceId)
                .and(w -> w.isNull(DeviceInfoDO::getLockType)
                        .or().lt(DeviceInfoDO::getLockExpireTime, now))) > 0;
    }

    /**
     * 命令结束后释放占用。只释放自己抢到的那次，别的命令或人工锁不动。
     */
    default void releaseOccupy(Long deviceId, Long commandId) {
        update(null, new LambdaUpdateWrapper<DeviceInfoDO>()
                .set(DeviceInfoDO::getCurrentCommandId, null)
                .set(DeviceInfoDO::getLockHolder, null)
                .set(DeviceInfoDO::getLockType, null)
                .set(DeviceInfoDO::getLockAcquiredTime, null)
                .set(DeviceInfoDO::getLockExpireTime, null)
                .eq(DeviceInfoDO::getId, deviceId)
                .eq(DeviceInfoDO::getCurrentCommandId, commandId));
    }

}
