package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    public IShopService shopService;

    @Override
    public Result queryById(Long id) {
        //缓存穿透
//        Shop shop = queryWithPassThrough(id);
        //互斥锁解决缓存击穿的方案
//        Shop shop = queryWithMutex(id);
        //逻辑过期解决缓存击穿的方案
        Shop shop = queryWithLogicalExpiration(id);
        if(shop == null){
            return Result.fail("店铺不存在");
        }
        //将查到的数据返回
        return Result.ok(shop);
    }

    //获取互斥锁
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    //释放互斥锁
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

    @Override
    @Transactional//要使用事务保障数据库和缓存的一致性
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
        //1。先更新数据库
        shopService.updateById(shop);
        //2。然后删除缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + id);
        return Result.ok();
    }

    public Shop queryWithPassThrough(Long id) {
        //从Redis中查询商铺缓存
        String shopJSON = stringRedisTemplate.opsForValue()
                .get(RedisConstants.CACHE_SHOP_KEY + id);
        //若存在，返回
        //这里由于isNotBlank在""也会返回false，所以这里返回的是正常的店铺数据
        if (StrUtil.isNotBlank(shopJSON)) {
            return JSONUtil.toBean(shopJSON, Shop.class);
        }
        //判断缓存命中的是""，还是不存在于缓存，后者才去查数据库
        if ("".equals(shopJSON)) {
            return null;
        }
        //不存在于缓存，根据id查询数据库
        Shop shop = getById(id);
        //数据库中查询不存在，返回错误，将空值写入缓存
        if (shop == null) {
            //防止缓存穿透，采用将空值写入缓存的方案
            //空值的TTL要短很多
            stringRedisTemplate.opsForValue()
                    .set(RedisConstants.CACHE_SHOP_KEY + id, "",
                            RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //数据库中查询存在
        //将查到的数据写入Redis
        shopJSON = JSONUtil.toJsonStr(shop);
        stringRedisTemplate.opsForValue()
                .set(RedisConstants.CACHE_SHOP_KEY + id, shopJSON,
                        RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //将查到的数据返回
        return shop;
    }

    public Shop queryWithMutex(Long id) {
        //从Redis中查询商铺缓存
        String shopJSON = stringRedisTemplate.opsForValue()
                .get(RedisConstants.CACHE_SHOP_KEY + id);
        //若存在，返回
        //这里由于isNotBlank在""也会返回false，所以这里返回的是正常的店铺数据
        if (StrUtil.isNotBlank(shopJSON)) {
            return JSONUtil.toBean(shopJSON, Shop.class);
        }
        //判断缓存命中的是""，还是不存在于缓存，后者才去查数据库
        if ("".equals(shopJSON)) {
            return null;
        }
        //不存在于缓存，尝试获取互斥锁以操作数据库
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;//每个店铺信息都可以对应一把互斥锁
        Shop shop = null;
        try {
            boolean isLock = tryLock(lockKey);
            //判断是否获取成功
            if(isLock){
                //再次检测redis缓存是否存在，如果存在（之前的数据库修改操作已经完成）则无需重建缓存
                shopJSON = stringRedisTemplate.opsForValue()
                        .get(RedisConstants.CACHE_SHOP_KEY + id);
                //若存在，返回
                if (StrUtil.isNotBlank(shopJSON)) {
                    return JSONUtil.toBean(shopJSON, Shop.class);
                }
                //判断缓存命中的是""，还是不存在于缓存，后者才去查数据库
                if ("".equals(shopJSON)) {
                    return null;
                }
            }
            //失败，则休眠并重试
            else{
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            //成功，则根据id查询数据库
            //根据id查询数据库
            shop = getById(id);
            //模拟重建延时
            Thread.sleep(200);
            //数据库中查询不存在，返回错误，将空值写入缓存
            if (shop == null) {
                //防止缓存穿透，采用将空值写入缓存的方案
                //空值的TTL要短很多
                stringRedisTemplate.opsForValue()
                        .set(RedisConstants.CACHE_SHOP_KEY + id, "",
                                RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            //数据库中查询存在
            //将查到的数据写入Redis
            shopJSON = JSONUtil.toJsonStr(shop);
            stringRedisTemplate.opsForValue()
                    .set(RedisConstants.CACHE_SHOP_KEY + id, shopJSON,
                            RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //释放互斥锁
            unlock(lockKey);
        }
        //将查到的数据返回
        return shop;
    }

    //将shop信息封装到具有逻辑过期功能的RedisData类中
    public void saveShop2Redis(Long id, Long expireSeconds) throws InterruptedException {
        //查询店铺数据
        Shop shop = getById(id);
//        Thread.sleep(200);
        //封装
        RedisData redisData = new RedisData(LocalDateTime.now().plusSeconds(expireSeconds), shop);
        //写入redis
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY+id, JSONUtil.toJsonStr(redisData));
    }

    public Shop queryWithLogicalExpiration(Long id) {
        //由于有缓存预热，这里可以不用考虑缓存穿透的情况（即有效的请求必将命中缓存）
        //从Redis中查询商铺缓存
        String redisDataJSON = stringRedisTemplate.opsForValue()
                .get(RedisConstants.CACHE_SHOP_KEY + id);
        //未命中缓存，返回（由于我们做了缓存预热，这里可以断定是无效请求）
        if (StrUtil.isBlank(redisDataJSON)) {
            return null;
        }
        //命中，需要把JSON反序列化为对象
        //注意这里的redisDataJSON是封装逻辑过期时间后的RedisData类
        RedisData redisData = JSONUtil.toBean(redisDataJSON, RedisData.class);
        JSONObject shopJSON = (JSONObject) redisData.getData();//得到店铺的json格式的信息
        Shop shop = JSONUtil.toBean(shopJSON, Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        //判断是否逻辑过期
        if(expireTime.isAfter(LocalDateTime.now())){
            //未过期，直接返回
            return shop;
        }
        //已过期，需要缓存重建
        //获取互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        //判断是否获取锁成功
        boolean isLock = tryLock(lockKey);
        //成功，开启独立线程，实现缓存重建
        if(isLock){
            //再次检测redis缓存是否过期
            redisDataJSON = stringRedisTemplate.opsForValue()
                    .get(RedisConstants.CACHE_SHOP_KEY + id);
            //未命中缓存，返回（由于我们做了缓存预热，这里可以断定是无效请求）
            if (StrUtil.isBlank(redisDataJSON)) {
                return null;
            }
            //命中，需要把JSON反序列化为对象
            //注意这里的redisDataJSON是封装逻辑过期时间后的RedisData类
            redisData = JSONUtil.toBean(redisDataJSON, RedisData.class);
            shopJSON = (JSONObject) redisData.getData();//得到店铺的json格式的信息
            shop = JSONUtil.toBean(shopJSON, Shop.class);
            expireTime = redisData.getExpireTime();
            //判断是否逻辑过期
            if(expireTime.isAfter(LocalDateTime.now())){
                //未过期，直接返回
                return shop;
            }
            //调用线程池中的线程
            CacheRebuildExecutor.submit(() -> {
                //重建
                try {
                    this.saveShop2Redis(id, 20L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //释放锁
                    unlock(lockKey);
                }
            });
        }
        //失败，直接返回逻辑过期的旧数据
        return shop;
    }

    private final ExecutorService CacheRebuildExecutor = Executors.newFixedThreadPool(10);
}
