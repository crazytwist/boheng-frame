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

    /**
     * 按关联键值查还在等回调的命令。固定地址回调靠它认人。
     *
     * <p>只认 WAITING：已经终态的命令不该被迟到的回调改掉，
     * 同时把匹配范围压到最小，降低关联键被猜中的风险。</p>
     */
    default DeviceCommandDO selectWaitingByCorrelateValue(String correlateValue) {
        if (correlateValue == null || correlateValue.isBlank()) {
            return null;
        }
        return selectOne(new LambdaQueryWrapperX<DeviceCommandDO>()
                .eq(DeviceCommandDO::getCorrelateValue, correlateValue)
                .eq(DeviceCommandDO::getStatus, CommandStatusEnum.WAITING.getStatus())
                .orderByDesc(DeviceCommandDO::getId)
                .last("LIMIT 1"));
    }

    /**
     * 兜底：按报文内容找还在等回调的命令。
     *
     * <p>关联键只存一列，但设备可能带回来的是另一个等价字段——比如我们发了
     * EventId 和 RequestId，它回调时只回其中一个。关联键列只有一个位置，
     * 另一个就得靠报文全文找。</p>
     *
     * <p>代价是不能走索引，所以只在精确匹配落空后调用，且限定 WAITING 状态
     * ——等待中的命令本来就只有个位数。调用方必须先用安全字符集校验入参，
     * 避免把 LIKE 的通配符带进来。</p>
     */
    default DeviceCommandDO selectWaitingByPayloadContains(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return selectOne(new LambdaQueryWrapperX<DeviceCommandDO>()
                .eq(DeviceCommandDO::getStatus, CommandStatusEnum.WAITING.getStatus())
                .like(DeviceCommandDO::getPayloadJson, value)
                .orderByDesc(DeviceCommandDO::getId)
                .last("LIMIT 1"));
    }

    /**
     * 在当前租户内按来源业务键读取原命令。租户隔离由 MyBatis 租户插件附加。
     */
    default DeviceCommandDO selectBySource(String sourceType, String sourceRef) {
        return selectOne(new LambdaQueryWrapperX<DeviceCommandDO>()
                .eq(DeviceCommandDO::getSourceType, sourceType)
                .eq(DeviceCommandDO::getSourceRef, sourceRef));
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
     * 长时间没有结果、也没有截止时刻的命令标成失败（已占用、已下发），通常是调用中途服务重启。
     *
     * <p>不含 WAITING：动作显式配了「回调不超时」时 {@code timeout_at} 留空是正常状态，
     * 拿它当中断处理会直接把这类命令打死，等于把「不超时」变成「15 分钟超时」。
     * 这类命令由 {@link #countWaitingWithoutDeadline} 只做提示，不自动收口。</p>
     */
    default int failStaleAcquired(LocalDateTime before) {
        return update(null, new LambdaUpdateWrapper<DeviceCommandDO>()
                .set(DeviceCommandDO::getStatus, CommandStatusEnum.FAILED.getStatus())
                .set(DeviceCommandDO::getWaitingFor, "")
                .set(DeviceCommandDO::getFinishedAt, LocalDateTime.now())
                .set(DeviceCommandDO::getErrorMsg, "调用中断，未拿到结果")
                .in(DeviceCommandDO::getStatus, CommandStatusEnum.ACQUIRED.getStatus(),
                        CommandStatusEnum.SENT.getStatus())
                .lt(DeviceCommandDO::getAcquiredAt, before));
    }

    /**
     * 还在等回调、但根本没有截止时刻的命令数（即动作配了「回调不超时」）。
     *
     * <p>这些命令不会被自动收口，回调丢了就一直挂着，只能人工结束；扫描器据此打提示日志。</p>
     */
    default long countWaitingWithoutDeadline(LocalDateTime before) {
        Long count = selectCount(new LambdaQueryWrapperX<DeviceCommandDO>()
                .eq(DeviceCommandDO::getStatus, CommandStatusEnum.WAITING.getStatus())
                .isNull(DeviceCommandDO::getTimeoutAt)
                .lt(DeviceCommandDO::getSentAt, before));
        return count == null ? 0L : count;
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
