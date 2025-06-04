package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import cn.hutool.core.util.StrUtil;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import jakarta.annotation.Resource;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.redis.connection.RedisGeoCommands.GeoLocation;


import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.DEFAULT_PAGE_SIZE;


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

    @Resource
    private CacheClient cacheClient;


    /**
     * 根据商铺id查询商铺信息
     * @param id 商铺id
     * @return
     */
    @Override
    public Result queryById(Long id) {
        //用缓存空值解决缓存穿透
        Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //逻辑过期解决缓存击穿
        //Type type = new TypeReference<RedisData<Shop>>(){}.getType();
        //Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY, id, type, this::getById, 20L, TimeUnit.SECONDS);
        //互斥锁解决缓存击穿
        //Shop shop = cacheClient.queryWithMutex(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        if(shop == null){
            //如果商铺信息不存在，返回错误信息
            return Result.fail("商铺不存在");
        }
        return Result.ok(shop);
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

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        //1.判断是否需要根据坐标查询
        if( x == null || y == null) {
            //1.1.不需要根据坐标查询，直接分页查询
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, DEFAULT_PAGE_SIZE));
            return Result.ok(page);
        }
        //2.计算分页参数
        int from = (current - 1) * DEFAULT_PAGE_SIZE;
        int end = current * DEFAULT_PAGE_SIZE;
        //3.查询redis,按照距离排序、分页  结果：shopId, distance
        String key = SHOP_GEO_KEY + typeId;
        GeoResults<GeoLocation<String>> results = stringRedisTemplate.opsForGeo()
                .search(
                        key,
                        GeoReference.fromCoordinate(x, y),
                        new Distance(5000),
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeCoordinates().includeDistance().limit(end)
                );
        if(results== null || results.getContent().isEmpty()) {
            //如果没有查询到结果，直接返回空
            return Result.ok(Collections.emptyList());
        }
        //4.解析出id
        List<GeoResult<GeoLocation<String>>> list = results.getContent();
        if(list.size()<= from) {
            //如果from大于等于结果集的大小，说明没有数据
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids = new ArrayList<>(list.size());
        Map<String, Distance> distanceMap = new HashMap<>(list.size());
        //4.1截取从from到end的部分
        list.stream().skip(from).forEach(result->{
            //4.2获取商铺id
            String shopId = result.getContent().getName();
            ids.add(Long.valueOf(shopId));
            //4.3获取商铺距离
            Distance distance = result.getDistance();
            distanceMap.put(shopId, distance);
        });
        //5.根据id查询商铺信息
        String idStr = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD(id, " + idStr + ")").list();
        //6.店铺，设置距离
        for (Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        return Result.ok(shops);
    }
}
