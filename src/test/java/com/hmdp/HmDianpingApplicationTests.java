package com.hmdp;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Shop;
import com.hmdp.entity.User;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.service.impl.UserServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisIdWorker;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.connection.RedisGeoCommands.GeoLocation;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.*;

@SpringBootTest
class HmDianpingApplicationTests {

    @Resource
    private CacheClient cacheClient;

    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private UserServiceImpl userService;

    private ExecutorService es = Executors.newFixedThreadPool(100);



    @Test
    void testIdWorker() throws InterruptedException {

        CountDownLatch latch = new CountDownLatch(2);
        long start = System.currentTimeMillis();
        Runnable task =()->{for(int i = 0;i<5;i++){
              long id = redisIdWorker.nextId("order");
              System.out.println("订单号：" + id);
          }
          latch.countDown();
        };
        //计时

        for(int i = 0;i<2;i++){
            es.submit(task);
        }

        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("耗时：" + (end - start) + "毫秒");
    }

    @Test
    void testSaveRedis() throws InterruptedException {
        Shop shop = shopService.getById(1L);
        cacheClient.setWithLogicalExpire(CACHE_SHOP_KEY + 1L, shop, 10L, TimeUnit.SECONDS);
    }

    @Test
    void testLoadUser() throws InterruptedException, IOException {
        List<User> users = userService.list();
        if (users == null || users.isEmpty()) {
            System.out.println("无用户数据！");
            return;
        }
        // 2. 创建 BufferedWriter 写入文件，每个 token 一行
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("tokens.txt"))) {
            for (User user : users) {
                // 转换为 UserDTO
                UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);

                // 生成 token
                String token = UUID.randomUUID().toString(true);

                // 转换为 Map<String, String> 写入 Redis
                Map<String, Object> userMap = BeanUtil.beanToMap(
                        userDTO,
                        new HashMap<>(),
                        CopyOptions.create()
                                .setIgnoreNullValue(true)
                                .setFieldValueEditor((k, v) -> v.toString())
                );
                String tokenKey = LOGIN_USER_KEY + token;
                stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
                // 写入一行
                writer.write(token);
                writer.newLine();
            }
        }
    }

    @Test
    void testLoadShopData(){
        //1.查询店铺信息
        List<Shop> list = shopService.list();
        //2.把店铺分组，按typeId分组,typeId一致的放到一个集合
        Map<Long,List<Shop>> map = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        //3.遍历map，获取每个typeId对应的店铺集合
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            //3.1 获取typeId
            Long typeId = entry.getKey();
            //3.2 获取店铺集合
            List<Shop> value = entry.getValue();
            String key = SHOP_GEO_KEY + typeId;
            //3.3 把店铺集合转换为RedisGeoCommands.GeoLocation对象集合
            List<GeoLocation<String>> locations = value.stream()
                    .map(shop -> new GeoLocation<>(
                            shop.getId().toString(),
                            new Point(shop.getX(), shop.getY())))
                    .collect(Collectors.toList());
            //4.把店铺集合添加到Redis的Geo集合中
            stringRedisTemplate.opsForGeo().add(key,locations);
        }
    }

}
