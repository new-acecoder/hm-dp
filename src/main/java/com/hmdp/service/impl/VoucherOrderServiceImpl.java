package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * @author Ace
 */
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    ISeckillVoucherService seckillVoucherService;

    @Resource
    RedisIdWorker redisIdWorker;

    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static{
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(
                new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    private final String queueName = "stream.orders";

    @PostConstruct
    private void init(){
        SECKILL_ORDER_EXECUTOR.submit(() -> {
            while (true) {
                try {
                    //1. 获取消息队列中的订单信息 XREAD GROUP g1 c1 BLOCK 2000 STREAMS streams.orders >
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            //1.1 指定消费组和消费者
                            Consumer.from("g1", "c1"),
                            //1.2 设置读取选项：最多读取 1 条，阻塞最多 2 秒
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            //1.3 指定从哪个 Stream 和偏移量读取（> 表示读取新消息）
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
                    );
                    //2.判断消息获取是否成功
                    if(list == null || list.isEmpty()){
                        //2.1如果获取失败，说明没有消息，继续下一次循环
                        continue;
                    }

                    //3.1解析消息中的订单信息
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
                    //3.2如果获取成功，可以下单
                    handleVoucherOrder(voucherOrder);
                    //4.ACK确认 SACK stream.orders g1 id
                    stringRedisTemplate.opsForStream().acknowledge(queueName,"g1",record.getId());

                } catch (Exception e) {
                    log.error("处理订单异常", e);
                    //没有被ack确认，在pendingList中处理异常消息
                    handlePendingList();
                }
            }
        });

    }

    private void handlePendingList() {

        while (true) {
            try {
                //1. 获取pendingList中的订单信息 XREAD GROUP g1 c1 STREAMS streams.orders 0
                List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                        //1.1 指定消费组和消费者
                        Consumer.from("g1", "c1"),
                        //1.2 设置读取选项：最多读取 1 条，不阻塞
                        StreamReadOptions.empty().count(1),
                        //1.3 指定从哪个 Stream 和偏移量读取（0 表示读取pendingList中的消息）
                        StreamOffset.create(queueName, ReadOffset.from("0"))
                );
                //2.判断消息获取是否成功
                if(list == null || list.isEmpty()){
                    //2.1如果获取失败，说明pendingList没有异常消息,结束循环
                    break;
                }

                //3.1解析pendingList中的订单信息
                MapRecord<String, Object, Object> record = list.get(0);
                Map<Object, Object> values = record.getValue();
                VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
                //3.2如果获取成功，可以下单
                handleVoucherOrder(voucherOrder);
                //4.ACK确认 SACK stream.orders g1 id
                stringRedisTemplate.opsForStream().acknowledge(queueName,"g1",record.getId());

            } catch (Exception e) {
                log.error("处理pendingList订单异常", e);
                try {
                    Thread.sleep(20);
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
            }
        }
    }


    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        //1.获取用户
        Long userId = voucherOrder.getUserId();
        //2.创建锁对象
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        //3.获取锁
        boolean isLock = lock.tryLock();
        //4.判断是否获取锁成功
        if (!isLock) {
            //获取锁失败，返回错误信息
            log.error("禁止重复下单，userId: {}", userId);
        }
        try {
            proxy.createVoucherOrder(voucherOrder);
        } finally {
            //释放锁
            lock.unlock();
        }

    }

    private IVoucherOrderService proxy;

    @Override
    public Result seckillVoucher(Long voucherId) {
        //获取用户
        Long userId = UserHolder.getUser().getId();
        //获取订单id
        long orderId = redisIdWorker.nextId("order");
        //1.执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(),String.valueOf(orderId)
        );
        //2.判断结果是否为0
        int r = result.intValue();
        if(r != 0){
            //2.1.不为0，没有购买资格
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }
        //3.获取代理对象
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        //4.返回订单id
        return Result.ok(orderId);
    }


    @Transactional
    @Override
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        //5.一人一单
        Long userId = voucherOrder.getUserId();
        //5.1 查询订单
        Long count = query().eq("user_id", userId)
                .eq("voucher_id", voucherOrder.getVoucherId()).count();
        //5.2判断用户是否已经购买过
        if (count > 0) {
            //5.3如果购买过，抛出异常
            log.error("用户已经下单，无法重复下单，userId: {}", userId);
        }
        //6.扣减库存
        //添加乐观锁，防止超卖,MYSQL中执行写的操作时默认加排他锁
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherOrder.getVoucherId()).gt("stock", 0)
                .update();
        if (!success) {
            log.error("库存不足");
        }
        save(voucherOrder);
    }
/*
    private final BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024*1024);
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    @PostConstruct
    private void init(){
        SECKILL_ORDER_EXECUTOR.submit(() -> {
            while (true) {
                try {
                    //1. 获取队列中的订单信息
                    VoucherOrder voucherOrder = orderTasks.take();
                    //2. 创建订单
                    handleVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    log.error("处理订单异常", e);
                }
            }
        });
    }*/



/*

    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        //1.执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString()
        );
        //2.判断结果是否为0
        int r = result.intValue();
        if(r != 0){
            //2.1.不为0，没有购买资格
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }
        //2.2.为0，表示有购买资格，把下单信息保存到阻塞队列中
        long orderId = redisIdWorker.nextId("order");
        //下单信息
        VoucherOrder voucherOrder = new VoucherOrder();
        //2.3 设置订单ID
        voucherOrder.setId(orderId);
        //2.4 设置用户ID
        voucherOrder.setUserId(userId);
        //2.5 设置秒杀券id
        voucherOrder.setVoucherId(voucherId);
        //2.6放到阻塞队列
        orderTasks.add(voucherOrder);
        //3.获取代理对象
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        //4.返回订单id
        return Result.ok(orderId);
    }
*/

    /*@Override
    public Result seckillVoucher(Long voucherId) {
        //1.先查询优惠券信息
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //2.判断秒杀是否开始，
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀未开始");
        }
        //3.是否已经结束
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已结束");
        }
        //4.判断库存是否充足
        if (voucher.getStock() < 1) {
            return Result.fail("库存不足");
        }
        Long userId = UserHolder.getUser().getId();

        //创建锁对象
        //SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
        //使用Redisson分布式锁
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        boolean isLock = lock.tryLock();
        //RedissonLock
        if (!isLock) {
            //获取锁失败，返回错误信息
            return Result.fail("请勿重复下单");
        }
        try {
            //拿到当前对象的代理对象
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        } finally {
            //释放锁
            lock.unlock();
        }
    }*/
}
