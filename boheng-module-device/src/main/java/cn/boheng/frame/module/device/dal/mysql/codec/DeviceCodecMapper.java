package cn.boheng.frame.module.device.dal.mysql.codec;

import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.framework.mybatis.core.mapper.BaseMapperX;
import cn.boheng.frame.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.boheng.frame.module.device.controller.admin.codec.vo.DeviceCodecPageReqVO;
import cn.boheng.frame.module.device.dal.dataobject.codec.DeviceCodecDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface DeviceCodecMapper extends BaseMapperX<DeviceCodecDO> {

    default PageResult<DeviceCodecDO> selectPage(DeviceCodecPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<DeviceCodecDO>()
                .likeIfPresent(DeviceCodecDO::getCodecCode, reqVO.getCodecCode())
                .likeIfPresent(DeviceCodecDO::getCodecName, reqVO.getCodecName())
                .eqIfPresent(DeviceCodecDO::getParseType, reqVO.getParseType())
                .eqIfPresent(DeviceCodecDO::getStatus, reqVO.getStatus())
                .orderByDesc(DeviceCodecDO::getId));
    }

    default DeviceCodecDO selectByCodecCode(String codecCode) {
        return selectOne(DeviceCodecDO::getCodecCode, codecCode);
    }

    default List<DeviceCodecDO> selectEnabledList() {
        return selectList(new LambdaQueryWrapperX<DeviceCodecDO>()
                .eq(DeviceCodecDO::getStatus, 0)
                .orderByAsc(DeviceCodecDO::getCodecCode));
    }

}
