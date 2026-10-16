package cn.boheng.frame.module.wms.controller.admin.sample;

import cn.boheng.frame.framework.common.pojo.CommonResult;
import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.framework.common.util.object.BeanUtils;
import cn.boheng.frame.module.wms.controller.admin.sample.vo.SampleInfoPageReqVO;
import cn.boheng.frame.module.wms.controller.admin.sample.vo.SampleInfoRespVO;
import cn.boheng.frame.module.wms.controller.admin.sample.vo.SampleInfoSaveReqVO;
import cn.boheng.frame.module.wms.dal.dataobject.sample.SampleInfoDO;
import cn.boheng.frame.module.wms.service.sample.SampleInfoService;
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
 * 样本 Controller
 *
 * @author yinan
 */
@Tag(name = "管理后台 - 样本")
@RestController
@RequestMapping("/wms/sample")
@Validated
public class SampleInfoController {

    @Resource
    private SampleInfoService sampleInfoService;

    @PostMapping("/create")
    @Operation(summary = "登记样本")
    @PreAuthorize("@ss.hasPermission('wms:sample:create')")
    public CommonResult<Long> createSample(@Valid @RequestBody SampleInfoSaveReqVO createReqVO) {
        return success(sampleInfoService.createSample(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新样本")
    @PreAuthorize("@ss.hasPermission('wms:sample:update')")
    public CommonResult<Boolean> updateSample(@Valid @RequestBody SampleInfoSaveReqVO updateReqVO) {
        sampleInfoService.updateSample(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除样本")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('wms:sample:delete')")
    public CommonResult<Boolean> deleteSample(@RequestParam("id") Long id) {
        sampleInfoService.deleteSample(id);
        return success(true);
    }

    @GetMapping("/page")
    @Operation(summary = "获得样本分页")
    @PreAuthorize("@ss.hasPermission('wms:sample:query')")
    public CommonResult<PageResult<SampleInfoRespVO>> getSamplePage(@Valid SampleInfoPageReqVO pageReqVO) {
        PageResult<SampleInfoDO> pageResult = sampleInfoService.getSamplePage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, SampleInfoRespVO.class));
    }

    @GetMapping("/get")
    @Operation(summary = "获得样本")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('wms:sample:query')")
    public CommonResult<SampleInfoRespVO> getSample(@RequestParam("id") Long id) {
        SampleInfoDO sample = sampleInfoService.getSample(id);
        return success(BeanUtils.toBean(sample, SampleInfoRespVO.class));
    }

    @GetMapping("/list")
    @Operation(summary = "获得样本列表")
    @PreAuthorize("@ss.hasPermission('wms:sample:query')")
    public CommonResult<List<SampleInfoRespVO>> getSampleList() {
        List<SampleInfoDO> list = sampleInfoService.getSampleList();
        return success(BeanUtils.toBean(list, SampleInfoRespVO.class));
    }
}
