package com.hmdp.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;


/**
 * 商铺类型服务实现类
 * @author Ace
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final String key = CACHE_SHOP_TYPE_KEY;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    /**
     * 查询商铺类型列表
     * @return 商铺类型列表
     */
    @Override
    public Result queryTypeList() throws JsonProcessingException {
        //1.从redis中查询商铺信息
        String shopTypeListJson = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if (StrUtil.isNotBlank(shopTypeListJson)) {
            //3.如果存在，直接返回
            // 将JSON字符串转换为List<ShopType>
            List<ShopType> shopTypeList = MAPPER.readValue(
                    shopTypeListJson,
                    new TypeReference<>() {
                    });
            //log.debug("从Redis中查询商铺类型列表");
            return Result.ok(shopTypeList);
        }
        //4.如果不存在，查询数据库
        List<ShopType> shopTypeList = this.query().orderByAsc("sort").list();
        if (CollUtil.isEmpty(shopTypeList)) {
            return Result.fail("店铺类型为空");
        }

        //6.数据库中存在，则将数据写入redis，并返回商铺信息
        stringRedisTemplate.opsForValue().set(key, MAPPER.writeValueAsString(shopTypeList));
        //log.debug("从数据库中查询商铺类型列表");
        return Result.ok(shopTypeList);
    }
}
