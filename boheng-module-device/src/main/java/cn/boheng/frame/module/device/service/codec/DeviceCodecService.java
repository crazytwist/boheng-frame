package cn.boheng.frame.module.device.service.codec;

import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.module.device.controller.admin.codec.vo.DeviceCodecPageReqVO;
import cn.boheng.frame.module.device.controller.admin.codec.vo.DeviceCodecPreviewReqVO;
import cn.boheng.frame.module.device.controller.admin.codec.vo.DeviceCodecSaveReqVO;
import cn.boheng.frame.module.device.dal.dataobject.codec.DeviceCodecDO;
import cn.hutool.json.JSONObject;

import java.util.List;

public interface DeviceCodecService {

    Long createCodec(DeviceCodecSaveReqVO createReqVO);

    void updateCodec(DeviceCodecSaveReqVO updateReqVO);

    void deleteCodec(Long id);

    DeviceCodecDO getCodec(Long id);

    DeviceCodecDO getByCode(String codecCode);

    PageResult<DeviceCodecDO> getCodecPage(DeviceCodecPageReqVO pageReqVO);

    List<DeviceCodecDO> getEnabledList();

    JSONObject preview(DeviceCodecPreviewReqVO reqVO);

}
