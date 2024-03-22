package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

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
//            //6.不存在则返回null
//            log.info("店铺不存在");
//            return Result.fail("店铺不存在");
            //不存在，则缓存空对象
            stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY+ id, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return Result.fail("店铺不存在");
        }

        //7.存在则存入redis并返回
//        Map<String, Object> shopMap = BeanUtil.beanToMap(shop);
        String jsonStr = JSONUtil.toJsonStr(shop);
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, jsonStr, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        log.info("将店铺信息存入redis:" + shop.getName());
        return Result.ok(shop);
    }


    public Result queryWithMutex(Long id) {
        //从redis查询店铺信息
        String json = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
        //是否命中
        if (StrUtil.isNotBlank(json)) {
            //存在且不为空，则返回
            return Result.ok(JSONUtil.toBean(json, Shop.class));
        }
        if (json != null) {//缓存存在，但为空值
            return Result.fail("店铺不存在");
        }
        //未命中，则尝试获取锁
        while (!tryLock(RedisConstants.LOCK_SHOP_KEY + id)) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        //获取锁成成功则rebuild
        //再次查询缓存判断
        String jsonStr = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
        if (StrUtil.isNotBlank(jsonStr)) {
            return Result.ok(JSONUtil.toBean(jsonStr, Shop.class));
        }
        //查询数据库
        Shop shop = getById(id);
        if (shop == null)
        {
            stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, "",
                    RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
        }
        //存入redis
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop),
                RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //释放锁
        unlock(RedisConstants.LOCK_SHOP_KEY + id);
        //返回
        return Result.ok(shop);
    }

    public Result queryWithLogicalExpire(Long id) {
        //缓存预热
        //从redis查询店铺信息
        String jsonStr = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);

        //是否命中
        if(jsonStr == null)
        {
            //未命中，返回null
            return Result.fail("店铺不存在");
        }
        //命中则判断是否过期
        RedisData redisData = JSONUtil.toBean(jsonStr, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        //未过期，返回
        if(redisData.getExpireTime().isAfter(LocalDateTime.now()))
        {
            return Result.ok(shop);
        }
        //过期则尝试获取锁
        if(!tryLock(RedisConstants.LOCK_SHOP_KEY + id))
        {
            return Result.ok(shop);
        }
        //获取锁成功则rebuild

        //开启新线程异步更新缓存
        //返回
        //释放锁
        return null;
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

    private boolean tryLock(String key)
    {
        Boolean bool = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 5, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(bool);
    }
    private void unlock(String key)
    {
        stringRedisTemplate.delete(key);
    }

}
