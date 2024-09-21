package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;


@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private IShopTypeService typeService;

    @Override
    public Result listShopType() throws JsonProcessingException {
        String tag = RedisConstants.CACHE_SHOP_KEY;
        //从Redis中查询商铺缓存
//        Set<String> shopTypeSet = stringRedisTemplate.opsForSet().members(tag);
        List<String> shopTypeJSONList = stringRedisTemplate.opsForList().range(tag, 0, -1);
        ArrayList<ShopType> shopTypeList = new ArrayList<>();
        ObjectMapper objectMapper = new ObjectMapper();
//        shopTypeList.addAll(shopTypeSet);
        if (shopTypeJSONList != null) {
            for (String shopTypeStr : shopTypeJSONList) {
                //将JSON转为ShopType
                ShopType shopType = objectMapper.readValue(shopTypeStr, ShopType.class);
                shopTypeList.add(shopType);
            }
        }
        //若存在，返回
        if (!shopTypeList.isEmpty()) {
            return Result.ok(shopTypeList);
        }
        //不存在，根据id查询数据库
        shopTypeList = (ArrayList<ShopType>) typeService.query().orderByAsc("sort").list();
        //数据库中查询不存在，返回错误
        if (shopTypeList.isEmpty()) {
            return Result.fail("店铺类型数据不存在");
        }
        //数据库中查询存在
        //将查到的数据写入Redis
//        for (ShopType shopType : shopTypeList) {
//            String shopTypeJSON = objectMapper.writeValueAsString(shopType);
////            stringRedisTemplate.opsForSet().add(tag, shopTypeJSON);
//            stringRedisTemplate.opsForList().rightPush();
//
//        }
        shopTypeJSONList = shopTypeList.stream().map(shopType -> {
            try {
                return objectMapper.writeValueAsString(shopType);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }).collect(Collectors.toList());
        stringRedisTemplate.opsForList().rightPushAll(tag, shopTypeJSONList);
        //将查到的数据返回
        return Result.ok(shopTypeList);
//        return Result.ok(typeService.query().orderByAsc("sort").list());
    }
}
