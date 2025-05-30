package com.hmdp.utils;


import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

/**
 * @author Ace
 * @date 2025/5/28 14:23
 */
@Component
@Slf4j
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 存储任意对象
     * @param key
     * @param value
     * @param time
     * @param unit
     * @param <T>
     */
    public<T> void set(String key, T value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    /**
     * 带有逻辑过期的存储任意对象
     * @param key
     * @param value
     * @param time
     * @param unit
     * @param <T>
     */
    public<T> void setWithLogicalExpire(String key, T value, Long time, TimeUnit unit) {
        //把value转换为RedisData对象
        RedisData<T> redisData = new RedisData<>();
        redisData.setData(value);
        //设置逻辑过期时间
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        //将RedisData对象转换为JSON字符串存储到Redis中
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 查询缓存，解决缓存穿透问题
     * @param keyPrefix
     * @param id
     * @param type
     * @return
     * @param <R>
     * @param <ID>
     */
    public <R,ID> R queryWithPassThrough(
            String keyPrefix,
            ID id,
            Class<R> type,
            Function<ID,R> dbFallback,
            Long time,
            TimeUnit unit) {

        String key = keyPrefix + id;
        //1.从redis中查询商铺信息
        String json = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if( StrUtil.isNotBlank(json)) {
            //2.1如果真实存在，直接返回
            return JSONUtil.toBean(json, type);
        }
        //3.判断命中的是否是空值
        if (json != null) {
            //3.1.如果命中的是空值""，返回错误信息
            return null;
        }
        //4..如果不存在，根据id查询数据库
        R r = dbFallback.apply(id);
        //5..如果数据库中不存在该商铺信息
        if(r == null){
            //5.1将空值写入redis，设置有效时间，避免缓存穿透
            stringRedisTemplate.opsForValue().set(key, "",CACHE_NULL_TTL, TimeUnit.MINUTES);
            //5.2返回错误信息
            return null;
        }
        //6.数据库中存在，则将数据写入redis，并设置有效时间，并返回商铺信息
        this.set(key, r, time, unit);
        return r;
    }

    public <R,ID> R queryWithLogicalExpire(
            String keyPrefix,
            ID id,
            Type type,
            Function<ID, R> dbFallback,
            Long time,
            TimeUnit unit){
        String key = keyPrefix + id;
        //1.从redis中查询商铺信息
        String json = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if(StrUtil.isBlank(json)){
            //2.1如果不存在，直接返回null
            return null;
        }
        //3.如果命中，需要先将json反序列化为RedisData对象
        RedisData<R> redisData= JSON.parseObject(json, type);
        R r = redisData.getData();
        LocalDateTime expireTime = redisData.getExpireTime();
        //4.判断是否过期
        if(expireTime.isAfter(LocalDateTime.now())){
            //4.1如果未过期，直接返回商铺信息
            return r;
        }
        //如果过期，进行缓存重建
        //5.缓存重建
        //5.1获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        //5.2判断是否获取成功
        if (isLock) {
            // 加锁成功，开启异步线程重建缓存
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // 再次判断缓存是否过期，避免重复重建
                    String json2 = stringRedisTemplate.opsForValue().get(key);
                    //RedisData<R> redisData2 =  JSONUtil.toBean(json2, new TypeReference<RedisData<R>>() {}, false);
                    RedisData<R> redisData2= JSON.parseObject(json2, new TypeReference<RedisData<R>>() {});
                    assert redisData2 != null;
                    if (redisData2.getExpireTime().isBefore(LocalDateTime.now())) {
                        //重新查询数据库
                        R r2 = dbFallback.apply(id);
                        //写入Redis
                        this.setWithLogicalExpire(key, r2, time, unit);
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    // 异步任务执行完释放锁
                    unlock(lockKey);
                }
            });
        }
        //5.4直接返回旧的商铺信息
        return r;
    }
    /**
     * 查询缓存，使用互斥锁解决缓存击穿问题
     * @param keyPrefix redis key 前缀
     * @param id 业务 id
     * @param type 返回类型
     * @param dbFallback 回源查询方法（例如从数据库查）
     * @param time 缓存时间
     * @param unit 时间单位
     * @return 查询结果
     * @param <R> 返回类型
     * @param <ID> id 类型
     */
    public <R, ID> R queryWithMutex(
            String keyPrefix,
            ID id,
            Class<R> type,
            Function<ID, R> dbFallback,
            Long time,
            TimeUnit unit) {

        String key = keyPrefix + id;
        // 1. 查询 Redis 缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2. 判断是否命中缓存
        if (StrUtil.isNotBlank(json)) {
            return JSON.parseObject(json, type);
        }
        // 3. 判断是否是空值
        if (json != null) {
            return null;
        }
        // 4. 缓存未命中，尝试获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        R r = null;
        try {
            boolean isLock = tryLock(lockKey);
            if (!isLock) {
                // 获取锁失败，休眠一段时间后重试
                Thread.sleep(50);
                return queryWithMutex(keyPrefix, id, type, dbFallback, time, unit);
            }

            // 获取锁成功，再次查询 Redis，避免重复构建
            String cacheJson = stringRedisTemplate.opsForValue().get(key);
            if (StrUtil.isNotBlank(cacheJson)) {
                return JSON.parseObject(cacheJson, type);
            }

            // 查询数据库
            r = dbFallback.apply(id);
            if (r == null) {
                // 数据库不存在，将空值写入 Redis 防止穿透
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            // 写入缓存
            this.set(key, r, time, unit);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 释放锁
            unlock(lockKey);
        }
        return r;
    }

    /**
     * 尝试获取锁
     * @param  key
     * @return 是否获取成功
     */
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key,"1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 释放锁
     * @param key
     */
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

}
