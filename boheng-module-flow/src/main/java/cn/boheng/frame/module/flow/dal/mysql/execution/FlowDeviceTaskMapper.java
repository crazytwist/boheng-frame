package cn.boheng.frame.module.flow.dal.mysql.execution;

import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.framework.mybatis.core.mapper.BaseMapperX;
import cn.boheng.frame.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.boheng.frame.module.flow.controller.admin.execution.vo.FlowDeviceTaskPageReqVO;
import cn.boheng.frame.module.flow.dal.dataobject.execution.FlowDeviceTaskDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

/**
 * 设备异步任务队列表。
 */
@Mapper
public interface FlowDeviceTaskMapper extends BaseMapperX<FlowDeviceTaskDO> {

    /**
     * 按任务号查
     */
    default FlowDeviceTaskDO selectByTaskNo(String taskNo) {
        return selectOne(FlowDeviceTaskDO::getTaskNo, taskNo);
    }

    /**
     * 按运行 + 节点 + 尝试查任务（超时标记用）
     */
    default FlowDeviceTaskDO selectByExecutionNode(Long executionId, String nodeId, Integer attempt) {
        return selectOne(new LambdaQueryWrapperX<FlowDeviceTaskDO>()
                .eq(FlowDeviceTaskDO::getExecutionId, executionId)
                .eq(FlowDeviceTaskDO::getNodeId, nodeId)
                .eq(FlowDeviceTaskDO::getAttempt, attempt)
                .orderByDesc(FlowDeviceTaskDO::getId)
                .last("LIMIT 1"));
    }

    /**
     * 按运行、状态、设备、任务类型分页（管理后台监控用）
     */
    default PageResult<FlowDeviceTaskDO> selectPage(FlowDeviceTaskPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<FlowDeviceTaskDO>()
                .eqIfPresent(FlowDeviceTaskDO::getExecutionId, reqVO.getExecutionId())
                .eqIfPresent(FlowDeviceTaskDO::getStatus, reqVO.getStatus())
                .eqIfPresent(FlowDeviceTaskDO::getDeviceCode, reqVO.getDeviceCode())
                .eqIfPresent(FlowDeviceTaskDO::getActionCode, reqVO.getActionCode())
                .orderByDesc(FlowDeviceTaskDO::getId));
    }

    /**
     * 取一条待认领任务。deviceCode / actionCode 空则不筛（机器端可按设备、按任务类型、或组合拉）
     */
    default FlowDeviceTaskDO selectClaimable(String deviceCode, String actionCode) {
        return selectOne(new LambdaQueryWrapperX<FlowDeviceTaskDO>()
                .eq(FlowDeviceTaskDO::getStatus, "PENDING")
                .eqIfPresent(FlowDeviceTaskDO::getDeviceCode, deviceCode)
                .eqIfPresent(FlowDeviceTaskDO::getActionCode, actionCode)
                .orderByAsc(FlowDeviceTaskDO::getId)
                .last("LIMIT 1"));
    }

    /**
     * CAS 认领：PENDING → CLAIMED。返回 1 表示认领成功，0 表示被抢走
     */
    default int claim(Long id, String claimedBy, Long waitMs) {
        FlowDeviceTaskDO patch = new FlowDeviceTaskDO();
        patch.setStatus("CLAIMED");
        patch.setClaimedBy(claimedBy);
        patch.setClaimedAt(LocalDateTime.now());
        patch.setWaitMs(waitMs);
        return update(patch, new LambdaUpdateWrapper<FlowDeviceTaskDO>()
                .eq(FlowDeviceTaskDO::getId, id)
                .eq(FlowDeviceTaskDO::getStatus, "PENDING"));
    }

    /**
     * 标记任务终态（完成 / 失败）。只允许从 CLAIMED 推进，防重复回报
     */
    default int finish(Long id, String status, String resultJson, String errorMessage, Long executeMs) {
        FlowDeviceTaskDO patch = new FlowDeviceTaskDO();
        patch.setStatus(status);
        patch.setResultJson(resultJson);
        patch.setErrorMessage(errorMessage);
        patch.setExecuteMs(executeMs);
        return update(patch, new LambdaUpdateWrapper<FlowDeviceTaskDO>()
                .eq(FlowDeviceTaskDO::getId, id)
                .eq(FlowDeviceTaskDO::getStatus, "CLAIMED"));
    }

}
