package com.demo.handler;

import com.demo.annotation.BucketLimiter;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

/**
 * @author wangyan01
 */
@Slf4j
@Aspect
@Component
public class BucketLimterHandler {

    @Resource
    private RedisTemplate redisTemplate;

    private DefaultRedisScript<Long> getRedisScript;

    @Value("${spring.application.name}")
    private String appName;

    @PostConstruct
    public void init() {
        getRedisScript = new DefaultRedisScript<>();
        getRedisScript.setResultType(Long.class);
        getRedisScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("bucketLimter.lua")));
        log.info("[令牌桶限流处理器]脚本加载完成");
    }

    @Around("@annotation(bucketLimiter)")
    public Object around(ProceedingJoinPoint point, BucketLimiter bucketLimiter) throws Throwable {
        Signature signature = point.getSignature();
        Object target = point.getTarget();
        if (!(signature instanceof MethodSignature)) {
            throw new IllegalArgumentException("注解未作用在方法上!");
        }
        // 获取值
        String key = getKey(signature, target);
        //拼接记录上次请求时间的key
        String lastMillRequestKey = key + "_" + bucketLimiter.last_mill_request_key();
        //初始化令牌桶的容量
        long limit = bucketLimiter.limit();
        //请求令牌的数量 每次请求消耗令牌的数量
        long permits = bucketLimiter.permits();
        //每秒放入桶中的令牌数 即流速
        long rate = bucketLimiter.rate();
        //当前系统时间
        long currMillTime = System.currentTimeMillis() / 1000;
        //拼接lua脚本参数
        List<String> keys = Arrays.asList(key, lastMillRequestKey);
        //调用lua脚本并执行
        Long result = (Long) redisTemplate.execute(getRedisScript, keys, limit, permits, rate, currMillTime);
        if (result == 0) {
            log.error("请求过于频繁，请稍后再试！");
        }else{
            if (log.isInfoEnabled()) {
                log.info("BucketLimterHandler[令牌桶限流处理器]限流执行结果-result={},请求[正常]响应", result);
            }
        }
        return point.proceed();
    }

    private String getKey(Signature signature, Object target) {
        try {
            //获取类路径
            String className = target.getClass().getName();
            //获取方法名
            String methodName = signature.getName();
            //获取服务器节点ip
            String serverAddress = InetAddress.getLocalHost().getHostAddress();
            //拼接 服务名称 + 类路径 + 方法名 + 服务器节点ip 为唯一key
            return appName + ":" + className + "." + methodName + "_" + serverAddress;
        } catch (UnknownHostException e) {
            log.error("获取节点ip失败");
        }
        return null;
    }
}
