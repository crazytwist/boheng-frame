package cn.boheng.frame.module.wms.service.movement;

import cn.hutool.core.util.StrUtil;
import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.framework.security.core.util.SecurityFrameworkUtils;
import cn.boheng.frame.module.wms.controller.admin.movement.vo.MaterialMovementPageReqVO;
import cn.boheng.frame.module.wms.dal.dataobject.movement.MaterialMovementDO;
import cn.boheng.frame.module.wms.dal.mysql.movement.MaterialMovementMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

import static cn.boheng.frame.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.boheng.frame.module.wms.enums.ErrorCodeConstants.MOVEMENT_IDEMPOTENT_DUPLICATE;

/**
 * 物料流水 Service 实现类
 *
 * @author yinan
 */
@Service
@Validated
public class MaterialMovementServiceImpl implements MaterialMovementService {

    /**
     * 操作者类型：人工
     */
    public static final String OPERATOR_TYPE_USER = "USER";

    /**
     * 业务来源：手工
     */
    public static final String BIZ_SOURCE_MANUAL = "MANUAL";

    @Resource
    private MaterialMovementMapper materialMovementMapper;

    @Override
    public Long recordMovement(MaterialMovementDO movement) {
        // 兜底填充：操作时间 / 操作人 / 操作者类型 / 业务来源
        if (movement.getOperateTime() == null) {
            movement.setOperateTime(LocalDateTime.now());
        }
        if (StrUtil.isBlank(movement.getOperatorType())) {
            movement.setOperatorType(OPERATOR_TYPE_USER);
        }
        if (StrUtil.isBlank(movement.getOperator())) {
            Long loginUserId = SecurityFrameworkUtils.getLoginUserId();
            movement.setOperator(loginUserId == null ? null : String.valueOf(loginUserId));
        }
        if (StrUtil.isBlank(movement.getBizSource())) {
            movement.setBizSource(BIZ_SOURCE_MANUAL);
        }
        // 流水只增不改：这里只有 insert，没有 update / delete
        try {
            materialMovementMapper.insert(movement);
        } catch (DuplicateKeyException e) {
            // 由唯一索引 uk_idempotent 兜底：外部模块重复投递同一次操作时，只生效一次。
            // 抛出后整个事务回滚，业务变更（落位 / 消耗）也随之撤销，不会出现半执行状态。
            if (StrUtil.containsIgnoreCase(e.getMessage(), "uk_idempotent")) {
                throw exception(MOVEMENT_IDEMPOTENT_DUPLICATE, movement.getIdempotentKey());
            }
            throw e;
        }
        return movement.getId();
    }

    @Override
    public PageResult<MaterialMovementDO> getMovementPage(MaterialMovementPageReqVO pageReqVO) {
        return materialMovementMapper.selectPage(pageReqVO);
    }

    @Override
    public List<MaterialMovementDO> getMovementListByInstance(Long instanceId) {
        return materialMovementMapper.selectListByInstanceId(instanceId);
    }

    @Override
    public List<MaterialMovementDO> getMovementListByRootInstance(Long rootInstanceId) {
        return materialMovementMapper.selectListByRootInstanceId(rootInstanceId);
    }

    @Override
    public List<MaterialMovementDO> getRecentMovementListBySlot(Long slotId, Integer limit) {
        if (slotId == null) {
            return List.of();
        }
        return materialMovementMapper.selectListBySlotId(slotId, limit);
    }
}
