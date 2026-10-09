package cn.boheng.frame.module.device.dal.mysql.codec;

import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.framework.mybatis.core.mapper.BaseMapperX;
import cn.boheng.frame.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.boheng.frame.module.device.controller.admin.codec.vo.DeviceCodecPageReqVO;
import cn.boheng.frame.module.device.dal.dataobject.codec.DeviceCodecDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
/**
 * 解析规则 Mapper
 */

@Mapper
public interface DeviceCodecMapper extends BaseMapperX<DeviceCodecDO> {

    /** 分页查询解析规则 */
    default PageResult<DeviceCodecDO> selectPage(DeviceCodecPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<DeviceCodecDO>()
                .likeIfPresent(DeviceCodecDO::getCodecCode, reqVO.getCodecCode())
                .likeIfPresent(DeviceCodecDO::getCodecName, reqVO.getCodecName())
                .eqIfPresent(DeviceCodecDO::getParseType, reqVO.getParseType())
                .eqIfPresent(DeviceCodecDO::getStatus, reqVO.getStatus())
                .orderByDesc(DeviceCodecDO::getId));
    }

    /** 按规则编码查询 */
    default DeviceCodecDO selectByCodecCode(String codecCode) {
        return selectOne(DeviceCodecDO::getCodecCode, codecCode);
    }

    /** 查询启用的解析规则 */
    default List<DeviceCodecDO> selectEnabledList() {
        return selectList(new LambdaQueryWrapperX<DeviceCodecDO>()
                .eq(DeviceCodecDO::getStatus, 0)
                .orderByAsc(DeviceCodecDO::getCodecCode));
    }

}
