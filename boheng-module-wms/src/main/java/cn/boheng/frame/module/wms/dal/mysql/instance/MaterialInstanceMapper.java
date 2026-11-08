package cn.boheng.frame.module.wms.dal.mysql.instance;

import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.framework.mybatis.core.mapper.BaseMapperX;
import cn.boheng.frame.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MaterialInstancePageReqVO;
import cn.boheng.frame.module.wms.dal.dataobject.instance.MaterialInstanceDO;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

/**
 * 物料实例 Mapper
 *
 * @author yinan
 */
@Mapper
public interface MaterialInstanceMapper extends BaseMapperX<MaterialInstanceDO> {

    default PageResult<MaterialInstanceDO> selectPage(MaterialInstancePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<MaterialInstanceDO>()
                .likeIfPresent(MaterialInstanceDO::getInstanceCode, reqVO.getInstanceCode())
                .likeIfPresent(MaterialInstanceDO::getInstanceName, reqVO.getInstanceName())
                .likeIfPresent(MaterialInstanceDO::getBarcode, reqVO.getBarcode())
                .eqIfPresent(MaterialInstanceDO::getContainerTypeId, reqVO.getContainerTypeId())
                .eqIfPresent(MaterialInstanceDO::getContainerTypeCode, reqVO.getContainerTypeCode())
                .eqIfPresent(MaterialInstanceDO::getParentInstanceId, reqVO.getParentInstanceId())
                .eqIfPresent(MaterialInstanceDO::getParentPositionCode, reqVO.getParentPositionCode())
                .likeIfPresent(MaterialInstanceDO::getInstancePath, reqVO.getInstancePath())
                .eqIfPresent(MaterialInstanceDO::getRootInstanceId, reqVO.getRootInstanceId())
                .eqIfPresent(MaterialInstanceDO::getRootSlotId, reqVO.getRootSlotId())
                .eqIfPresent(MaterialInstanceDO::getRootSlotCode, reqVO.getRootSlotCode())
                .eqIfPresent(MaterialInstanceDO::getContentDefId, reqVO.getContentDefId())
                .eqIfPresent(MaterialInstanceDO::getContentDefCode, reqVO.getContentDefCode())
                .eqIfPresent(MaterialInstanceDO::getContentType, reqVO.getContentType())
                .eqIfPresent(MaterialInstanceDO::getSampleId, reqVO.getSampleId())
                .eqIfPresent(MaterialInstanceDO::getInstanceStatus, reqVO.getInstanceStatus())
                .betweenIfPresent(MaterialInstanceDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(MaterialInstanceDO::getId));
    }

    default MaterialInstanceDO selectByInstanceCode(String instanceCode) {
        return selectOne(MaterialInstanceDO::getInstanceCode, instanceCode);
    }


    default List<MaterialInstanceDO> selectListBySampleId(String sampleId) {
        return selectList(new LambdaQueryWrapperX<MaterialInstanceDO>()
                .eq(MaterialInstanceDO::getSampleId, sampleId)
                .orderByDesc(MaterialInstanceDO::getId));
    }

    /**
     * 按执行号查仍处于 RESERVED 或同属该执行的 IN_USE（有界，按 id 升序）。
     */
    default List<MaterialInstanceDO> selectOwnedByExecutionId(String executionId, int limit) {
        int size = Math.max(1, Math.min(limit, 500));
        return selectList(new LambdaQueryWrapperX<MaterialInstanceDO>()
                .eq(MaterialInstanceDO::getReservedByExecutionId, executionId)
                .in(MaterialInstanceDO::getInstanceStatus,
                        java.util.List.of("RESERVED", "IN_USE"))
                .orderByAsc(MaterialInstanceDO::getId)
                .last("LIMIT " + size));
    }

    /**
     * 过期预留扫描候选：RESERVED 且 reserved_until <= now（有界）。
     */
    default List<MaterialInstanceDO> selectExpiredReserved(java.time.LocalDateTime now, int limit) {
        int size = Math.max(1, Math.min(limit, 200));
        return selectList(new LambdaQueryWrapperX<MaterialInstanceDO>()
                .eq(MaterialInstanceDO::getInstanceStatus, "RESERVED")
                .le(MaterialInstanceDO::getReservedUntil, now)
                .orderByAsc(MaterialInstanceDO::getReservedUntil)
                .orderByAsc(MaterialInstanceDO::getId)
                .last("LIMIT " + size));
    }

    /**
     * 统计某样本下可用（AVAILABLE）且无归属的实例数量。
     */
    default long countAvailableBySampleId(String sampleId) {
        return selectCount(new LambdaQueryWrapperX<MaterialInstanceDO>()
                .eq(MaterialInstanceDO::getSampleId, sampleId)
                .eq(MaterialInstanceDO::getInstanceStatus, "AVAILABLE")
                .isNull(MaterialInstanceDO::getReservationId));
    }

    /**
     * 统计内容物可用实例数。
     */
    default long countAvailableByContentDefCode(String contentDefCode) {
        return selectCount(new LambdaQueryWrapperX<MaterialInstanceDO>()
                .eq(MaterialInstanceDO::getContentDefCode, contentDefCode)
                .eq(MaterialInstanceDO::getInstanceStatus, "AVAILABLE")
                .isNull(MaterialInstanceDO::getReservationId));
    }

    /**
     * 统计容器类型可用实例数（空容器或任意该类型 AVAILABLE）。
     */
    default long countAvailableByContainerTypeCode(String containerTypeCode) {
        return selectCount(new LambdaQueryWrapperX<MaterialInstanceDO>()
                .eq(MaterialInstanceDO::getContainerTypeCode, containerTypeCode)
                .eq(MaterialInstanceDO::getInstanceStatus, "AVAILABLE")
                .isNull(MaterialInstanceDO::getReservationId));
    }

    /**
     * 锁住一个容器下的全部子实例，按主键顺序加锁，避免两笔按容器消耗交叉锁死。
     * 必须在事务内调用，挑选发生在加锁之后。
     */
    default List<MaterialInstanceDO> selectChildrenForUpdate(Long parentInstanceId) {
        return selectList(new LambdaQueryWrapperX<MaterialInstanceDO>()
                .eq(MaterialInstanceDO::getParentInstanceId, parentInstanceId)
                .orderByAsc(MaterialInstanceDO::getId)
                .last("FOR UPDATE"));
    }
}
