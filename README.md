# RedisX Starter

RedisX คือ Spring Boot Starter ที่ออกแบบมาให้ **ใช้งาน Redis ครอบคลุมทุกฟีเจอร์** ได้ผ่าน Annotation บนเมธอดโดยตรง  
เน้น **Annotation-first Programming Model** → นักพัฒนาไม่ต้องเรียก API เอง แต่ใช้แค่แปะ annotation แล้วระบบจัดการให้ครบ

---

## Why RedisX (เมื่อมี Spring Cache อยู่แล้ว)

หลายคนอาจสงสัยว่า: *“Spring เองก็มี **`@Cacheable`** อยู่แล้ว ทำไมต้องมี RedisX?”*
คำตอบคือ **Spring Cache ถูกออกแบบมาแบบ generic** (ใช้กับ backend ได้หลายชนิด) ในขณะที่ RedisX โฟกัสที่ Redis 100% และปรับให้เหมาะกับ production จริง

### 1. Spring Cache = Abstraction กว้าง

* รองรับหลาย backend (Ehcache, Caffeine, Redis ฆลฎ)
* ออกแบบมาให้ generic มาก → ดีเวลาอยากสลับ backend
* แต่เวลาใช้ Redis จริง มักรู้สึกว่า “ยังไม่สุด” เช่น

    * TTL ต่อ key ไม่ flexible เท่าที่ควร
    * การ evict ทั้ง group ทำยาก
    * ไม่มี logging HIT/MISS ให้ดู performance

### 2. RedisX = Redis-First + Opinionated

* โฟกัส Redis อย่างเดียว → optimize ได้ลึกกว่า
* Features ที่ Spring Cache ไม่มี:

    * TTL ต่อ key ชัดเจน
    * Evict ทั้งกลุ่ม (`@CacheEvictX(allEntries=true)`) ด้วย SCAN (ปลอดภัยกว่า KEYS)
    * Hash/Map cache (`@MapCachePut` / `@MapCacheGet`)
    * Logging HIT / MISS / SET พร้อมขนาด payload
    * SpEL condition/unless ที่ integrate ลึกกับ result object

---

## RedisX + RedissonLock

นอกจาก cache แล้ว ระบปริงเจอาปัญหา **race condition** หรือ **update ซ้อนกัน** อยู่บ่่อย ๆ เช่น

* User หลายคนยิง request อัปเดต order พร้อมกัน
* Service หลาย instance ดึงข้อมูลเดียวกันพร้อมกัน → ทำให้เกิด *cache stampede*
* Job ที่รันทุก ๆ 5 นาที แต่มีหลาย instance → รันชนกันซ้ำ

### Spring เองไม่มี Lock API

* Dev ต้องไปใช้ Redisson เอง → ต้องเขี่ยน boilerplate `lock()/unlock()` เยอะ
* เส่งลืม unlock หรือเขี่ยน try/finally พลาด

### RedisX มาพร้อม `@RedissonLockX`

* ใช้ annotation ครอบ method ได้เลย
* Lock จะถูก acquire ก่อน execute method
* ปลอดภัย: auto-unlock หลังเมธอดเสร็จ หรือถ้า timeout

**ตัวอย่างการใช้งาน**

```java
@Service
class PaymentService {

  // ป้องกันไม่ให้มีการจ่ายเงิน order เดียวกันพร้อมกัน
  @RedissonLockX(key = "'order:' + #orderId", waitTime = 5, leaseTime = 30)
  public void pay(String orderId) {
    // โค้ดนี้ถูก lock แบบ distributed แล้ว
    processPayment(orderId);
  }
}
```

### Parameters ของ `@RedissonLockX`

* `key` – SpEL สำหรับระบุชื่อ lock (ควร unique ต่อ resource)
* `waitTime` – เวลาที่จะรอ lock (วินาที) ก่อน timeout
* `leaseTime` – อายุ lock (วินาที) ป้องกัน dev ลืม unlock

---

---

## ใช้ผ่าน JitPack

