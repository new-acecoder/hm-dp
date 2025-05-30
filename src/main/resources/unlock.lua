--比较当前线程标识和锁中的线程标识是否一致
if (redis.call('get',KEYS[1]) == ARGV[1]) then
    --释放锁 del key
    return redis.call('del',KEYS[1])
end
--如果不一致则返回0
return 0
