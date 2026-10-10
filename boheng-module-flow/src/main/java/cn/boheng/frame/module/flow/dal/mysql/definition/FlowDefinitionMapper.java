package cn.boheng.frame.module.flow.dal.mysql.definition;

import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.framework.mybatis.core.mapper.BaseMapperX;
import cn.boheng.frame.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.boheng.frame.module.flow.controller.admin.definition.vo.FlowDefinitionPageReqVO;
import cn.boheng.frame.module.flow.dal.dataobject.definition.FlowDefinitionDO;
import cn.boheng.frame.module.flow.enums.FlowConstants;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 流程版本表。
 */
@Mapper
public interface FlowDefinitionMapper extends BaseMapperX<FlowDefinitionDO> {

    /**
     * 按编码、名称、状态分页。新版本排在前面。
     */
    default PageResult<FlowDefinitionDO> selectPage(FlowDefinitionPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<FlowDefinitionDO>()
                .likeIfPresent(FlowDefinitionDO::getPipelineKey, reqVO.getPipelineKey())
                .likeIfPresent(FlowDefinitionDO::getName, reqVO.getName())
                .eqIfPresent(FlowDefinitionDO::getStatus, reqVO.getStatus())
                .orderByDesc(FlowDefinitionDO::getId));
    }

    /**
     * 这个编码是否已经有任意版本。创建时用来拒绝重复编码。
     */
    default FlowDefinitionDO selectFirstByKey(String pipelineKey) {
        return selectOne(new LambdaQueryWrapperX<FlowDefinitionDO>()
                .eq(FlowDefinitionDO::getPipelineKey, pipelineKey)
                .last("LIMIT 1"));
    }

    /**
     * 这个编码当前的草稿。一个编码同时只允许一份。
     */
    default FlowDefinitionDO selectDraftByKey(String pipelineKey) {
        return selectOne(new LambdaQueryWrapperX<FlowDefinitionDO>()
                .eq(FlowDefinitionDO::getPipelineKey, pipelineKey)
                .eq(FlowDefinitionDO::getStatus, FlowConstants.STATUS_DRAFT)
                .last("LIMIT 1"));
    }

    /**
     * 关联页的流程下拉。版本不多，一次取回。
     */
    default List<FlowDefinitionDO> selectSimpleList() {
        return selectList(new LambdaQueryWrapperX<FlowDefinitionDO>()
                .orderByDesc(FlowDefinitionDO::getId));
    }

    /**
     * 这个编码已经用到的最大版本号。修订时加一。
     */
    default Integer selectMaxVersion(String pipelineKey) {
        FlowDefinitionDO latest = selectOne(new LambdaQueryWrapperX<FlowDefinitionDO>()
                .eq(FlowDefinitionDO::getPipelineKey, pipelineKey)
                .orderByDesc(FlowDefinitionDO::getVersion)
                .last("LIMIT 1"));
        return latest == null || latest.getVersion() == null ? 0 : latest.getVersion();
    }

    /**
     * 某编码当前已发布的最新版。子流程引用不写版本号时用它
     */
    default FlowDefinitionDO selectPublishedLatest(String pipelineKey) {
        return selectOne(new LambdaQueryWrapperX<FlowDefinitionDO>()
                .eq(FlowDefinitionDO::getPipelineKey, pipelineKey)
                .eq(FlowDefinitionDO::getStatus, FlowConstants.STATUS_PUBLISHED)
                .orderByDesc(FlowDefinitionDO::getVersion)
                .last("LIMIT 1"));
    }

    /**
     * 按编码和版本取一条。子流程固定版本引用时用它
     */
    default FlowDefinitionDO selectByKeyAndVersion(String pipelineKey, Integer version) {
        return selectOne(new LambdaQueryWrapperX<FlowDefinitionDO>()
                .eq(FlowDefinitionDO::getPipelineKey, pipelineKey)
                .eq(FlowDefinitionDO::getVersion, version)
                .last("LIMIT 1"));
    }

}
