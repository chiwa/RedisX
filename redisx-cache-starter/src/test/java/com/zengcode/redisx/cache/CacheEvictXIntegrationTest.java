package com.zengcode.redisx.cache;

import com.zengcode.redisx.annotation.CacheableX;
import com.zengcode.redisx.annotation.CacheEvictX;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
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
        "redisx.cache.prefix=itcache",
        "redisx.cache.enabled=true"
})
class CacheEvictXIntegrationTest {

    @Container
    @ServiceConnection // Spring Boot จะ bind spring.data.redis.* ให้อัตโนมัติ
    static GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
                    .withExposedPorts(6379);

    @Autowired
    DemoService demo;

    @Autowired
    StringRedisTemplate srt;

    private String k(String id) {
        return "itcache:demo:user:" + id;
    }

    @Test
    void evict_one_key_after_update() {
        // arrange
        var id = "U1";
        demo.getUser(id); // MISS → SET
        assertThat(srt.hasKey(k(id))).isTrue();

        // act
        demo.updateUser(id); // มี @CacheEvictX รายคีย์

        // assert
        assertThat(srt.hasKey(k(id))).isFalse();
    }

    @Test
    void evict_all_before_invoke() {
        // arrange
        demo.getUser("U1");
        demo.getUser("U2");
        assertThat(srt.hasKey(k("U1"))).isTrue();
        assertThat(srt.hasKey(k("U2"))).isTrue();

        // act
        demo.refreshAll(); // มี @CacheEvictX(allEntries=true, beforeInvoke=true)

        // assert
        assertThat(srt.hasKey(k("U1"))).isFalse();
        assertThat(srt.hasKey(k("U2"))).isFalse();
    }

    // ===== Config =====
    @SpringBootApplication
    static class TestApp {
        @Bean DemoService demoService() { return new DemoService(); }
    }

    static class DemoService {
        private final AtomicInteger counter = new AtomicInteger(0);

        @CacheableX(cacheName = "demo:user", key = "#id", ttlSeconds = 60)
        public String getUser(String id) {
            counter.incrementAndGet();
            return "user-" + id;
        }

        @CacheEvictX(cacheName = "demo:user", key = "#id")
        public void updateUser(String id) {
            // จำลองอัปเดต
        }

        @CacheEvictX(cacheName = "demo:user", allEntries = true, beforeInvoke = true)
        public void refreshAll() {
            // จำลอง import ข้อมูลใหม่
        }

        int getCallCount() { return counter.get(); }
    }
}