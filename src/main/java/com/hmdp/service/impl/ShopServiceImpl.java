package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    /**
     * 根据商铺id查询商铺信息
     * @param id 商铺id
     * @return
     */
    @Override
    public Result queryById(Long id) {
        String key = CACHE_SHOP_KEY + id;
        //1.从redis中查询商铺信息
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if(StrUtil.isNotBlank(shopJson)){
            //2.1如果真实存在，直接返回
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            //2.2将查询到的商铺信息返回
            return Result.ok(shop);
        }
        //3.判断命中的是否是空值
        if (shopJson != null) {
            //3.1.如果命中的是空值""，返回错误信息
            return Result.fail("商铺不存在");
        }
        //4..如果不存在，根据id查询数据库
        Shop shop = getById(id);
        //5..如果数据库中不存在该商铺信息
        if(shop == null){
            //5.1将空值写入redis，设置有效时间，避免缓存穿透
            stringRedisTemplate.opsForValue().set(key, "",CACHE_NULL_TTL, TimeUnit.MINUTES);
            //5.2返回错误信息
            return Result.fail("商铺不存在");
        }
        //6.数据库中存在，则将数据写入redis，并设置有效时间，并返回商铺信息
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
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
}
