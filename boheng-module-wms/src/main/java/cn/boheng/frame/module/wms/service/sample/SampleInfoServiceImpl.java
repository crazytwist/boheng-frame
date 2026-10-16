package cn.boheng.frame.module.wms.service.sample;

import cn.hutool.core.util.StrUtil;
import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.framework.common.util.object.BeanUtils;
import cn.boheng.frame.module.wms.controller.admin.sample.vo.SampleInfoPageReqVO;
import cn.boheng.frame.module.wms.controller.admin.sample.vo.SampleInfoSaveReqVO;
import cn.boheng.frame.module.wms.dal.dataobject.instance.MaterialInstanceDO;
import cn.boheng.frame.module.wms.dal.dataobject.sample.SampleInfoDO;
import cn.boheng.frame.module.wms.dal.mysql.instance.MaterialInstanceMapper;
import cn.boheng.frame.module.wms.dal.mysql.sample.SampleInfoMapper;
import cn.boheng.frame.module.wms.enums.SampleStatusEnum;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.google.common.annotations.VisibleForTesting;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static cn.boheng.frame.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.boheng.frame.module.wms.enums.ErrorCodeConstants.SAMPLE_DERIVE_TYPE_INVALID;
import static cn.boheng.frame.module.wms.enums.ErrorCodeConstants.SAMPLE_HAS_INSTANCES;
import static cn.boheng.frame.module.wms.enums.ErrorCodeConstants.SAMPLE_ID_DUPLICATE;
import static cn.boheng.frame.module.wms.enums.ErrorCodeConstants.SAMPLE_NOT_EXISTS;
import static cn.boheng.frame.module.wms.enums.ErrorCodeConstants.SAMPLE_STATUS_TRANSITION_INVALID;

/**
 * 样本 Service 实现类
 *
 * @author yinan
 */
@Service
@Validated
public class SampleInfoServiceImpl implements SampleInfoService {

    /**
     * 原始样本的衍生类型
     */
    private static final String DERIVE_ORIGINAL = "ORIGINAL";

    /**
     * 合法衍生类型。衍生样本（parentSampleId 非空）必须落在其中，
     * 原始样本强制 ORIGINAL，二者互斥保证谱系字段一致。
     */
    private static final Set<String> DERIVE_TYPES = Set.of("SPLIT", "ALIQUOT", "MERGE", "ADD_REAGENT");

    /**
     * 样本状态合法流转表（键=当前态，值=可流转到的目标态集合）。
     * 主线 REGISTERED → LOADED → IN_PROCESS → COMPLETED → ARCHIVED；
     * IN_PROCESS 分支 SPLIT / REJECTED / LOST；LOST 从装载前任意态均可；
     * 终态 ARCHIVED / REJECTED / LOST 不可再流转。
     */
    private static final Map<String, Set<String>> STATUS_TRANSITIONS = Map.of(
            "REGISTERED", Set.of("LOADED", "REJECTED", "LOST"),
            "LOADED", Set.of("IN_PROCESS", "REJECTED", "LOST"),
            "IN_PROCESS", Set.of("COMPLETED", "SPLIT", "REJECTED", "LOST"),
            "SPLIT", Set.of("COMPLETED"),
            "COMPLETED", Set.of("ARCHIVED"),
            "ARCHIVED", Set.of(),
            "REJECTED", Set.of(),
            "LOST", Set.of());

    @Resource
    private SampleInfoMapper sampleInfoMapper;

