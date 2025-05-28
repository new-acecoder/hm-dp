package com.hmdp.service.impl;

import cn.hutool.core.lang.TypeReference;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisData;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;


/**
 * 商铺服务实现类
 * 该类继承了 MyBatis-Plus 的 ServiceImpl 类，并实现了 IShopService 接口。
 * 提供了根据商铺 ID 查询商铺信息的功能，并使用 Redis 缓存来提高查询效率。
 * @author Ace
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    //线程池，用于缓存重建
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 根据商铺id查询商铺信息
     * @param id 商铺id
     * @return
     */
    @Override
    public Result queryById(Long id) {
        //用缓存空值解决缓存穿透
        //Shop shop = queryWithPassThrough(id);

        //用互斥锁解决缓存击穿
        //Shop shop = queryWithMutex(id);
        //逻辑过期解决缓存击穿
        Shop shop = queryWithLogicalExpire(id);
        if(shop == null){
            //如果商铺信息不存在，返回错误信息
            return Result.fail("商铺不存在");
        }
        return Result.ok(shop);
    }

    /**
     * 逻辑过期解决缓存击穿问题
     * @param id
     * @return
     */
    public Shop queryWithLogicalExpire(Long id){
        String key = CACHE_SHOP_KEY + id;
        //1.从redis中查询商铺信息
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if(StrUtil.isBlank(shopJson)){
            //2.1如果不存在，直接返回null
            return null;
        }
        //3.如果命中，需要先将json反序列化为RedisData对象
        RedisData<Shop> redisData = JSONUtil.toBean(shopJson, new TypeReference<>() {}, false);
        Shop shop = redisData.getData();
        LocalDateTime expireTime = redisData.getExpireTime();
        //4.判断是否过期
        if(expireTime.isAfter(LocalDateTime.now())){
            //4.1如果未过期，直接返回商铺信息
            return shop;
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
                    String shopJson2 = stringRedisTemplate.opsForValue().get(key);
                    RedisData<Shop> redisData2 = JSONUtil.toBean(shopJson2, new TypeReference<>() {}, false);
                    if (redisData2.getExpireTime().isBefore(LocalDateTime.now())) {
                        this.saveToRedis(id, 20L);
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
        return shop;
    }

    /**
     * 用互斥锁解决缓存击穿问题
     * @param id 商铺id
     * @return 商铺信息
     */
    public Shop queryWithMutex(Long id){
        String key = CACHE_SHOP_KEY + id;
        //1.从redis中查询商铺信息
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if(StrUtil.isNotBlank(shopJson)){
            //2.1如果真实存在，直接返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        //3.判断命中的是否是空值
        if (shopJson != null) {
            //3.1.如果命中的是空值""，返回错误信息
            return null;
        }
        //4.实现缓存重建
        //4.1获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        Shop shop = null;
        try {
            boolean isLock = tryLock(lockKey);
            //4.2判断是否获取成功
            if(!isLock){
                //4.3如果失败，休眠一段时间后重试
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            //4.4如果获取成功，查询数据库
            shop = getById(id);
            //模拟数据库重建延时
            //Thread.sleep(200);
            //5..如果数据库中不存在该商铺信息
            if(shop == null){
                //5.1将空值写入redis，设置有效时间，避免缓存穿透
                stringRedisTemplate.opsForValue().set(key, "",CACHE_NULL_TTL, TimeUnit.MINUTES);
                //5.2返回错误信息
                return null;
            }
            //6.数据库中存在，则将数据写入redis，并设置有效时间，并返回商铺信息
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }finally {
            //7.释放互斥锁
            unlock(lockKey);
        }
        return shop;

    }
    /**
     * 解决缓存穿透问题
     * @param id
     * @return
     */
    public Shop queryWithPassThrough(Long id){
        String key = CACHE_SHOP_KEY + id;
        //1.从redis中查询商铺信息
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if(StrUtil.isNotBlank(shopJson)){
            //2.1如果真实存在，直接返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        //3.判断命中的是否是空值
        if (shopJson != null) {
            //3.1.如果命中的是空值""，返回错误信息
            return null;
        }
        //4..如果不存在，根据id查询数据库
        Shop shop = getById(id);
        //5..如果数据库中不存在该商铺信息
        if(shop == null){
            //5.1将空值写入redis，设置有效时间，避免缓存穿透
            stringRedisTemplate.opsForValue().set(key, "",CACHE_NULL_TTL, TimeUnit.MINUTES);
            //5.2返回错误信息
            return null;
        }
        //6.数据库中存在，则将数据写入redis，并设置有效时间，并返回商铺信息
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return shop;
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

    /**
     * 缓存预热
     * @param id
     */
    public void saveToRedis(Long id,Long expireSeconds) throws InterruptedException {
        //1.查询店铺数据
        Shop shop = getById(id);
        //模拟数据库重建延时
        //Thread.sleep(200);
        //2.封装逻辑过期时间
        RedisData<Shop> redisData = new RedisData<>();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        //3.写入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }


    /**
     * 更新商铺信息
     * @param shop
     * @return
     */
    @Transactional
    @Override
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("商铺id不能为空");
        }
        //1.先更新数据库
        updateById(shop);
        //2.删除缓存
        String key = CACHE_SHOP_KEY + id;
        stringRedisTemplate.delete(key);
        log.debug("删除缓存");
        return Result.ok();
    }
}
