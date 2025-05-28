package com.hmdp;

import com.hmdp.service.impl.ShopServiceImpl;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class HmDianpingApplicationTests {

    @Resource
    private ShopServiceImpl shopService;

    @Test
    void testSaveRedis() throws InterruptedException {
        // 测试保存商铺信息到 Redis
        shopService.saveToRedis(1L, 10L);
    }

}