ถ้าคุณต้องการใช้ RedisX โดยไม่ต้อง deploy ขึ้น Maven Central หรือมีการบิลด์รุ่นใหม่บ่อย ๆ สามารถใช้ JitPack ได้:

**Gradle (Kotlin DSL)**

```kotlin
dependencies {
  implementation("com.github.chiwa:RedisX:0.1.0")
}

repositories {
  maven { url = uri("https://jitpack.io") }
}
```

**Gradle (Groovy DSL)**

```groovy
dependencies {
  implementation 'com.github.chiwa:RedisX:0.1.0'
}

repositories {
  maven { url 'https://jitpack.io' }
}
```

**Maven**

```xml
<repositories>
  <repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
  </repository>
</repositories>

<dependency>
  <groupId>com.github.chiwa</groupId>
  <artifactId>RedisX</artifactId>
  <version>0.1.0</version>
</dependency>
```
---

## Redis Features & Use Cases

### 1. Cache
- **คืออะไร:** เก็บข้อมูลชั่วคราวในหน่วยความจำ พร้อม TTL/Expire
- **ใช้เมื่อไหร่:** ลดภาระ DB, session store, token store
- **Annotation:** `@CacheableX`, `@MapCachePut`, `@MapCacheGet`

---

### 2. Pub/Sub
- **คืออะไร:** ส่งข้อความแบบ real-time ผ่าน channel (ไม่คงค้าง)
- **ใช้เมื่อไหร่:** Broadcast event, invalidate cache, notify หลาย service
- **Annotation:** `@Publish`

---

### 3. Streams
- **คืออะไร:** Event log มี Consumer Group, ack, retry, DLQ
- **ใช้เมื่อไหร่:** Event-driven architecture, durable queue, job processing
- **Annotation:** `@StreamEmit`

---

### 4. Distributed Lock
- **คืออะไร:** ป้องกันหลาย instance ทำงานชนกัน
- **ใช้เมื่อไหร่:** Process order, cron job ที่ต้อง run แค่ตัวเดียว
- **Annotation:** `@DistributedLock`

---

### 5. Rate Limiter
- **คืออะไร:** จำกัดจำนวนการเรียก/หน่วยเวลา
- **ใช้เมื่อไหร่:** API throttle, ป้องกัน abuse
- **Annotation:** `@RateLimit`

---

### 6. Idempotency
- **คืออะไร:** ป้องกัน request ซ้ำซ้อน
- **ใช้เมื่อไหร่:** Payment, checkout, critical transaction
- **Annotation:** `@Idempotent`

---

### 7. Semaphore / Concurrency Control
- **คืออะไร:** จำกัดจำนวนงานที่ทำพร้อมกัน
- **ใช้เมื่อไหร่:** Job ที่กิน resource มาก เช่น report rendering
- **Annotation:** `@SemaphoreAcquire`

---

### 8. Queue (List / Sorted Set / Redisson)
- **คืออะไร:** จัดคิวงาน NORMAL / DELAYED / PRIORITY
- **ใช้เมื่อไหร่:** Async job, scheduled job
- **Annotation:** `@QueueEnqueue`

---

### 9. Geo
- **คืออะไร:** เก็บพิกัด ค้นหาจุดในรัศมี
- **ใช้เมื่อไหร่:** Rider near me, ร้านใกล้ฉัน
- **Annotation:** `@GeoAdd`, `@GeoNearby`

---

### 10. HyperLogLog
- **คืออะไร:** นับ unique แบบ memory-efficient (~12KB)
- **ใช้เมื่อไหร่:** นับ unique visitors, unique IP
- **Annotation:** `@HllAdd`

---

### 11. Bitmaps
- **คืออะไร:** Track สถานะ/บิต เช่น login วันไหน
- **ใช้เมื่อไหร่:** Daily active users, feature seen/unseen
- **Annotation:** (planned) `@BitmapSet`, `@BitmapGet`

---

### 12. Bloom Filter (RedisBloom)
- **คืออะไร:** Probabilistic filter “น่าจะเคยเห็นแล้ว”  
- **ใช้เมื่อไหร่:** กัน duplicate, spam, ป้องกัน query DB ซ้ำ
- **Annotation:** `@BloomEnsureNew`

