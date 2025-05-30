package com.hmdp.utils;

/**
 * @author Ace
 * @date 2025/5/29 14:20
 */
public interface ILock {
    /**
     * 尝试获取锁
     * @param timeoutSec 锁超时时间，单位秒
     * @return true 获取锁成功，false 获取锁失败
     */
    boolean tryLock(long timeoutSec);

    /**
     * 释放锁
     */
    void unlock();
}
