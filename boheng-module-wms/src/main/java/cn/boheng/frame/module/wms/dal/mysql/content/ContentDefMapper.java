package cn.boheng.frame.module.wms.dal.mysql.content;

import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.framework.mybatis.core.mapper.BaseMapperX;
import cn.boheng.frame.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.boheng.frame.module.wms.controller.admin.content.vo.ContentDefPageReqVO;
import cn.boheng.frame.module.wms.dal.dataobject.content.ContentDefDO;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

/**
 * 内容物定义 Mapper
 *
 * @author yinan
 */
@Mapper
public interface ContentDefMapper extends BaseMapperX<ContentDefDO> {

    default PageResult<ContentDefDO> selectPage(ContentDefPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ContentDefDO>()
                .likeIfPresent(ContentDefDO::getContentCode, reqVO.getContentCode())
                .likeIfPresent(ContentDefDO::getContentName, reqVO.getContentName())
                .eqIfPresent(ContentDefDO::getContentType, reqVO.getContentType())
                .eqIfPresent(ContentDefDO::getCasNo, reqVO.getCasNo())
                .eqIfPresent(ContentDefDO::getStorageCond, reqVO.getStorageCond())
                .eqIfPresent(ContentDefDO::getHazardLevel, reqVO.getHazardLevel())
                .eqIfPresent(ContentDefDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(ContentDefDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(ContentDefDO::getId));
    }

    default ContentDefDO selectByContentCode(String contentCode) {
        return selectOne(ContentDefDO::getContentCode, contentCode);
    }
}
