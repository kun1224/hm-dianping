package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

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
    private IVoucherService voucherService;

    @Resource
    private IVoucherOrderService voucherOrderService;
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedisIdWorker redisIdWorker;

    @Override
    public Result seckillVoucher(Long voucherId) {
        //1.查询数据库
        Voucher voucher = voucherService.query().eq("id", voucherId).one();
        //2.判断是否在有效时间范围
        if (voucher == null) {
            return Result.fail("优惠券不存在！");
        }
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("优惠券还未开始！");
        }
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("优惠券已经结束！");
        }
        //3.判断是否还有库存
        if (voucher.getStock() <= 0) {
            return Result.fail("优惠券已经抢完！");
        }
        //4.调用创建订单方法
        Long userId = UserHolder.getUser().getId();
        synchronized (userId.toString().intern()) {
            VoucherOrderServiceImpl proxy = (VoucherOrderServiceImpl) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucher);
        }
    }

    @Transactional
    public Result createVoucherOrder(Voucher voucher) {
        Long userId = UserHolder.getUser().getId();
        Long voucherId = voucher.getId();
        //1.判断该用户是否已有订单
        VoucherOrder voucherOrder = voucherOrderService.query().eq("user_id", userId).eq("voucher_id", voucherId).one();
        if (voucherOrder != null) {
            return Result.fail("您已经抢过了该优惠券！");
        }
        //2.扣减库存,更新数据库tb_voucher表,使用乐观锁
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1") // set stock = stock - 1
                .eq("voucher_id", voucherId).gt("stock", 0)// where id = ? and stock > 0
                .update();
        if (!success) {
            return Result.fail("优惠券已经抢完！");
        }
        //3.创建订单,插入数据库tb_voucher_order表
        long orderId = redisIdWorker.nextId("voucher_order");
        voucherOrder = new VoucherOrder();
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(userId);
        voucherOrder.setId(orderId);
        voucherOrderService.save(voucherOrder);
        //4.返回成功
        return Result.ok(orderId);
    }
}
