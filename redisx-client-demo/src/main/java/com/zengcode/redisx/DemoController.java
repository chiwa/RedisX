package com.zengcode.redisx;

import org.springframework.web.bind.annotation.*;

@RestController
public class DemoController {

    private final DemoService demo;

    public DemoController(DemoService demo) { this.demo = demo; }

    @GetMapping("/t")
    public String t(@RequestParam String id) {
        return demo.getUserName(id);
    }
}