package com.hmdp.utils;

import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * @author Ace
 * @date 2025/5/28 18:18
 */
@Component
public class RedisIdWorker {
    // 2025-01-01 00:00:00 UTC 开始时间戳
    private static final long BEGIN_TIMESTAMP = 1735689600L;
    //序列号的位数
    private static final int COUNT_BITS = 32;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public long nextId(String keyPrefix) {
        //1.生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;
        //2.生成序列号
        //获取当前日期精确到天
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        long count = stringRedisTemplate.opsForValue().increment("icr" + keyPrefix + ":" + date);
        //3.拼接并返回
        return timestamp << COUNT_BITS | count;
    }
}
