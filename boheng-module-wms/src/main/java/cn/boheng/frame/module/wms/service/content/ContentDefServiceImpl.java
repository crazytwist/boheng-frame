package cn.boheng.frame.module.wms.service.content;

import cn.hutool.core.util.StrUtil;
import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.framework.common.util.object.BeanUtils;
import cn.boheng.frame.module.wms.controller.admin.content.vo.ContentDefPageReqVO;
import cn.boheng.frame.module.wms.controller.admin.content.vo.ContentDefSaveReqVO;
import cn.boheng.frame.module.wms.dal.dataobject.content.ContentDefDO;
import cn.boheng.frame.module.wms.dal.mysql.content.ContentDefMapper;
import com.google.common.annotations.VisibleForTesting;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.Resource;
import java.util.List;

import static cn.boheng.frame.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.boheng.frame.module.wms.enums.ErrorCodeConstants.*;

/**
 * 内容物定义 Service 实现类
 *
 * @author yinan
 */
@Service
@Validated
public class ContentDefServiceImpl implements ContentDefService {

    @Resource
    private ContentDefMapper contentDefMapper;


    @Override
    public Long createContentDef(ContentDefSaveReqVO createReqVO) {
        // 校验内容物编码的唯一性
        validateContentCodeUnique(createReqVO.getId(), createReqVO.getContentCode());

        // 插入
        ContentDefDO contentDef = BeanUtils.toBean(createReqVO, ContentDefDO.class);
        contentDefMapper.insert(contentDef);
        return contentDef.getId();
    }


    @Override
    public void updateContentDef(ContentDefSaveReqVO updateReqVO) {
        // 校验存在
        validateContentDefExists(updateReqVO.getId());
        // 校验内容物编码的唯一性
        validateContentCodeUnique(updateReqVO.getId(), updateReqVO.getContentCode());

        // 更新
        ContentDefDO updateObj = BeanUtils.toBean(updateReqVO, ContentDefDO.class);
        contentDefMapper.updateById(updateObj);
    }

    @Override
    public void deleteContentDef(Long id) {
        // 校验存在
        validateContentDefExists(id);
        // 删除
        contentDefMapper.deleteById(id);
    }

    @Override
    public void deleteContentDefList(List<Long> ids) {
        contentDefMapper.deleteBatch(ContentDefDO::getId, ids);
    }


    @Override
    public ContentDefDO getContentDef(Long id) {
        return contentDefMapper.selectById(id);
    }

    @Override
    public PageResult<ContentDefDO> getContentDefPage(ContentDefPageReqVO pageReqVO) {
        return contentDefMapper.selectPage(pageReqVO);
    }


    @Override
    public List<ContentDefDO> getContentDefList() {
        return contentDefMapper.selectList();
    }

    @VisibleForTesting
    void validateContentDefExists(Long id) {
        if (contentDefMapper.selectById(id) == null) {
            throw exception(CONTENT_DEF_NOT_EXISTS);
        }
    }


    @VisibleForTesting
    void validateContentCodeUnique(Long id, String contentCode) {
        if (StrUtil.isBlank(contentCode)) {
            return;
        }
        ContentDefDO contentDef = contentDefMapper.selectByContentCode(contentCode);
        if (contentDef == null) {
            return;
        }
        // 如果 id 为空，说明不用比较是否为相同 id 的记录
        if (id == null || !contentDef.getId().equals(id)) {
            throw exception(CONTENT_DEF_CODE_DUPLICATE, contentCode);
        }
    }
}
