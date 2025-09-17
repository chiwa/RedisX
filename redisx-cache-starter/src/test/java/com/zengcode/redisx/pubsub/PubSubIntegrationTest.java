package com.zengcode.redisx.pubsub;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.zengcode.redisx.annotation.pubsub.PublishX;
import com.zengcode.redisx.annotation.pubsub.SubscribeX;
import com.zengcode.redisx.autoconfiguration.cache.CacheProps;
import com.zengcode.redisx.autoconfiguration.pubsub.PubSubProps;
import com.zengcode.redisx.autoconfiguration.pubsub.PublishXAspect;
import com.zengcode.redisx.autoconfiguration.pubsub.SubscribeXRegistrar;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Integration tests for @PublishX / @SubscribeX on Redis Pub/Sub. */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
        // cache (prefix นี้จะถูกรวมใน topic: {prefix}:pub:{topic})
        "redisx.cache.enabled=true",
        "redisx.cache.prefix=zengcache",

        // pubsub + retry/backoff + logging
        "redisx.pubsub.enabled=true",
        "redisx.pubsub.publish-max-attempts=3",
        "redisx.pubsub.publish-backoff-ms=50",
        "redisx.pubsub.publish-backoff-multiplier=2.0",
        "redisx.pubsub.handler-max-attempts=3",
        "redisx.pubsub.handler-backoff-ms=50",
        "redisx.pubsub.handler-backoff-multiplier=2.0",
        "redisx.pubsub.log-payload=true",

        // source ใน message
        "spring.application.name=redisx-it"
})
@DirtiesContext
@Import(PubSubIntegrationTest.TestWiring.class)
class PubSubIntegrationTest {

    @Container
    @ServiceConnection
    static GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
                    .withExposedPorts(6379);

    @Autowired ProducerService producer;
    @Autowired OkSubscriber okSubscriber;
    @Autowired RetrySubscriber retrySubscriber;
    @Autowired FilteredSubscriber filteredSubscriber;

    @BeforeEach
    void reset() {
        okSubscriber.reset();
        retrySubscriber.reset();
        filteredSubscriber.reset();
    }

    @Test
    @DisplayName("Publish → Subscribe สำเร็จ (payload ถูก deserialize)")
    void publish_then_subscribe_ok() throws Exception {
        UserDto dto = new UserDto("u-1", "Chiwa");

        producer.updateUser(dto); // @PublishX(topic="user-updated", event="UPDATED")

        boolean received = okSubscriber.await(2, TimeUnit.SECONDS);
        assertThat(received).isTrue();
        assertThat(okSubscriber.lastPayload).isNotNull();
        assertThat(okSubscriber.lastPayload.id()).isEqualTo("u-1");
        assertThat(okSubscriber.lastPayload.name()).isEqualTo("Chiwa");

        // subscriber ที่ filter event != UPDATED ต้องไม่ถูกเรียก
        assertThat(filteredSubscriber.calls.get()).isZero();

        // เตรียม latch ใหม่ก่อนยิงรอบสอง
        okSubscriber.prepareNext();

        dto = new UserDto("u-2", "Pea");
        producer.updateUser(dto); // @PublishX(topic="user-updated", event="UPDATED")

        received = okSubscriber.await(2, TimeUnit.SECONDS);
        assertThat(received).isTrue();
        assertThat(okSubscriber.lastPayload).isNotNull();
        assertThat(okSubscriber.lastPayload.id()).isEqualTo("u-2");
        assertThat(okSubscriber.lastPayload.name()).isEqualTo("Pea");

    }

    @Test
    @DisplayName("Retry: Subscriber โยน exception 2 ครั้งแรก แล้วสำเร็จในครั้งที่ 3")
    void subscriber_retry_then_success() throws Exception {
        retrySubscriber.failuresToThrow.set(2); // ให้ล้ม 2 ครั้งแรก

        producer.updateUser(new UserDto("u-2", "Bob"));

        boolean received = retrySubscriber.await(3, TimeUnit.SECONDS);
        assertThat(received).isTrue();
        assertThat(retrySubscriber.calls.get()).isEqualTo(3); // 2 fail + 1 success
        assertThat(retrySubscriber.lastPayload.id()).isEqualTo("u-2");
    }

