package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.redisson.api.RedissonClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedissonClient redissonClient;

    @Resource
    private IVoucherOrderService voucherOrderService;
    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private RocketMQTemplate rocketMQTemplate;
    //创建lua脚本对象
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));//设置lua脚本
        SECKILL_SCRIPT.setResultType(Long.class);//设置返回值类型
    }
    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        long orderId = redisIdWorker.nextId("order");
        // 执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,//传入lua脚本
                Collections.emptyList(),//传入lua脚本的key，这里没有
                voucherId.toString(), userId.toString(), String.valueOf(orderId)//传入lua脚本的参数：优惠券id，用户id，订单id
        );
        assert result != null;
        int r = result.intValue();
        if( r !=0)
        {
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }
        //异步发送到消息队列
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(userId);
        rocketMQTemplate.asyncSend("voucher_seckill_topic", voucherOrder, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                System.out.println(voucherOrder.getVoucherId()+"发送成功");
            }

            @Override
            public void onException(Throwable throwable) {
                System.out.println(voucherOrder.getVoucherId()+"发送失败:" + throwable.getMessage());
            }
        });
        return Result.ok(orderId);
    }

//    @Override
//    public Result seckillVoucher(Long voucherId) {
//
//        //使用redisson，不异步
//        //判断秒杀资格
//        Result result = isVoucherAvailable(voucherId);
//        if (!result.getSuccess()) {
//            return result;
//        }
//        //尝试获取锁
//        Long userId = UserHolder.getUser().getId();
////        //使用synchronized单机锁，不异步
////        synchronized (userId.toString().intern()) {
////            VoucherOrderServiceImpl proxy = (VoucherOrderServiceImpl) AopContext.currentProxy();
////            //4.返回结果
////            return proxy.createVoucherOrder(voucherId);
////        }
//        RLock lock = redissonClient.getLock(RedisConstants.LOCK_ORDER_KEY + userId);
//        boolean tryLock = lock.tryLock();
//        if (!tryLock) {
//            return Result.fail("不允许重复下单！");
//        }
//        try {
//            //生成订单
//            VoucherOrderServiceImpl proxy = (VoucherOrderServiceImpl) AopContext.currentProxy();
//            //返回结果
//            return proxy.createVoucherOrder(voucherId);
//        } finally {
//            //释放锁
//            lock.unlock();
//        }
//    }

    private Result isVoucherAvailable(Long voucherId) {
        //1.判断优惠卷库存,时间
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        if (voucher == null) {
            return Result.fail("秒杀优惠券不存在");
        }
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀未开始");
        }
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已结束");
        }
        if (voucher.getStock() <= 0) {
            return Result.fail("stock not enough");
        }
        //2.判断同一个是否已经抢购过
        Long userId = UserHolder.getUser().getId();
        Integer count = this.query().eq("voucher_id", voucherId).eq("user_id", userId).count();
        if (count > 0) {
            return Result.fail("请勿重复抢购");
        }
        return Result.ok();
    }

    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        //4.生成订单
        //  1.减库存
        // 6.扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1") // set stock = stock - 1
                .eq("voucher_id", voucherId).gt("stock", 0) // where id = ? and stock > 0
                .update();
        if (!success) {
            // 扣减失败
            return Result.fail("库存不足！");
        }
        //  2.生成订单
        long OrderId = redisIdWorker.nextId("voucher_order");
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(OrderId);
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(UserHolder.getUser().getId());
        save(voucherOrder);
        return Result.ok(OrderId);
    }
}
