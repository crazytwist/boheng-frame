package cn.boheng.frame.module.device.service.codec;

import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.framework.common.util.object.BeanUtils;
import cn.boheng.frame.module.device.controller.admin.codec.vo.DeviceCodecPageReqVO;
import cn.boheng.frame.module.device.controller.admin.codec.vo.DeviceCodecPreviewReqVO;
import cn.boheng.frame.module.device.controller.admin.codec.vo.DeviceCodecSaveReqVO;
import cn.boheng.frame.module.device.dal.dataobject.codec.DeviceCodecDO;
import cn.boheng.frame.module.device.dal.mysql.codec.DeviceCodecMapper;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.Resource;
import java.util.List;

import static cn.boheng.frame.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.boheng.frame.module.device.enums.ErrorCodeConstants.DEVICE_CODEC_CODE_DUPLICATE;
import static cn.boheng.frame.module.device.enums.ErrorCodeConstants.DEVICE_CODEC_NOT_EXISTS;
import static cn.boheng.frame.module.device.enums.ErrorCodeConstants.DEVICE_CODEC_PARSE_FAILED;
/**
 * 解析规则服务实现
 */

@Service
@Validated
public class DeviceCodecServiceImpl implements DeviceCodecService {
    /**
     * 解析规则表
     */

    @Resource
    private DeviceCodecMapper deviceCodecMapper;
    /**
     * 创建解析规则
     */

    @Override
    public Long createCodec(DeviceCodecSaveReqVO createReqVO) {
        normalize(createReqVO);
        validateCodeUnique(null, createReqVO.getCodecCode());
        DeviceCodecDO row = BeanUtils.toBean(createReqVO, DeviceCodecDO.class);
        deviceCodecMapper.insert(row);
        return row.getId();
    }
    /**
     * 更新解析规则
     */

    @Override
    public void updateCodec(DeviceCodecSaveReqVO updateReqVO) {
        validateExists(updateReqVO.getId());
        normalize(updateReqVO);
        validateCodeUnique(updateReqVO.getId(), updateReqVO.getCodecCode());
        deviceCodecMapper.updateById(BeanUtils.toBean(updateReqVO, DeviceCodecDO.class));
    }
    /**
     * 删除解析规则
     */

    @Override
    public void deleteCodec(Long id) {
        validateExists(id);
        deviceCodecMapper.deleteById(id);
    }
    /**
     * 获得解析规则
     */

    @Override
    public DeviceCodecDO getCodec(Long id) {
        return deviceCodecMapper.selectById(id);
    }
    /**
     * 按规则编码获得解析规则
     */

    @Override
    public DeviceCodecDO getByCode(String codecCode) {
        if (StrUtil.isBlank(codecCode)) {
            return null;
        }
        return deviceCodecMapper.selectByCodecCode(codecCode);
    }
    /**
     * 解析规则分页
     */

    @Override
    public PageResult<DeviceCodecDO> getCodecPage(DeviceCodecPageReqVO pageReqVO) {
        return deviceCodecMapper.selectPage(pageReqVO);
    }
    /**
     * 获得启用中的解析规则，供动作选择
     */

    @Override
    public List<DeviceCodecDO> getEnabledList() {
        return deviceCodecMapper.selectEnabledList();
    }
    /**
     * 用当前表单里的样例报文试解析，不落库
     */

    @Override
    public JSONObject preview(DeviceCodecPreviewReqVO reqVO) {
        String type = StrUtil.blankToDefault(reqVO.getParseType(), "JSON").trim().toUpperCase();
        if (!"JSON".equals(type) && !"REGEX".equals(type)) {
            throw exception(DEVICE_CODEC_PARSE_FAILED, "只支持 JSON 和正则");
        }
        return DeviceCodecParser.parse(type, reqVO.getFieldMapping(), reqVO.getRegexPattern(), reqVO.getSampleRaw());
    }

    /**
     * 整理编码和解析类型。只允许 JSON 和正则
     */
    private void normalize(DeviceCodecSaveReqVO reqVO) {
        reqVO.setCodecCode(reqVO.getCodecCode().trim());
        reqVO.setParseType(reqVO.getParseType().trim().toUpperCase());
        if (!"JSON".equals(reqVO.getParseType()) && !"REGEX".equals(reqVO.getParseType())) {
            throw exception(DEVICE_CODEC_PARSE_FAILED, "只支持 JSON 和正则");
        }
        if (reqVO.getStatus() == null) {
            reqVO.setStatus(0);
        }
    }

    /**
     * 校验解析规则存在
     */
    private void validateExists(Long id) {
        if (id == null || deviceCodecMapper.selectById(id) == null) {
            throw exception(DEVICE_CODEC_NOT_EXISTS);
        }
    }

    /**
     * 校验解析规则编码不重复
     */
    private void validateCodeUnique(Long id, String codecCode) {
        DeviceCodecDO existing = deviceCodecMapper.selectByCodecCode(codecCode);
        if (existing == null) {
            return;
        }
        if (id == null || !existing.getId().equals(id)) {
            throw exception(DEVICE_CODEC_CODE_DUPLICATE, codecCode);
        }
    }

}
