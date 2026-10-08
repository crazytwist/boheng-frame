package cn.boheng.frame.module.wms.dal.mysql.movement;

import cn.hutool.core.util.StrUtil;
import cn.boheng.frame.framework.common.pojo.PageParam;
import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.framework.mybatis.core.mapper.BaseMapperX;
import cn.boheng.frame.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.boheng.frame.module.wms.controller.admin.movement.vo.MaterialMovementPageReqVO;
import cn.boheng.frame.module.wms.dal.dataobject.movement.MaterialMovementDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 物料流水 Mapper
 *
 * ⚠️ 流水只增不改：本 Mapper 不提供任何 update / delete 的封装方法。
 *
 * @author yinan
 */
@Mapper
public interface MaterialMovementMapper extends BaseMapperX<MaterialMovementDO> {

    /**
     * 「最近流水」默认条数
     */
    int DEFAULT_RECENT_LIMIT = 10;

    /**
     * 单页条数上限（与 PageParam.pageSize 的 @Max(200) 对齐）
     */
    int MAX_PAGE_SIZE = 200;

    default PageResult<MaterialMovementDO> selectPage(MaterialMovementPageReqVO reqVO) {
        LambdaQueryWrapperX<MaterialMovementDO> wrapper = new LambdaQueryWrapperX<MaterialMovementDO>()
                .eqIfPresent(MaterialMovementDO::getMovementType, reqVO.getMovementType())
                .eqIfPresent(MaterialMovementDO::getBizSource, reqVO.getBizSource())
                .eqIfPresent(MaterialMovementDO::getOperationId, reqVO.getOperationId())
                .eqIfPresent(MaterialMovementDO::getInstanceId, reqVO.getInstanceId())
                .likeIfPresent(MaterialMovementDO::getInstanceCode, reqVO.getInstanceCode())
                .eqIfPresent(MaterialMovementDO::getRootInstanceId, reqVO.getRootInstanceId())
                .eqIfPresent(MaterialMovementDO::getFromSlotId, reqVO.getFromSlotId())
                .eqIfPresent(MaterialMovementDO::getToSlotId, reqVO.getToSlotId())
                .eqIfPresent(MaterialMovementDO::getFromZoneCode, reqVO.getFromZoneCode())
                .eqIfPresent(MaterialMovementDO::getToZoneCode, reqVO.getToZoneCode())
                .eqIfPresent(MaterialMovementDO::getContentDefCode, reqVO.getContentDefCode())
                .eqIfPresent(MaterialMovementDO::getOperatorType, reqVO.getOperatorType())
                .eqIfPresent(MaterialMovementDO::getOperator, reqVO.getOperator())
                .eqIfPresent(MaterialMovementDO::getRefType, reqVO.getRefType())
                .eqIfPresent(MaterialMovementDO::getRefId, reqVO.getRefId())
                .betweenIfPresent(MaterialMovementDO::getOperateTime, reqVO.getOperateTime());
        // 「本槽位进出明细」：源或目标任一命中该槽位
        if (reqVO.getSlotId() != null) {
            wrapper.and(w -> w.eq(MaterialMovementDO::getFromSlotId, reqVO.getSlotId())
                    .or().eq(MaterialMovementDO::getToSlotId, reqVO.getSlotId()));
        }
        // 「按区域筛流水」：源或目标任一命中该区域
        if (StrUtil.isNotBlank(reqVO.getZoneCode())) {
            wrapper.and(w -> w.eq(MaterialMovementDO::getFromZoneCode, reqVO.getZoneCode())
                    .or().eq(MaterialMovementDO::getToZoneCode, reqVO.getZoneCode()));
        }
        wrapper.orderByDesc(MaterialMovementDO::getOperateTime)
                .orderByDesc(MaterialMovementDO::getId);
        return selectPage(reqVO, wrapper);
    }

    /**
     * 某实例的完整轨迹（按业务时间正序，用于时间线展示）
     */
    default List<MaterialMovementDO> selectListByInstanceId(Long instanceId) {
        return selectList(new LambdaQueryWrapperX<MaterialMovementDO>()
                .eq(MaterialMovementDO::getInstanceId, instanceId)
                .orderByAsc(MaterialMovementDO::getOperateTime)
                .orderByAsc(MaterialMovementDO::getId));
    }

    /**
     * 某顶层容器整树的流水（根实例编号命中）
     */
    default List<MaterialMovementDO> selectListByRootInstanceId(Long rootInstanceId) {
        return selectList(new LambdaQueryWrapperX<MaterialMovementDO>()
                .eq(MaterialMovementDO::getRootInstanceId, rootInstanceId)
                .orderByAsc(MaterialMovementDO::getOperateTime)
                .orderByAsc(MaterialMovementDO::getId));
    }

    /**
     * 某槽位的最近流水（源或目标任一命中），倒序取前 limit 条
     */
    default List<MaterialMovementDO> selectListBySlotId(Long slotId, Integer limit) {
        // 受 PageParam.pageSize 的 @Max(200) 约束，这里同步夹紧上限
        int size = (limit == null || limit <= 0) ? DEFAULT_RECENT_LIMIT : Math.min(limit, MAX_PAGE_SIZE);
        PageParam pageParam = new PageParam();
        pageParam.setPageNo(1);
        pageParam.setPageSize(size);
        LambdaQueryWrapperX<MaterialMovementDO> wrapper = new LambdaQueryWrapperX<MaterialMovementDO>();
        // 注意：and(Consumer) 的返回值被声明为 LambdaQueryWrapper，不能再赋回 X 类型，故单独调用
        wrapper.and(w -> w.eq(MaterialMovementDO::getFromSlotId, slotId)
                .or().eq(MaterialMovementDO::getToSlotId, slotId));
        wrapper.orderByDesc(MaterialMovementDO::getOperateTime)
                .orderByDesc(MaterialMovementDO::getId);
        return selectPage(pageParam, wrapper).getList();
    }
}
