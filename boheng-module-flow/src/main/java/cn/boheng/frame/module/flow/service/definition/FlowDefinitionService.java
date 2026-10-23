package cn.boheng.frame.module.flow.service.definition;

import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.module.flow.controller.admin.definition.vo.FlowDefinitionCreateReqVO;
import cn.boheng.frame.module.flow.controller.admin.definition.vo.FlowDefinitionPageReqVO;
import cn.boheng.frame.module.flow.controller.admin.definition.vo.FlowDefinitionRespVO;
import cn.boheng.frame.module.flow.controller.admin.definition.vo.FlowDefinitionSaveReqVO;
import cn.boheng.frame.module.flow.controller.admin.definition.vo.FlowIssueRespVO;
import jakarta.validation.Valid;

import java.util.List;

/**
 * 流程定义。负责草稿、发布和修订，不负责把流程跑起来。
 */
public interface FlowDefinitionService {

    /**
     * 创建第 1 版草稿。
     *
     * @return 新版本编号
     */
    Long createDefinition(@Valid FlowDefinitionCreateReqVO reqVO);

    /**
     * 覆盖保存草稿的名称和说明。步骤在流程步骤里单独关联。
     */
    void saveDefinition(@Valid FlowDefinitionSaveReqVO reqVO);

    /**
     * 删除草稿及其步骤。已发布的版本不能删。
     */
    void deleteDefinition(Long id);

    /**
     * 取一个版本。带步骤。
     */
    FlowDefinitionRespVO getDefinition(Long id);

    /**
     * 分页。每行带关联的步骤数，不带步骤正文。
     */
    PageResult<FlowDefinitionRespVO> getDefinitionPage(FlowDefinitionPageReqVO reqVO);

    /**
     * 下拉框。关联页用来选择流程版本。
     */
    List<FlowDefinitionRespVO> getDefinitionSimpleList();

    /**
     * 按库里已经关联的步骤检查能不能发布，不写库。
     */
    List<FlowIssueRespVO> validateDefinition(Long id);

    /**
     * 把草稿收成已发布版本。图不合法时整次发布取消。
     */
    void publishDefinition(Long id);

    /**
     * 已发布的版本不再给新运行选用。已有指纹保留。
     */
    void retireDefinition(Long id);

    /**
     * 将已发布或已停用的流程版本退回草稿。
     */
    void rollbackDraft(Long id);

    /**
     * 从已发布或已停用的版本复制出下一版草稿。
     *
     * @return 新草稿编号
     */
    Long reviseDefinition(Long id);

}
