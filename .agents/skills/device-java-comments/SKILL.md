---
name: device-java-comments
description: >-
  要求 boheng-module-device 里每个类、属性、方法都有中文注释。
  在新增或修改 device 模块的 Java（DO、VO、Service、Controller、Mapper、Gateway）时使用。
---

# device 模块注释

整个 device 模块，每个属性和方法上都要有注释。这些都是需要的。

改 `boheng-module-device` 的 Java 时，先补齐本次碰到的类里缺的注释，再结束。

## 写在哪

- 类：类声明上方的 Javadoc，说明这张表或这个类负责什么。
- 属性：字段声明上方的 Javadoc。常量、注入的 Mapper 和 Service 也算属性。
- 方法：方法声明上方的 Javadoc。`private` 方法、包内方法、Mapper 的 `default` 方法都要写。接口和实现类各自都要有，不能只写在接口上。
- 记录组件：写在 record 的 Javadoc 里，用 `@param`。

注释用中文，写这个成员在设备域里做什么。不要只重复字段名。

## 已经算注释的写法

下面两种不要再叠一层 Javadoc：

- VO 字段上的 `@Schema(description = "...")`
- Controller 方法上的 `@Operation(summary = "...")`

没有这两种注解的字段和方法，必须有 Javadoc。
