package com.myexampleproject.productservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

@Configuration
@Profile("redis")
public class RedisConfig {
    @Bean
    public RedisCacheConfiguration cacheConfiguration() {

        // 1. Tạo một ObjectMapper (trình xử lý JSON) chuẩn
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule()) // Hỗ trợ Java Time (nếu cần)
                .findAndRegisterModules(); // Tự động tìm các module khác

        // 2. Tạo Serializer và "ép" nó dùng ObjectMapper chuẩn của chúng ta
        // (Cách này sẽ không tạo ra thông tin "@class" nữa)
        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer(objectMapper);

        // 3. Trả về cấu hình cache
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .disableCachingNullValues()
                // 4. Dùng Serializer JSON mới của chúng ta
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer));
    }
}
