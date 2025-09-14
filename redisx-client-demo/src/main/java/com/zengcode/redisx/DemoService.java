package com.zengcode.redisx;

import com.zengcode.redisx.annotation.CacheableX;
import org.springframework.stereotype.Service;

@Service
public class DemoService {
    @CacheableX(cacheName = "demo", key = "'user:'+ #id", ttlSeconds = 60)
    public String getUserName(String id) {
        System.out.println(">> hit real method");
        return "user-" + id;
    }
}
