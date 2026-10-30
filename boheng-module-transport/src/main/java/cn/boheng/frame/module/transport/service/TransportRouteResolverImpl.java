package cn.boheng.frame.module.transport.service;

import cn.boheng.frame.framework.common.util.object.BeanUtils;
import cn.boheng.frame.module.transport.api.dto.TransportRouteDTO;
import cn.boheng.frame.module.transport.dal.dataobject.TransportRouteConfigDO;
import cn.boheng.frame.module.transport.dal.mysql.TransportRouteConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import static cn.boheng.frame.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.boheng.frame.module.transport.enums.ErrorCodeConstants.TRANSPORT_ROUTE_NOT_FOUND;

/** 静态 Transport 路线解析器。 */
@Service
@RequiredArgsConstructor
public class TransportRouteResolverImpl implements TransportRouteResolver {
    /** 静态路线配置数据访问对象。 */
    private final TransportRouteConfigMapper routeConfigMapper;

    /** 根据路线编码读取启用的静态路线。 */
    @Override
    public TransportRouteDTO resolve(String routeCode) {
        TransportRouteConfigDO route = routeConfigMapper.selectEnabledByCode(routeCode);
        if (route == null) {
            throw exception(TRANSPORT_ROUTE_NOT_FOUND, routeCode);
        }
        return BeanUtils.toBean(route, TransportRouteDTO.class);
    }
}