    // ---------------- Wiring/Beans สำหรับทดสอบ ----------------

    @SpringBootApplication
    static class App { }

    @Configuration
    @EnableConfigurationProperties(PubSubProps.class) // ให้ Spring bind redisx.pubsub.* → PubSubProps
    static class TestWiring {

        // Core beans
        @Primary
        @Bean CacheProps cacheProps() {
            CacheProps p = new CacheProps();
            p.setEnabled(true);
            p.setPrefix("zengcache");
            return p;
        }

        // อย่า @Bean PubSubProps ซ้ำ ปล่อยให้ Spring สร้างจาก @EnableConfigurationProperties

        @Bean RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory cf) {
            RedisMessageListenerContainer c = new RedisMessageListenerContainer();
            c.setConnectionFactory(cf);
            return c;
        }

        @Bean PublishXAspect publishXAspect(StringRedisTemplate srt, CacheProps cacheProps,
                                            PubSubProps pubProps, com.fasterxml.jackson.databind.ObjectMapper om) {
            return new PublishXAspect(srt, cacheProps, pubProps, om, "redisx-it");
        }

        // BeanPostProcessor ควรประกาศแบบ static
        @Bean
        public static SubscribeXRegistrar subscribeXRegistrar(RedisMessageListenerContainer container,
                                                              CacheProps cacheProps, PubSubProps pubProps,
                                                              com.fasterxml.jackson.databind.ObjectMapper om) {
            return new SubscribeXRegistrar(container, cacheProps, pubProps, om);
        }

        // Services under test
        @Bean ProducerService producerService() { return new ProducerService(); }
        @Bean OkSubscriber okSubscriber() { return new OkSubscriber(); }
        @Bean RetrySubscriber retrySubscriber() { return new RetrySubscriber(); }
        @Bean FilteredSubscriber filteredSubscriber() { return new FilteredSubscriber(); }
    }

    // ---------------- Demo DTO + Services/Subscribers ----------------

    public record UserDto(String id, String name) {
        @JsonCreator public UserDto(@JsonProperty("id") String id,
                                    @JsonProperty("name") String name) {
            this.id = id; this.name = name;
        }
    }

    static class ProducerService {
        @PublishX(topic = "user-updated", event = "UPDATED",
                payload = "#result", condition = "#result != null")
        public UserDto updateUser(UserDto input) {
            // สมมติอัปเดต DB สำเร็จแล้วคืนค่า
            return input;
        }
    }

    public static class OkSubscriber {
        private volatile CountDownLatch latch = new CountDownLatch(1);
        volatile UserDto lastPayload;

        @SubscribeX(topic = "user-updated", event = "UPDATED")
        public void onUpdated(UserDto dto) {
            lastPayload = dto;
            latch.countDown();
        }

        boolean await(long timeout, TimeUnit unit) throws InterruptedException {
            return latch.await(timeout, unit);
        }
        void prepareNext() { latch = new CountDownLatch(1); lastPayload = null; } // <— เพิ่ม
        void reset() { prepareNext(); }
    }

    public static class RetrySubscriber {
        private volatile CountDownLatch latch = new CountDownLatch(1);
        final AtomicInteger calls = new AtomicInteger(0);
        final AtomicInteger failuresToThrow = new AtomicInteger(0);
        volatile UserDto lastPayload;

        @SubscribeX(topic = "user-updated", event = "UPDATED")
        public void onUpdated(UserDto dto) {
            int call = calls.incrementAndGet();
            lastPayload = dto;
            if (failuresToThrow.getAndDecrement() > 0) {
                throw new RuntimeException("intentional failure on call " + call);
            }
            latch.countDown();
        }

        boolean await(long timeout, TimeUnit unit) throws InterruptedException {
            return latch.await(timeout, unit);
        }
        void reset() {
            calls.set(0);
            failuresToThrow.set(0);
            lastPayload = null;
            latch = new CountDownLatch(1);
        }
    }

    public static class FilteredSubscriber {
        final AtomicInteger calls = new AtomicInteger(0);

        // filter event ให้ไม่ตรงกับ UPDATED
        @SubscribeX(topic = "user-updated", event = "CREATED")
        public void onCreated(UserDto dto) { calls.incrementAndGet(); }

        void reset() { calls.set(0); }
    }
}