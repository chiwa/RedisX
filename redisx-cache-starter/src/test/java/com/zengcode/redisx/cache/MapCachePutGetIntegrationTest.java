package com.zengcode.redisx.cache;

import com.zengcode.redisx.annotation.MapCacheEvict;
import com.zengcode.redisx.annotation.MapCacheGet;
import com.zengcode.redisx.annotation.MapCachePut;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
        "redisx.cache.prefix=zengcache",
        "redisx.cache.enabled=true"
})
@DirtiesContext
class MapCachePutGetIntegrationTest {

    @Container
    @ServiceConnection // Spring Boot จะ bind spring.data.redis.* ให้อัตโนมัติ
    static GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
                    .withExposedPorts(6379);

    @Autowired DemoService demo;
    @Autowired StringRedisTemplate srt;

    private static final String HASH_KEY = "zengcache:demo:user"; // prefix:cacheName

    @BeforeEach
    void clear() {
        srt.delete(HASH_KEY);
        demo.reset();
    }

    @Test
    @DisplayName("@MapCachePut → put ลง Redis HASH + ตั้ง TTL สำเร็จ")
    void mapCachePut_putsIntoHash_and_setsTtl() {
        String id = "123";
        String expected = "user-" + id;

        // call business → ควร put เข้า hash field = id
        String result = demo.saveUser(id);
        assertThat(result).isEqualTo(expected);

        // hash field ต้องมี และค่าถูกต้อง
        Object fromHash = srt.opsForHash().get(HASH_KEY, id);
        assertThat(fromHash).isEqualTo(expected);

        // TTL ของ hash (key ระดับ hash) ต้องถูกตั้ง (>= ใกล้ ๆ ttlSeconds)
        Long ttlSeconds = srt.getExpire(HASH_KEY, TimeUnit.SECONDS);
        if (ttlSeconds == null || ttlSeconds <= 0) {
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
            ttlSeconds = srt.getExpire(HASH_KEY, TimeUnit.SECONDS);
        }
        assertThat(ttlSeconds).isPositive();
    }

    @Test
    @DisplayName("@MapCacheGet → ครั้งแรก miss แล้ว hit รอบต่อไป")
    void mapCacheGet_firstMiss_thenHit() {
        String id = "200";
        String expected = "user-" + id;

        // ก่อนหน้า cache ว่าง → ครั้งแรกควร miss และนับเป็น 1
        String v1 = demo.getUser(id);
        assertThat(v1).isEqualTo(expected);
        assertThat(demo.dbHits()).isEqualTo(1);

        // ควรมีค่าใน hash แล้ว
        Object fromHash = srt.opsForHash().get(HASH_KEY, id);
        assertThat(fromHash).isEqualTo(expected);

        // ครั้งที่สองต้อง hit (ไม่เพิ่ม dbHits)
        String v2 = demo.getUser(id);
        assertThat(v2).isEqualTo(expected);
        assertThat(demo.dbHits()).isEqualTo(1);

        // เรียกคนละ id → miss ใหม่
        String other = demo.getUser("201");
        assertThat(other).isEqualTo("user-201");
        assertThat(demo.dbHits()).isEqualTo(2);
    }

    @Test
    @DisplayName("@MapCachePut + TTL → หมดอายุแล้ว hash หายทั้งก้อน")
    void mapCachePut_ttlExpires_hashGone() throws Exception {
        String id = "777";
        demo.saveUser(id); // ttlSeconds = 3 ใน annotation ข้างล่าง

        assertThat(srt.opsForHash().get(HASH_KEY, id)).isEqualTo("user-" + id);

        // รอให้หมดอายุ (กันเหนี่ยวเผื่อ jitter)
        Thread.sleep(Duration.ofSeconds(4).toMillis());

        // hash จะหมดอายุทั้งก้อน → key หาย
        Boolean exists = srt.hasKey(HASH_KEY);
        assertThat(Boolean.TRUE.equals(exists)).isFalse();
    }

