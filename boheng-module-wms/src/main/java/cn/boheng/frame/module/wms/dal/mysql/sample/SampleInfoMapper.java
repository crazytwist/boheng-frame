package cn.boheng.frame.module.wms.dal.mysql.sample;

import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.framework.mybatis.core.mapper.BaseMapperX;
import cn.boheng.frame.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.boheng.frame.module.wms.controller.admin.sample.vo.SampleInfoPageReqVO;
import cn.boheng.frame.module.wms.dal.dataobject.sample.SampleInfoDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 样本 Mapper
 *
 * @author yinan
 */
@Mapper
public interface SampleInfoMapper extends BaseMapperX<SampleInfoDO> {

    default PageResult<SampleInfoDO> selectPage(SampleInfoPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<SampleInfoDO>()
                .likeIfPresent(SampleInfoDO::getSampleId, reqVO.getSampleId())
                .likeIfPresent(SampleInfoDO::getSampleName, reqVO.getSampleName())
                .eqIfPresent(SampleInfoDO::getParentSampleId, reqVO.getParentSampleId())
                .eqIfPresent(SampleInfoDO::getRootSampleId, reqVO.getRootSampleId())
                .eqIfPresent(SampleInfoDO::getDeriveType, reqVO.getDeriveType())
                .eqIfPresent(SampleInfoDO::getSampleType, reqVO.getSampleType())
                .eqIfPresent(SampleInfoDO::getContentDefId, reqVO.getContentDefId())
                .eqIfPresent(SampleInfoDO::getContainerTypeCode, reqVO.getContainerTypeCode())
                .eqIfPresent(SampleInfoDO::getStatus, reqVO.getStatus())
                .eqIfPresent(SampleInfoDO::getCurrentExecutionId, reqVO.getCurrentExecutionId())
                .eqIfPresent(SampleInfoDO::getBatchNo, reqVO.getBatchNo())
                .eqIfPresent(SampleInfoDO::getOrderNo, reqVO.getOrderNo())
                .betweenIfPresent(SampleInfoDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(SampleInfoDO::getId));
    }

    default SampleInfoDO selectBySampleId(String sampleId) {
        return selectOne(SampleInfoDO::getSampleId, sampleId);
    }
}
