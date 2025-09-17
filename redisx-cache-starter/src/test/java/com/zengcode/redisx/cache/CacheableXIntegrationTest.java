package com.zengcode.redisx.cache;

import com.zengcode.redisx.annotation.cache.CacheableX;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
        "redisx.cache.prefix=zengcache",
        "redisx.cache.enabled=true"
})
@DirtiesContext
class CacheableXIntegrationTest {

    @Container
    @ServiceConnection // Spring Boot จะ bind spring.data.redis.* ให้อัตโนมัติ
    static GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
                    .withExposedPorts(6379);

    @Autowired DemoService demo;
    @Autowired StringRedisTemplate srt;

    @Test
    void firstCall_isMiss_andSetCache_thenSecondCall_isHit() {
        String id = "123";

        var v1 = demo.getUser(id);
        assertThat(v1).isEqualTo("user-" + id);
        assertThat(demo.callCount()).isEqualTo(1);

        String key = "zengcache:demo:user:" + id;
        assertThat(Boolean.TRUE.equals(srt.hasKey(key))).isTrue();

        var v2 = demo.getUser(id);
        assertThat(v2).isEqualTo("user-" + id);
        assertThat(demo.callCount()).isEqualTo(1); // ยัง 1 แปลว่า HIT

        var v3 = demo.getUser("999");
        assertThat(v3).isEqualTo("user-999");
        assertThat(demo.callCount()).isEqualTo(2);
    }

    @SpringBootApplication
    static class TestApp {
        @Bean DemoService demoService() { return new DemoService(); }
    }

    static class DemoService {
        private final AtomicInteger counter = new AtomicInteger();

        @CacheableX(cacheName = "demo:user", key = "#id", ttlSeconds = 60)
        public String getUser(String id) {
            counter.incrementAndGet();
            return "user-" + id;
        }
        int callCount() { return counter.get(); }
    }
}