    @Test
    @DisplayName("@MapCacheEvict (field) → ลบเฉพาะ field แล้ว get ครั้งถัดไปควร miss และเขียนกลับ")
    void mapCacheEvict_field_removesOnlyThatField() {
        // Arrange: ใส่ 2 ค่าใน hash
        demo.saveUser("u1"); // field=u1
        demo.saveUser("u2"); // field=u2
        assertThat(srt.opsForHash().get(HASH_KEY, "u1")).isEqualTo("user-u1");
        assertThat(srt.opsForHash().get(HASH_KEY, "u2")).isEqualTo("user-u2");

        // Act: evict เฉพาะ u1
        demo.evictUser("u1");

        // Assert: u1 หาย แต่ u2 ยังอยู่
        assertThat(srt.opsForHash().get(HASH_KEY, "u1")).isNull();
        assertThat(srt.opsForHash().get(HASH_KEY, "u2")).isEqualTo("user-u2");

        // และเมื่อ get u1 ควร miss (dbHits +1) แล้วใส่กลับเข้า hash
        int before = demo.dbHits();
        var v = demo.getUser("u1");
        assertThat(v).isEqualTo("user-u1");
        assertThat(demo.dbHits()).isEqualTo(before + 1);
        assertThat(srt.opsForHash().get(HASH_KEY, "u1")).isEqualTo("user-u1");
    }

    @Test
    @DisplayName("@MapCacheEvict (allEntries) → ลบทั้งกลุ่ม (DEL hash) แล้ว key หายทั้งก้อน")
    void mapCacheEvict_allEntries_removesWholeHash() {
        // Arrange
        demo.saveUser("x");
        demo.saveUser("y");
        assertThat(Boolean.TRUE.equals(srt.hasKey(HASH_KEY))).isTrue();

        // Act: ลบทั้งกลุ่ม
        demo.evictAllUsers();

        // Assert: hash key หายทั้งก้อน
        assertThat(Boolean.TRUE.equals(srt.hasKey(HASH_KEY))).isFalse();

        // และครั้งหน้าที่ get ควร miss (dbHits +1) และสร้าง hash ใหม่
        int before = demo.dbHits();
        var v = demo.getUser("x");
        assertThat(v).isEqualTo("user-x");
        assertThat(demo.dbHits()).isEqualTo(before + 1);
        assertThat(srt.opsForHash().get(HASH_KEY, "x")).isEqualTo("user-x");
    }

    // ------------------ Test App & Demo Service ------------------

    @SpringBootApplication
    static class TestApp {
        @Bean DemoService demoService() { return new DemoService(); }
    }

    static class DemoService {
        private final AtomicInteger dbHits = new AtomicInteger();

        /**
         * บันทึก user แล้ว cache ลง Hash:
         *   redis key = "zengcache:demo:user"
         *   field     = id
         *   value     = "user-{id}"
         *   TTL       = 3s (ทั้ง hash)
         */
        @MapCachePut(cacheName = "demo:user", key = "#id", ttlSeconds = 3)
        public String saveUser(String id) {
            return "user-" + id;
        }

        /**
         * ดึง user จาก Hash:
         *   ถ้า miss → นับว่าไป DB 1 ครั้ง แล้วคืนค่า "user-{id}" และใส่ cache ให้อัตโนมัติ
         *   ถ้า hit → ไม่เพิ่ม dbHits
         */
        @MapCacheGet(cacheName = "demo:user", key = "#id")
        public String getUser(String id) {
            dbHits.incrementAndGet(); // จำลอง hit DB เมื่อ cache miss
            return "user-" + id;
        }

        /** ลบเฉพาะ field (user รายตัว) */
        @MapCacheEvict(cacheName = "demo:user", key = "#id")
        public void evictUser(String id) {
            // สมมุติว่ามีการลบ/อัพเดท DB จริง ๆ ตรงนี้
        }

        /** ลบทั้งกลุ่ม (ทั้ง hash) */
        @MapCacheEvict(cacheName = "demo:user", allEntries = true)
        public void evictAllUsers() {
            // สมมุติว่ามีการลบ/อัพเดท DB จริง ๆ ตรงนี้
        }

        int dbHits() { return dbHits.get(); }
        void reset() { dbHits.set(0); }
    }
}