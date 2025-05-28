package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisIdWorker;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

@SpringBootTest
class HmDianpingApplicationTests {

    @Resource
    private CacheClient cacheClient;

    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private RedisIdWorker redisIdWorker;

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



}
