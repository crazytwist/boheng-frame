package cn.boheng.frame.module.wms.service.movement;

import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.module.wms.controller.admin.movement.vo.MaterialMovementPageReqVO;
import cn.boheng.frame.module.wms.dal.dataobject.movement.MaterialMovementDO;

import java.util.List;

/**
 * 物料流水 Service 接口
 *
 * ⚠️ 流水只增不改：只提供写入与查询，不提供任何修改 / 删除能力。
 *
 * @author yinan
 */
public interface MaterialMovementService {

    /**
     * 写入一条流水。
     * <p>
     * ⚠️ 调用方必须保证本方法与引起该流水的业务变更处于**同一个事务**，
     * 否则会出现「位置变了但没流水」的不一致。
     *
     * @param movement 流水（调用方需填好主体、位置、状态、数量等字段）
     * @return 流水编号
     */
    Long recordMovement(MaterialMovementDO movement);

    /**
     * 获得流水分页
     */
    PageResult<MaterialMovementDO> getMovementPage(MaterialMovementPageReqVO pageReqVO);

    /**
     * 获得某实例的完整轨迹（按业务时间正序）
     */
    List<MaterialMovementDO> getMovementListByInstance(Long instanceId);

    /**
     * 获得某顶层容器整树的流水（按业务时间正序）
     */
    List<MaterialMovementDO> getMovementListByRootInstance(Long rootInstanceId);

    /**
     * 获得某槽位的最近流水（源或目标任一命中），按业务时间倒序
     *
     * @param slotId 槽位编号
     * @param limit  条数（为空取 10，最大 200）
     */
    List<MaterialMovementDO> getRecentMovementListBySlot(Long slotId, Integer limit);
}
