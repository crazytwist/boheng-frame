package cn.boheng.frame.module.wms.dal.mysql.container;

import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.framework.mybatis.core.mapper.BaseMapperX;
import cn.boheng.frame.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.boheng.frame.module.wms.controller.admin.container.vo.ContainerTypePageReqVO;
import cn.boheng.frame.module.wms.dal.dataobject.container.ContainerTypeDO;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

/**
 * 容器类型 Mapper
 *
 * @author yinan
 */
@Mapper
public interface ContainerTypeMapper extends BaseMapperX<ContainerTypeDO> {

    default PageResult<ContainerTypeDO> selectPage(ContainerTypePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ContainerTypeDO>()
                .likeIfPresent(ContainerTypeDO::getTypeCode, reqVO.getTypeCode())
                .likeIfPresent(ContainerTypeDO::getTypeName, reqVO.getTypeName())
                .eqIfPresent(ContainerTypeDO::getCategory, reqVO.getCategory())
                .eqIfPresent(ContainerTypeDO::getHierarchyRole, reqVO.getHierarchyRole())
                .eqIfPresent(ContainerTypeDO::getUsageType, reqVO.getUsageType())
                .eqIfPresent(ContainerTypeDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(ContainerTypeDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(ContainerTypeDO::getId));
    }

    default ContainerTypeDO selectByTypeCode(String typeCode) {
        return selectOne(ContainerTypeDO::getTypeCode, typeCode);
    }
}
