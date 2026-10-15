package com.nuclei.orderservice.cache;

import com.nuclei.orderservice.dtos.OrderResponseDto;
import lombok.NoArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
@NoArgsConstructor
public class OrderIdempotencyRedisConfig {

    @Bean("orderIdempotencyRedisTemplate")
    public RedisTemplate<String, OrderResponseDto> orderIdempotencyRedisTemplate(
            final RedisConnectionFactory connectionFactory) {
        final RedisTemplate<String, OrderResponseDto> redisTemplate =
                new RedisTemplate<>();

        redisTemplate.setConnectionFactory(connectionFactory);
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(
                new JacksonJsonRedisSerializer<>(OrderResponseDto.class));
        redisTemplate.afterPropertiesSet();

        return redisTemplate;
    }
}