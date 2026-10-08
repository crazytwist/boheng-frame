package cn.boheng.frame.module.wms.controller.admin.content;

import cn.boheng.frame.framework.common.pojo.CommonResult;
import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.framework.common.util.object.BeanUtils;
import cn.boheng.frame.module.wms.controller.admin.content.vo.ContentDefPageReqVO;
import cn.boheng.frame.module.wms.controller.admin.content.vo.ContentDefRespVO;
import cn.boheng.frame.module.wms.controller.admin.content.vo.ContentDefSaveReqVO;
import cn.boheng.frame.module.wms.dal.dataobject.content.ContentDefDO;
import cn.boheng.frame.module.wms.service.content.ContentDefService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.util.List;

import static cn.boheng.frame.framework.common.pojo.CommonResult.success;

/**
 * 内容物定义 Controller
 *
 * @author yinan
 */
@Tag(name = "管理后台 - 内容物定义")
@RestController
@RequestMapping("/wms/content-def")
@Validated
public class ContentDefController {

    @Resource
    private ContentDefService contentDefService;


    @PostMapping("/create")
    @Operation(summary = "创建内容物定义")
    @PreAuthorize("@ss.hasPermission('wms:content-def:create')")
    public CommonResult<Long> createContentDef(@Valid @RequestBody ContentDefSaveReqVO createReqVO) {
        return success(contentDefService.createContentDef(createReqVO));
    }


    @PutMapping("/update")
    @Operation(summary = "更新内容物定义")
    @PreAuthorize("@ss.hasPermission('wms:content-def:update')")
    public CommonResult<Boolean> updateContentDef(@Valid @RequestBody ContentDefSaveReqVO updateReqVO) {
        contentDefService.updateContentDef(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除内容物定义")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('wms:content-def:delete')")
    public CommonResult<Boolean> deleteContentDef(@RequestParam("id") Long id) {
        contentDefService.deleteContentDef(id);
        return success(true);
    }

    @DeleteMapping("/delete-list")
    @Operation(summary = "批量删除内容物定义")
    @Parameter(name = "ids", description = "编号列表", required = true)
    @PreAuthorize("@ss.hasPermission('wms:content-def:delete')")
    public CommonResult<Boolean> deleteContentDefList(@RequestParam("ids") List<Long> ids) {
        contentDefService.deleteContentDefList(ids);
        return success(true);
    }


    @GetMapping("/page")
    @Operation(summary = "获得内容物定义分页")
    @PreAuthorize("@ss.hasPermission('wms:content-def:query')")
    public CommonResult<PageResult<ContentDefRespVO>> getContentDefPage(@Valid ContentDefPageReqVO pageReqVO) {
        PageResult<ContentDefDO> pageResult = contentDefService.getContentDefPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, ContentDefRespVO.class));
    }

    @GetMapping("/get")
    @Operation(summary = "获得内容物定义")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('wms:content-def:query')")
    public CommonResult<ContentDefRespVO> getContentDef(@RequestParam("id") Long id) {
        ContentDefDO contentDef = contentDefService.getContentDef(id);
        return success(BeanUtils.toBean(contentDef, ContentDefRespVO.class));
    }


    @GetMapping("/list")
    @Operation(summary = "获得内容物定义列表")
    @PreAuthorize("@ss.hasPermission('wms:content-def:query')")
    public CommonResult<List<ContentDefRespVO>> getContentDefList() {
        List<ContentDefDO> list = contentDefService.getContentDefList();
        return success(BeanUtils.toBean(list, ContentDefRespVO.class));
    }
}
