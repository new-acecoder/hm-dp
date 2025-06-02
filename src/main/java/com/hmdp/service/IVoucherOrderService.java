package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.transaction.annotation.Transactional;


public interface IVoucherOrderService extends IService<VoucherOrder> {

    Result seckillVoucher(Long voucherId);


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
    @Transactional
    void createVoucherOrder(VoucherOrder voucherOrder);
}
