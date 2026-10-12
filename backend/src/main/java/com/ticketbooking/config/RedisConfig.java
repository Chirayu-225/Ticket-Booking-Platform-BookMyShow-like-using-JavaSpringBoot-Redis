package com.ticketbooking.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class RedisConfig {

    /**
     * A plain String RedisTemplate is all the hold lock needs: keys and values
     * are seat ids and hold ids, and the only Redis command that matters here
     * is the atomic "SET key value NX EX seconds" issued from HoldService.
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}
