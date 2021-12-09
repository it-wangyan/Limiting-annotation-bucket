package com.demo.controller;

import com.demo.annotation.BucketLimiter;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author wangyan01
 */
@RestController
public class TestController {

    @RequestMapping("/test")
    @BucketLimiter(limit = 10, rate = 1)
    public String test() {
        return "success";
    }
}
