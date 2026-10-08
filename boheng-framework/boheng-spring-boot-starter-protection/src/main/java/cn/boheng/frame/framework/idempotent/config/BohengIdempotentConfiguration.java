package cn.boheng.frame.framework.idempotent.config;

import cn.boheng.frame.framework.idempotent.core.aop.IdempotentAspect;
import cn.boheng.frame.framework.idempotent.core.keyresolver.impl.DefaultIdempotentKeyResolver;
import cn.boheng.frame.framework.idempotent.core.keyresolver.impl.ExpressionIdempotentKeyResolver;
import cn.boheng.frame.framework.idempotent.core.keyresolver.IdempotentKeyResolver;
import cn.boheng.frame.framework.idempotent.core.keyresolver.impl.UserIdempotentKeyResolver;
import cn.boheng.frame.framework.idempotent.core.redis.IdempotentRedisDAO;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import cn.boheng.frame.framework.redis.config.BohengRedisAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

@AutoConfiguration(after = BohengRedisAutoConfiguration.class)
public class BohengIdempotentConfiguration {

    @Bean
    public IdempotentAspect idempotentAspect(List<IdempotentKeyResolver> keyResolvers, IdempotentRedisDAO idempotentRedisDAO) {
        return new IdempotentAspect(keyResolvers, idempotentRedisDAO);
    }

    @Bean
    public IdempotentRedisDAO idempotentRedisDAO(StringRedisTemplate stringRedisTemplate) {
        return new IdempotentRedisDAO(stringRedisTemplate);
    }

    // ========== 各种 IdempotentKeyResolver Bean ==========

    @Bean
    public DefaultIdempotentKeyResolver defaultIdempotentKeyResolver() {
        return new DefaultIdempotentKeyResolver();
    }

    @Bean
    public UserIdempotentKeyResolver userIdempotentKeyResolver() {
        return new UserIdempotentKeyResolver();
    }

    @Bean
    public ExpressionIdempotentKeyResolver expressionIdempotentKeyResolver() {
        return new ExpressionIdempotentKeyResolver();
    }

}
