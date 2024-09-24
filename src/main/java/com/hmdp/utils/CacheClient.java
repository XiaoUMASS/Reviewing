package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Component
@Slf4j
public class CacheClient {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    public void set(String key, Object value, Long time, TimeUnit unit) {
        //需要将value序列化为JSON
        String valueJSON = JSONUtil.toJsonStr(value);
        stringRedisTemplate.opsForValue().set(key, valueJSON, time, unit);
    }

    public void setWithLogicalExpiration(String key, Object value, Long time, TimeUnit unit) {
        //需要将value序列化为JSON
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        //将redisData对象转换为JSON
        String redisDataJSON = JSONUtil.toJsonStr(redisData);
        stringRedisTemplate.opsForValue().set(key, redisDataJSON);
    }

    public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type,
                                          Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        //从Redis中查询商铺缓存
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        //若存在，返回
        //这里由于isNotBlank在""也会返回false，所以这里返回的是正常的店铺数据
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, type);
        }
        //判断缓存命中的是""，还是不存在于缓存，后者才去查数据库
        if ("".equals(json)) {
            return null;
        }
        //不存在于缓存，根据id查询数据库
        R r = dbFallback.apply(id);
        //数据库中查询不存在，返回错误，将空值写入缓存
        if (r == null) {
            //防止缓存穿透，采用将空值写入缓存的方案
            //空值的TTL要短很多
            stringRedisTemplate.opsForValue()
                    .set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //数据库中查询存在
        //将查到的数据写入Redis
        this.set(key, r, time, unit);
        //将查到的数据返回
        return r;
    }

    public <R, ID> R queryWithLogicalExpiration(String keyPrefix, ID id, Class<R> type,
                                                Function<ID, R> dbFallback, Long time, TimeUnit unit ) {
        //由于有缓存预热，这里可以不用考虑缓存穿透的情况（即有效的请求必将命中缓存）
        //从Redis中查询商铺缓存
        String key = keyPrefix + id;
        String redisDataJSON = stringRedisTemplate.opsForValue().get(key);
        //未命中缓存，返回（由于我们做了缓存预热，这里可以断定是无效请求）
        if (StrUtil.isBlank(redisDataJSON)) {
            return null;
        }
        //命中，需要把JSON反序列化为对象
        //注意这里的redisDataJSON是封装逻辑过期时间后的RedisData类
        RedisData redisData = JSONUtil.toBean(redisDataJSON, RedisData.class);
        JSONObject jsonObject = (JSONObject) redisData.getData();//得到店铺的json格式的信息
        R r = JSONUtil.toBean(jsonObject, type);
        LocalDateTime expireTime = redisData.getExpireTime();
        //判断是否逻辑过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            //未过期，直接返回
            return r;
        }
        //已过期，需要缓存重建
        //获取互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + key;
        //判断是否获取锁成功
        boolean isLock = tryLock(lockKey);
        //成功，开启独立线程，实现缓存重建
        if (isLock) {
            //再次检测redis缓存是否过期
            redisDataJSON = stringRedisTemplate.opsForValue().get(key);
            //未命中缓存，返回（由于我们做了缓存预热，这里可以断定是无效请求）
            if (StrUtil.isBlank(redisDataJSON)) {
                return null;
            }
            //命中，需要把JSON反序列化为对象
            //注意这里的redisDataJSON是封装逻辑过期时间后的RedisData类
            redisData = JSONUtil.toBean(redisDataJSON, RedisData.class);
            jsonObject = (JSONObject) redisData.getData();//得到店铺的json格式的信息
            r = JSONUtil.toBean(jsonObject, type);
            expireTime = redisData.getExpireTime();
            //判断是否逻辑过期
            if (expireTime.isAfter(LocalDateTime.now())) {
                //未过期，直接返回
                return r;
            }
            //调用线程池中的线程
            CacheRebuildExecutor.submit(() -> {
                //重建缓存
                try {
                    R r1 = dbFallback.apply(id);
                    setWithLogicalExpiration(key, r1, time, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //释放锁
                    unlock(lockKey);
                }
            });
        }
        //失败，直接返回逻辑过期的旧数据
        return r;
    }

    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    //释放互斥锁
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

    private final ExecutorService CacheRebuildExecutor = Executors.newFixedThreadPool(10);
}
