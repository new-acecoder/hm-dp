package com.hmdp.utils;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author Ace
 */
@Data
public class RedisData<T> {
    private LocalDateTime expireTime;
    // 缓存数据
    private T data;
}
