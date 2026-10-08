/**
 * 设备域「对外契约」包。
 *
 * 只放跨模块调用的接口（DeviceApi）、DTO、枚举、常量。
 * 硬约束：零 Spring / 零 MyBatis / 零 DAL，只被实现模块与消费方依赖，绝不反向依赖实现。
 *
 * 详见 .workbuddy/reference/device-domain-architecture.md §一（双模块 + 两个 SPI）。
 */
package cn.boheng.frame.module.device.api;
