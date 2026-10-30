package cn.boheng.frame.module.transport.dal.mysql;

import cn.boheng.frame.framework.mybatis.core.mapper.BaseMapperX;
import cn.boheng.frame.module.transport.dal.dataobject.TransportRouteConfigDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

/** Transport 静态路线配置 Mapper。 */
@Mapper
public interface TransportRouteConfigMapper extends BaseMapperX<TransportRouteConfigDO> {
    /** 查询启用路线，路线表只读，不创建运行时记录。 */
    default TransportRouteConfigDO selectEnabledByCode(String routeCode) {
        return selectOne(new LambdaQueryWrapper<TransportRouteConfigDO>()
                .eq(TransportRouteConfigDO::getRouteCode, routeCode)
                .eq(TransportRouteConfigDO::getEnabled, 1));
    }
}
