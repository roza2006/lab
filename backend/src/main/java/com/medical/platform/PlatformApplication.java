package com.medical.platform;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 后端启动类（模块化单体）。
 *
 * <p>包结构按限界上下文划分：{@code identity}、{@code consultation}、{@code prescription}、
 * {@code circulation}、{@code catalog}、{@code audit}；每个上下文内部再分
 * {@code interfaces / application / domain / infrastructure}，领域层不依赖 Spring 与 MyBatis。
 *
 * <p>仓储实现与 MyBatis Mapper 统一放在各上下文的 {@code infrastructure.mapper} 包下，
 * 由 {@link MapperScan} 扫描注册。
 */
@SpringBootApplication
@MapperScan("com.medical.platform.**.infrastructure.mapper")
public class PlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(PlatformApplication.class, args);
    }
}
