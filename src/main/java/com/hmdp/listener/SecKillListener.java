package com.hmdp.listener;

import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

@Slf4j
@Component
@RocketMQMessageListener(
        topic = "voucher_seckill_topic",
        consumerGroup = "secKillGroup")
public class SecKillListener implements RocketMQListener<VoucherOrder> {

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private IVoucherOrderService voucherOrderService;
    @Resource
    private RedissonClient redissonClient;

    @Transactional
    @Override
    public void onMessage(VoucherOrder voucherOrder) {

        //获取消息
        System.out.println("秒杀订单消息：" + voucherOrder);
        Long voucherId = voucherOrder.getVoucherId();
        Long userId = voucherOrder.getUserId();
        //处理消息
        RLock lock = redissonClient.getLock(RedisConstants.LOCK_ORDER_KEY + userId);
        boolean tryLock = lock.tryLock();
        if (!tryLock) {
            log.info(LocalDateTime.now()+"_"+userId+"_"+voucherId+"重复下单");
            return;
        }
        // 扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1") // set stock = stock - 1
                .eq("voucher_id", voucherId).gt("stock", 0) // where id = ? and stock > 0
                .update();
        if (!success) {
            // 扣减失败
            log.info(LocalDateTime.now()+"_"+userId+"_"+voucherId+"扣减库存失败");
        }
        voucherOrderService.save(voucherOrder);
    }
}