    @Resource
    private MaterialInstanceMapper materialInstanceMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createSample(SampleInfoSaveReqVO createReqVO) {
        // 1. 校验样本编号唯一性
        validateSampleIdUnique(createReqVO.getId(), createReqVO.getSampleId());

        // 2. 组装样本
        SampleInfoDO sample = BeanUtils.toBean(createReqVO, SampleInfoDO.class);

        // 3. 谱系冗余计算：原始样本 root=自身、generation=0、deriveType=ORIGINAL；衍生样本沿父样本累加
        if (StrUtil.isBlank(sample.getParentSampleId())) {
            sample.setRootSampleId(sample.getSampleId());
            sample.setGeneration(sample.getGeneration() == null ? 0 : sample.getGeneration());
            // 原始样本的衍生类型无条件固定为 ORIGINAL，不接受前端透传
            sample.setDeriveType(DERIVE_ORIGINAL);
        } else {
            // 衍生样本：衍生类型必须落在合法集合（非空、非 ORIGINAL）
            if (StrUtil.isBlank(sample.getDeriveType()) || !DERIVE_TYPES.contains(sample.getDeriveType())) {
                throw exception(SAMPLE_DERIVE_TYPE_INVALID);
            }
            SampleInfoDO parent = sampleInfoMapper.selectBySampleId(sample.getParentSampleId());
            if (parent == null) {
                throw exception(SAMPLE_NOT_EXISTS);
            }
            sample.setRootSampleId(parent.getRootSampleId());
            sample.setGeneration((parent.getGeneration() == null ? 0 : parent.getGeneration()) + 1);
        }

        // 4. 状态缺省为「已登记」
        if (StrUtil.isBlank(sample.getStatus())) {
            sample.setStatus(SampleStatusEnum.REGISTERED.name());
        }
        // 5. 来源缺省为「手动」
        if (StrUtil.isBlank(sample.getSource())) {
            sample.setSource("MANUAL");
        }

        sampleInfoMapper.insert(sample);
        return sample.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSample(SampleInfoSaveReqVO updateReqVO) {
        // 校验存在
        SampleInfoDO oldSample = validateSampleExists(updateReqVO.getId());
        // 校验样本编号唯一性
        validateSampleIdUnique(updateReqVO.getId(), updateReqVO.getSampleId());

        SampleInfoDO updateObj = BeanUtils.toBean(updateReqVO, SampleInfoDO.class);
        // 谱系冗余字段不接受前端覆盖，保持原值（谱系只能经「登记」的父样本链路产生）
        updateObj.setParentSampleId(oldSample.getParentSampleId());
        updateObj.setRootSampleId(oldSample.getRootSampleId());
        updateObj.setGeneration(oldSample.getGeneration());
        updateObj.setDeriveType(oldSample.getDeriveType());
        sampleInfoMapper.updateById(updateObj);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSample(Long id) {
        SampleInfoDO sample = validateSampleExists(id);
        // 已生成实例的样本不允许删除，否则实例的 sample_id 悬空、详情反查查不到样本
        List<MaterialInstanceDO> instances = materialInstanceMapper.selectListBySampleId(sample.getSampleId());
        if (!instances.isEmpty()) {
            throw exception(SAMPLE_HAS_INSTANCES, sample.getSampleId(), instances.size());
        }
        sampleInfoMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSampleStatus(String sampleId, String status, String executionId, String nodeId) {
        SampleInfoDO sample = sampleInfoMapper.selectBySampleId(sampleId);
        if (sample == null) {
            throw exception(SAMPLE_NOT_EXISTS);
        }
        // 有目标状态时校验流转合法性；空目标 = 只更新流程关联字段
        if (StrUtil.isNotBlank(status)) {
            checkStatusTransition(sample.getStatus(), status, sampleId);
        }
        if (StrUtil.isBlank(status) && StrUtil.isBlank(executionId) && StrUtil.isBlank(nodeId)) {
            return;
        }
        LambdaUpdateWrapper<SampleInfoDO> update = new LambdaUpdateWrapper<SampleInfoDO>()
                .eq(SampleInfoDO::getId, sample.getId());
        if (StrUtil.isNotBlank(status)) {
            update.set(SampleInfoDO::getStatus, status);
        }
        // 流程关联：传了才更新，传空不覆盖（允许「脱离流程」或「只登记节点」）
        if (StrUtil.isNotBlank(executionId)) {
            update.set(SampleInfoDO::getCurrentExecutionId, executionId);
        }
        if (StrUtil.isNotBlank(nodeId)) {
            update.set(SampleInfoDO::getCurrentNodeId, nodeId);
        }
        sampleInfoMapper.update(null, update);
    }

    @Override
    public SampleInfoDO getSample(Long id) {
        return sampleInfoMapper.selectById(id);
    }

    @Override
    public SampleInfoDO getSampleBySampleId(String sampleId) {
        return sampleInfoMapper.selectBySampleId(sampleId);
    }

    @Override
    public PageResult<SampleInfoDO> getSamplePage(SampleInfoPageReqVO pageReqVO) {
        return sampleInfoMapper.selectPage(pageReqVO);
    }

    @Override
    public List<SampleInfoDO> getSampleList() {
        return sampleInfoMapper.selectList();
    }

    // ==================== 校验 ====================

    @VisibleForTesting
    SampleInfoDO validateSampleExists(Long id) {
        SampleInfoDO sample = sampleInfoMapper.selectById(id);
        if (sample == null) {
            throw exception(SAMPLE_NOT_EXISTS);
        }
        return sample;
    }

    @VisibleForTesting
    void validateSampleIdUnique(Long id, String sampleId) {
        if (StrUtil.isBlank(sampleId)) {
            return;
        }
        SampleInfoDO sample = sampleInfoMapper.selectBySampleId(sampleId);
        if (sample == null) {
            return;
        }
        if (id == null || !sample.getId().equals(id)) {
            throw exception(SAMPLE_ID_DUPLICATE, sampleId);
        }
    }

    /**
     * 状态流转合法性校验。同态（目标 = 当前）视为幂等放行，用于只更新流程关联字段；
     * 跨态必须落在 {@link #STATUS_TRANSITIONS} 的合法集合里，杜绝任意态互跳。
     */
    private void checkStatusTransition(String from, String to, String sampleId) {
        if (from == null || from.equals(to)) {
            return;
        }
        Set<String> allowed = STATUS_TRANSITIONS.getOrDefault(from, Set.of());
        if (!allowed.contains(to)) {
            throw exception(SAMPLE_STATUS_TRANSITION_INVALID, sampleId, from, to);
        }
    }
}
