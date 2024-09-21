package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;


@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    public IShopService shopService;

    @Override
    public Result queryById(Long id) {
        //从Redis中查询商铺缓存
        String shopJSON = stringRedisTemplate.opsForValue()
                .get(RedisConstants.CACHE_SHOP_KEY + id);
        //若存在，返回
        if (StrUtil.isNotBlank(shopJSON)) {
            Shop shop = JSONUtil.toBean(shopJSON, Shop.class);
            return Result.ok(shop);
        }
        //不存在，根据id查询数据库
        Shop shop = getById(id);
        //数据库中查询不存在，返回错误
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        //数据库中查询存在
        //将查到的数据写入Redis
        shopJSON = JSONUtil.toJsonStr(shop);
        stringRedisTemplate.opsForValue()
                .set(RedisConstants.CACHE_SHOP_KEY + id, shopJSON,
                        RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //将查到的数据返回
        return Result.ok(shop);
    }

    @Override
    @Transactional//要使用事务保障数据库和缓存的一致性
    public Result update(Shop shop) {
        Long id = shop.getId();
        if(id == null){
            return Result.fail("店铺id不能为空");
        }
        //1。先更新数据库
        shopService.updateById(shop);
        //2。然后删除缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + id);
        return Result.ok();
    }
}
