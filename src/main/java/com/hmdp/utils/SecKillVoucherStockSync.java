package com.hmdp.utils;

import com.hmdp.entity.SeckillVoucher;
import com.hmdp.service.ISeckillVoucherService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.annotation.PostConstruct;
import java.util.List;

/**
 * @Author: DLJD
 * @Date: 2023/4/24
 * 1.每天10点 晚上8点 通过定时任务 将mysql的库存 同步到redis中去
 * 2.为了测试方便 希望项目启动的时候 就同步数据
 */
@Component
public class SecKillVoucherStockSync {

    @Autowired
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * 我希望这个方法再项目启动以后
     * 并且再这个类的属性注入完毕以后执行
     * bean生命周期了
     * 实例化 new
     * 属性赋值
     * 初始化  (前PostConstruct/中InitializingBean/后BeanPostProcessor)
     * 使用
     * 销毁
     * ----------
     * 定位不一样
     */
//    @Scheduled(cron = "0 0 10 0 0 ?")
    @PostConstruct
    public void initData() {
        // 查询mysql的商品数据
        List<SeckillVoucher> seckillVoucherList = seckillVoucherService.query().list();
        if (CollectionUtils.isEmpty(seckillVoucherList)) {
            return;
        }
        // 将mysql的库存 同步到redis中去
        seckillVoucherList.forEach(seckillVoucher -> {
            redisTemplate.opsForValue().set("seckill:stock:"+seckillVoucher.getVoucherId(), seckillVoucher.getStock().toString());
        });
    }
}
