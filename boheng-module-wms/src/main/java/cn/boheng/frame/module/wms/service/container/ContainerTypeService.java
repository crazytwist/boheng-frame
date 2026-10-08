package cn.boheng.frame.module.wms.service.container;

import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.module.wms.controller.admin.container.vo.ContainerTypePageReqVO;
import cn.boheng.frame.module.wms.controller.admin.container.vo.ContainerTypeSaveReqVO;
import cn.boheng.frame.module.wms.dal.dataobject.container.ContainerTypeDO;

import jakarta.validation.Valid;
import java.util.List;

/**
 * 容器类型 Service 接口
 *
 * @author yinan
 */
public interface ContainerTypeService {


    /**
     * 创建容器类型
     *
     * @param createReqVO 创建信息
     * @return 编号
     */
    Long createContainerType(@Valid ContainerTypeSaveReqVO createReqVO);


    /**
     * 更新容器类型
     *
     * @param updateReqVO 更新信息
     */
    void updateContainerType(@Valid ContainerTypeSaveReqVO updateReqVO);

    /**
     * 删除容器类型
     *
     * @param id 编号
     */
    void deleteContainerType(Long id);

    /**
     * 批量删除容器类型
     *
     * @param ids 编号列表
     */
    void deleteContainerTypeList(List<Long> ids);


    /**
     * 获得容器类型
     *
     * @param id 编号
     * @return 容器类型
     */
    ContainerTypeDO getContainerType(Long id);

    /**
     * 获得容器类型分页
     *
     * @param pageReqVO 分页查询
     * @return 容器类型分页
     */
    PageResult<ContainerTypeDO> getContainerTypePage(ContainerTypePageReqVO pageReqVO);


    /**
     * 获得容器类型列表
     *
     * @return 容器类型列表
     */
    List<ContainerTypeDO> getContainerTypeList();
}
