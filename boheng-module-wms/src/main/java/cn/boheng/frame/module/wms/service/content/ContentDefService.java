package cn.boheng.frame.module.wms.service.content;

import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.module.wms.controller.admin.content.vo.ContentDefPageReqVO;
import cn.boheng.frame.module.wms.controller.admin.content.vo.ContentDefSaveReqVO;
import cn.boheng.frame.module.wms.dal.dataobject.content.ContentDefDO;

import jakarta.validation.Valid;
import java.util.List;

/**
 * 内容物定义 Service 接口
 *
 * @author yinan
 */
public interface ContentDefService {


    /**
     * 创建内容物定义
     *
     * @param createReqVO 创建信息
     * @return 编号
     */
    Long createContentDef(@Valid ContentDefSaveReqVO createReqVO);


    /**
     * 更新内容物定义
     *
     * @param updateReqVO 更新信息
     */
    void updateContentDef(@Valid ContentDefSaveReqVO updateReqVO);

    /**
     * 删除内容物定义
     *
     * @param id 编号
     */
    void deleteContentDef(Long id);

    /**
     * 批量删除内容物定义
     *
     * @param ids 编号列表
     */
    void deleteContentDefList(List<Long> ids);


    /**
     * 获得内容物定义
     *
     * @param id 编号
     * @return 内容物定义
     */
    ContentDefDO getContentDef(Long id);

    /**
     * 获得内容物定义分页
     *
     * @param pageReqVO 分页查询
     * @return 内容物定义分页
     */
    PageResult<ContentDefDO> getContentDefPage(ContentDefPageReqVO pageReqVO);


    /**
     * 获得内容物定义列表
     *
     * @return 内容物定义列表
     */
    List<ContentDefDO> getContentDefList();
}
