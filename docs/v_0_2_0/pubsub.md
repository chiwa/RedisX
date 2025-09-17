# RedisX – Release v0.2.0 


Release นี้เพิ่มความสามารถ Pub/Sub แบบ annotation-first, ระบบรีทรายทั้งฝั่ง publish/subscribe, และปรับปรุงความเสถียรของการ deserialize payload พร้อมตัวอย่างทดสอบแบบ end-to-end ด้วย Testcontainers

---

##  New Features

* **Pub/Sub Annotation Support**

    * `@PublishX` สำหรับประกาศ publish event ได้บน method
    * `@SubscribeX` สำหรับ subscribe แบบ method-based handler
    * รองรับ `topic` + `event` filter และ auto-deserialize payload → DTO

* **Retry & Backoff for Subscriber**

    * รีทรายอัตโนมัติเมื่อ handler โยน exception
    * ปรับได้ผ่าน:

        * `redisx.pubsub.handler-max-attempts`
        * `redisx.pubsub.handler-backoff-ms`
        * `redisx.pubsub.handler-backoff-multiplier`

* **Publish Retry**

    * รีทรายเมื่อ publish ไป Redis ผิดพลาด
    * ตั้งค่าได้ผ่าน `redisx.pubsub.publish-*`

* **Structured Logging**

    * Log topic, event, และ (optional) payload เมื่อ `redisx.pubsub.log-payload=true`

---

## 🛠 Improvements

* ใช้ `ObjectMapper.convertValue` สำหรับ mapping payload → DTO เพื่อความยืดหยุ่นและรองรับ record/class ได้ดีขึ้น
* Topic จะถูก prefix ด้วย `redisx.cache.prefix` (เช่น `zengcache:pub:{topic}`) เพื่อหลีกเลี่ยงการชนกันระหว่าง services
* `SubscribeXRegistrar` จำกัด method ที่รองรับให้มีพารามิเตอร์ **0 หรือ 1 ตัว** เพื่อให้ contract ชัดเจนและตรวจสอบได้ง่าย

---

##  Configuration Reference (`application.yml`)

```yaml
redisx:
  cache:
    enabled: true
    prefix: zengcache
  pubsub:
    enabled: true
    publish-max-attempts: 3
    publish-backoff-ms: 50
    publish-backoff-multiplier: 2.0
    handler-max-attempts: 3
    handler-backoff-ms: 50
    handler-backoff-multiplier: 2.0
    log-payload: true
```

### Explanation of Pub/Sub Config

* **enabled** → เปิด/ปิดระบบ Pub/Sub (true = เปิดใช้งาน)
* **publish-max-attempts** → จำนวนครั้งสูงสุดที่ retry เมื่อ publish ล้มเหลว
* **publish-backoff-ms** → เวลารอ (ms) ก่อน retry ครั้งแรก
* **publish-backoff-multiplier** → ตัวคูณเวลาหน่วง → exponential backoff (เช่น 50 → 100 → 200)
* **handler-max-attempts** → จำนวนครั้งสูงสุดที่ retry เมื่อ subscriber handler ล้มเหลว
* **handler-backoff-ms** → เวลารอ (ms) ก่อน retry handler
* **handler-backoff-multiplier** → ตัวคูณเวลาหน่วงของ handler → exponential backoff
* **log-payload** →

    * `true` = log payload จริง (full JSON)
    * `false` = log เฉพาะ class type (เหมาะกับ production ที่ payload ใหญ่มาก)

---

##  Behavior เมื่อรันหลาย Instance

* **Redis Pub/Sub = Broadcast**
  ทุก instance ที่ subscribe topic เดียวกันจะได้รับ message เดียวกันทั้งหมด (fan-out)
  → ถ้าไม่อยากให้ทำซ้ำ ต้องทำ handler ให้ **idempotent** หรือ dedupe เอง

* **ต้องการ Load Balancing จริงจัง**
  Pub/Sub ไม่รองรับ competing consumers
  → แนะนำใช้ **Redis Streams (Consumer Groups)** หรือ Kafka/RabbitMQ แทน

* **Use Cases**

    * Fan-out → ใช้ Pub/Sub ได้เลย แต่ต้องทำ idempotent
    * Load balance → ใช้ Streams/Kafka
    * Partitioning → hash key → route ไป instance เฉพาะ
