package cn.boheng.frame.module.device.service.codec;

import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.module.device.controller.admin.codec.vo.DeviceCodecPageReqVO;
import cn.boheng.frame.module.device.controller.admin.codec.vo.DeviceCodecPreviewReqVO;
import cn.boheng.frame.module.device.controller.admin.codec.vo.DeviceCodecSaveReqVO;
import cn.boheng.frame.module.device.dal.dataobject.codec.DeviceCodecDO;
import cn.hutool.json.JSONObject;

import java.util.List;

/**
 * 解析规则服务
 */
public interface DeviceCodecService {

    /**
     * 创建解析规则
     */
    Long createCodec(DeviceCodecSaveReqVO createReqVO);

    /**
     * 更新解析规则
     */
    void updateCodec(DeviceCodecSaveReqVO updateReqVO);

    /**
     * 删除解析规则
     */
    void deleteCodec(Long id);

    /**
     * 获得解析规则
     */
    DeviceCodecDO getCodec(Long id);

    /**
     * 按规则编码获得解析规则
     */
    DeviceCodecDO getByCode(String codecCode);

    /**
     * 解析规则分页
     */
    PageResult<DeviceCodecDO> getCodecPage(DeviceCodecPageReqVO pageReqVO);

    /**
     * 获得启用中的解析规则，供动作选择
     */
    List<DeviceCodecDO> getEnabledList();

    /**
     * 用当前表单里的样例报文试解析，不落库
     */
    JSONObject preview(DeviceCodecPreviewReqVO reqVO);

}
