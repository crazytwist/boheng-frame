package cn.boheng.frame.module.transport.service;

import cn.boheng.frame.module.transport.api.dto.TransportRouteDTO;

/** 根据静态路线编码解析 Transport 路线，不承载运行时任务数据。 */
public interface TransportRouteResolver {
    /** 查询已启用的路线。 */
    TransportRouteDTO resolve(String routeCode);
}
