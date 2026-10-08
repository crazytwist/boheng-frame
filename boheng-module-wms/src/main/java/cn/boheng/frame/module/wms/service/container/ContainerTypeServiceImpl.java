package cn.boheng.frame.module.wms.service.container;

import cn.hutool.core.util.StrUtil;
import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.framework.common.util.object.BeanUtils;
import cn.boheng.frame.module.wms.controller.admin.container.vo.ContainerTypePageReqVO;
import cn.boheng.frame.module.wms.controller.admin.container.vo.ContainerTypeSaveReqVO;
import cn.boheng.frame.module.wms.dal.dataobject.container.ContainerTypeDO;
import cn.boheng.frame.module.wms.dal.mysql.container.ContainerTypeMapper;
import com.google.common.annotations.VisibleForTesting;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.Resource;
import java.util.List;

import static cn.boheng.frame.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.boheng.frame.module.wms.enums.ErrorCodeConstants.*;

/**
 * 容器类型 Service 实现类
 *
 * @author yinan
 */
@Service
@Validated
public class ContainerTypeServiceImpl implements ContainerTypeService {

    @Resource
    private ContainerTypeMapper containerTypeMapper;


    @Override
    public Long createContainerType(ContainerTypeSaveReqVO createReqVO) {
        // 校验容器类型编码的唯一性
        validateTypeCodeUnique(createReqVO.getId(), createReqVO.getTypeCode());

        // 插入
        ContainerTypeDO containerType = BeanUtils.toBean(createReqVO, ContainerTypeDO.class);
        containerTypeMapper.insert(containerType);
        return containerType.getId();
    }


    @Override
    public void updateContainerType(ContainerTypeSaveReqVO updateReqVO) {
        // 校验存在
        validateContainerTypeExists(updateReqVO.getId());
        // 校验容器类型编码的唯一性
        validateTypeCodeUnique(updateReqVO.getId(), updateReqVO.getTypeCode());

        // 更新
        ContainerTypeDO updateObj = BeanUtils.toBean(updateReqVO, ContainerTypeDO.class);
        containerTypeMapper.updateById(updateObj);
    }

    @Override
    public void deleteContainerType(Long id) {
        // 校验存在
        validateContainerTypeExists(id);
        // 删除
        containerTypeMapper.deleteById(id);
    }

    @Override
    public void deleteContainerTypeList(List<Long> ids) {
        containerTypeMapper.deleteBatch(ContainerTypeDO::getId, ids);
    }


    @Override
    public ContainerTypeDO getContainerType(Long id) {
        return containerTypeMapper.selectById(id);
    }

    @Override
    public PageResult<ContainerTypeDO> getContainerTypePage(ContainerTypePageReqVO pageReqVO) {
        return containerTypeMapper.selectPage(pageReqVO);
    }


    @Override
    public List<ContainerTypeDO> getContainerTypeList() {
        return containerTypeMapper.selectList();
    }

    @VisibleForTesting
    void validateContainerTypeExists(Long id) {
        if (containerTypeMapper.selectById(id) == null) {
            throw exception(CONTAINER_TYPE_NOT_EXISTS);
        }
    }


    @VisibleForTesting
    void validateTypeCodeUnique(Long id, String typeCode) {
        if (StrUtil.isBlank(typeCode)) {
            return;
        }
        ContainerTypeDO containerType = containerTypeMapper.selectByTypeCode(typeCode);
        if (containerType == null) {
            return;
        }
        // 如果 id 为空，说明不用比较是否为相同 id 的记录
        if (id == null || !containerType.getId().equals(id)) {
            throw exception(CONTAINER_TYPE_CODE_DUPLICATE, typeCode);
        }
    }
}
