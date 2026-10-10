package cn.boheng.frame.module.flow.service.step;

import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.module.flow.controller.admin.step.vo.FlowStepPageReqVO;
import cn.boheng.frame.module.flow.controller.admin.step.vo.FlowStepRespVO;
import cn.boheng.frame.module.flow.controller.admin.step.vo.FlowStepSaveReqVO;
import cn.boheng.frame.module.flow.dal.dataobject.definition.FlowDefinitionDO;
import cn.boheng.frame.module.flow.dal.dataobject.definition.FlowNodeDO;
import cn.boheng.frame.module.flow.dal.dataobject.step.FlowStepDO;
import cn.boheng.frame.module.flow.dal.mysql.definition.FlowDefinitionMapper;
import cn.boheng.frame.module.flow.dal.mysql.definition.FlowNodeMapper;
import cn.boheng.frame.module.flow.dal.mysql.step.FlowStepMapper;
import cn.boheng.frame.module.flow.enums.FlowConstants;
import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.List;

import static cn.boheng.frame.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.boheng.frame.module.flow.enums.ErrorCodeConstants.FLOW_STEP_DELETE_USED;
import static cn.boheng.frame.module.flow.enums.ErrorCodeConstants.FLOW_STEP_KEY_DUPLICATE;
import static cn.boheng.frame.module.flow.enums.ErrorCodeConstants.FLOW_STEP_LOCKED;
import static cn.boheng.frame.module.flow.enums.ErrorCodeConstants.FLOW_STEP_NOT_EXISTS;

/**
 * 步骤先独立保存。被已发布的流程引用后不能再改，避免已发布版本的设备动作跟着变。
 */
@Service
@Validated
public class FlowStepServiceImpl implements FlowStepService {

    /**
     * 步骤表
     */
    @Resource
    private FlowStepMapper flowStepMapper;

    /**
     * 流程和步骤的关联
     */
    @Resource
    private FlowNodeMapper flowNodeMapper;

    /**
     * 流程版本。用来判断引用它的版本是否已经定稿
     */
    @Resource
    private FlowDefinitionMapper flowDefinitionMapper;

    /**
     * 新建。编码在租户内唯一。
     */
    @Override
    public Long createStep(FlowStepSaveReqVO reqVO) {
        String key = reqVO.getStepKey().trim();
        if (flowStepMapper.selectByStepKey(key) != null) {
            throw exception(FLOW_STEP_KEY_DUPLICATE, key);
        }
        FlowStepDO row = toRow(reqVO, key);
        flowStepMapper.insert(row);
        return row.getId();
    }

    /**
     * 修改名称、设备和等待。编码保持创建时的值。已被定稿流程引用时整条拒绝。
     */
    @Override
    public void updateStep(FlowStepSaveReqVO reqVO) {
        FlowStepDO existing = requireStep(reqVO.getId());
        assertNotLocked(existing.getId(), existing.getStepKey());
        FlowStepDO row = toRow(reqVO, existing.getStepKey());
        row.setId(existing.getId());
        flowStepMapper.updateById(row);
    }

    /**
     * 还有流程引用时不能删，先到流程步骤里解除关联。
     */
    @Override
    public void deleteStep(Long id) {
        FlowStepDO existing = requireStep(id);
        if (!flowNodeMapper.selectByStepId(id).isEmpty()) {
            throw exception(FLOW_STEP_DELETE_USED, existing.getStepKey());
        }
        flowStepMapper.deleteById(id);
    }

    /**
     * 详情。
     */
    @Override
    public FlowStepRespVO getStep(Long id) {
        return toResp(requireStep(id));
    }

    /**
     * 分页。
     */
    @Override
    public PageResult<FlowStepRespVO> getStepPage(FlowStepPageReqVO reqVO) {
        PageResult<FlowStepDO> page = flowStepMapper.selectPage(reqVO);
        return new PageResult<>(page.getList().stream().map(this::toResp).toList(), page.getTotal());
    }

    /**
     * 关联页只列出启用的步骤。
     */
    @Override
    public List<FlowStepRespVO> getEnabledStepList() {
        return flowStepMapper.selectEnabledList().stream().map(this::toResp).toList();
    }

    /**
     * 没有这条步骤时抛不存在。
     */
    private FlowStepDO requireStep(Long id) {
        FlowStepDO row = flowStepMapper.selectById(id);
        if (row == null) {
            throw exception(FLOW_STEP_NOT_EXISTS);
        }
        return row;
    }

    /**
     * 已发布或已停用的版本还指着它，就不能改。草稿引用不影响修改。
     */
    private void assertNotLocked(Long stepId, String stepKey) {
        for (FlowNodeDO link : flowNodeMapper.selectByStepId(stepId)) {
            FlowDefinitionDO definition = flowDefinitionMapper.selectById(link.getDefinitionId());
            if (definition != null && !FlowConstants.STATUS_DRAFT.equals(definition.getStatus())) {
                throw exception(FLOW_STEP_LOCKED, stepKey);
            }
        }
    }

    /**
     * 请求收成表行。编码由调用方决定，更新时不采用请求里的新编码。
     */
    private FlowStepDO toRow(FlowStepSaveReqVO reqVO, String stepKey) {
        return FlowStepDO.builder()
                .stepKey(stepKey)
                .name(reqVO.getName().trim())
                .deviceCode(reqVO.getDeviceCode().trim())
                .actionCode(reqVO.getActionCode().trim())
                .timeoutMs(reqVO.getTimeoutSec() * 1000L)
                .maxAttempts(reqVO.getMaxAttempts())
                .remark(StrUtil.trimToNull(reqVO.getRemark()))
                .status(reqVO.getStatus())
                .build();
    }

    /**
     * 毫秒换成秒，给列表和表单用。
     */
    private FlowStepRespVO toResp(FlowStepDO row) {
        FlowStepRespVO resp = new FlowStepRespVO();
        resp.setId(row.getId());
        resp.setStepKey(row.getStepKey());
        resp.setName(row.getName());
        resp.setDeviceCode(row.getDeviceCode());
        resp.setActionCode(row.getActionCode());
        resp.setTimeoutSec(row.getTimeoutMs() == null ? null : (int) (row.getTimeoutMs() / 1000));
        resp.setMaxAttempts(row.getMaxAttempts());
        resp.setRemark(row.getRemark());
        resp.setStatus(row.getStatus());
        resp.setCreateTime(row.getCreateTime());
        return resp;
    }

}