---

### 13. RedisJSON (Redis Stack)
- **คืออะไร:** เก็บ/อัปเดต JSON เป็น document store
- **ใช้เมื่อไหร่:** Config/document service, query sub-path
- **Annotation:** (planned) `@JsonSet`, `@JsonGet`

---

### 14. RediSearch (Redis Stack)
- **คืออะไร:** Full-text search + secondary index
- **ใช้เมื่อไหร่:** Search ภายใน document, analytics
- **Annotation:** (planned) `@SearchIndex`, `@SearchQuery`

---

### 15. TimeSeries (RedisTimeSeries)
- **คืออะไร:** เก็บข้อมูลชุดเวลา (metrics, IoT)
- **ใช้เมื่อไหร่:** Monitor metrics, IoT sensor data
- **Annotation:** (planned) `@TimeSeriesAdd`, `@TimeSeriesQuery`

---

## Roadmap: RedisX Starter

### Core (MVP)
- **Cache**: @CacheableX, @MapCachePut/@MapCacheGet  
- **Distributed Lock**: @DistributedLock  
- **Idempotency**: @Idempotent  
- **Rate Limiter**: @RateLimit  
- **Semaphore**: @SemaphoreAcquire  
- **Pub/Sub**: @Publish  
- **Queue**: @QueueEnqueue (NORMAL/DELAYED/PRIORITY)  
- **Streams**: @StreamEmit + consumer infra  

### Extended Data Structures
- **Geo**: @GeoAdd, @GeoNearby  
- **HyperLogLog**: @HllAdd  
- **Bitmaps**: @BitmapSet, @BitmapGet  
- **Bloom Filter**: @BloomEnsureNew  

### Redis Stack Integration
- **RedisJSON**: @JsonSet, @JsonGet  
- **RediSearch**: @SearchIndex, @SearchQuery  
- **TimeSeries**: @TimeSeriesAdd, @TimeSeriesQuery  

---

## แนวคิดหลัก
- ทุกฟีเจอร์ใช้งานผ่าน **Annotation บนเมธอด** → ไม่ต้องเรียก API เอง  
- เน้น **Idempotency + Reliability** → กันซ้ำ, retry, DLQ  
- มี **Actuator metrics/health** ครอบคลุมสำหรับ production  

---

## ตัวอย่างโค้ดสั้น ๆ

```java
@Service
public class PaymentService {

  @Idempotent(key=\"'pay:' + #orderId\", ttlSeconds=600)
  @DistributedLock(key=\"'order:' + #orderId\", leaseMillis=15000)
  @RateLimit(key=\"'user:' + #userId + ':pay'\", permits=5, intervalSeconds=1)
  @StreamEmit(stream=\"'stream:payment'\", body=\"T(java.util.Map).of('order',#orderId,'event','PAID')\")
  public Receipt pay(String orderId, String userId) {
    // ... ตัดบัตร / update order
    return new Receipt(orderId, \"PAID\");
  }
}
```
# RedisX Starters – Release Notes & Docs

## Releases 

- [Cache Starter](docs/v_0_1_0/v0.1.0-cache.md)
- [Pub/Sub Starter](docs/v_0_2_0/v0.2.0-pubsub.md) [Future]
- [Streams Starter](docs/v_0_3_0/v0.3.0-streams.md) [Future]
- [Distributed Lock Starter](docs/v_0_4_0/v0.4.0-lock.md) [Future]
- [Rate Limiter Starter](docs/v_0_5_0/v0.5.0-ratelimit.md) [Future]
- [Idempotency Starter](docs/v_0_6_0/v0.6.0-idempotent.md) [Future]
- [Semaphore Starter](docs/v_0_7_0/v0.7.0-semaphore.md) [Future]
- [Queue Starter](docs/v_0_8_0/v0.8.0-queue.md) [Future]
- [Geo Starter](docs/v_0_9_0/v0.9.0-geo.md) [Future]