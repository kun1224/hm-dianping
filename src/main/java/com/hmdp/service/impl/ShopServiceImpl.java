package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result getShopById(Long id) {
        //1.从redis查询店铺信息
        String json = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
        Shop shop;
        //2.判断是否存在
        if (StrUtil.isNotBlank(json)) {
            //3.存在则返回
            shop = JSONUtil.toBean(json, Shop.class);
            log.info("从redis中查询到了店铺信息:" + shop.getName());
            return Result.ok(shop);
        }
        //4.不存在则查询数据库
        shop = getById(id);
        //5.判断是否存在
        if (shop == null) {
            //6.不存在则返回null
            log.info("店铺不存在");
            return Result.fail("店铺不存在");
        }
        //7.存在则存入redis并返回
//        Map<String, Object> shopMap = BeanUtil.beanToMap(shop);
        String jsonStr = JSONUtil.toJsonStr(shop);
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, jsonStr, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        log.info("将店铺信息存入redis:" + shop.getName());
        return Result.ok(shop);
    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺不存在");
        }
        //update
        updateById(shop);
        //delete cache
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + id);
        return Result.ok();
    }


}
