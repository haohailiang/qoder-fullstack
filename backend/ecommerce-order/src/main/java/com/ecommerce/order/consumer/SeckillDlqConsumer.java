package com.ecommerce.order.consumer;

import com.ecommerce.common.SeckillMessage;
import com.ecommerce.order.client.ProductClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import static com.ecommerce.common.config.SeckillRabbitConfig.SECKILL_DLX_QUEUE;

/**
 * 秒杀死信消费者：消费重试 3 次仍失败后进入死信队列的消息。
 *
 * <p>职责是补偿回滚——调用商品服务恢复 Redis 预扣库存并清除用户购买标记，
 * 避免「用户抢到了但订单没落库，库存却被吃掉」的资损。
 * 补偿本身失败时只记录日志，不再抛出异常（否则死信会无限重回队列）。
 */
@Component
public class SeckillDlqConsumer {

    private static final Logger log = LoggerFactory.getLogger(SeckillDlqConsumer.class);

    private final ProductClient productClient;

    public SeckillDlqConsumer(ProductClient productClient) {
        this.productClient = productClient;
    }

    @RabbitListener(queues = SECKILL_DLX_QUEUE)
    public void onMessage(SeckillMessage message) {
        log.error("秒杀订单进入死信队列，开始库存补偿: orderNo={}, userId={}, productId={}, quantity={}",
                message.getOrderNo(), message.getUserId(), message.getProductId(), message.getQuantity());
        try {
            productClient.rollbackSeckillStock(message.getProductId(), message.getQuantity(), message.getUserId());
            log.info("秒杀库存补偿完成: orderNo={}", message.getOrderNo());
        } catch (Exception e) {
            log.error("秒杀库存补偿失败，需人工介入: orderNo={}, productId={}, error={}",
                    message.getOrderNo(), message.getProductId(), e.getMessage(), e);
        }
    }
}
