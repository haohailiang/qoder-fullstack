package com.ecommerce.order.consumer;

import com.ecommerce.common.SeckillMessage;
import com.ecommerce.order.client.ProductClient;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.order.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static com.ecommerce.order.config.SeckillRabbitConfig.SECKILL_QUEUE;

/**
 * 秒杀订单消费者：监听秒杀队列，异步创建订单
 */
@Component
public class SeckillOrderConsumer {

    private static final Logger log = LoggerFactory.getLogger(SeckillOrderConsumer.class);

    private final OrderRepository orderRepository;
    private final ProductClient productClient;

    public SeckillOrderConsumer(OrderRepository orderRepository,
                                ProductClient productClient) {
        this.orderRepository = orderRepository;
        this.productClient = productClient;
    }

    @RabbitListener(queues = SECKILL_QUEUE)
    public void onMessage(SeckillMessage message) {
        log.info("收到秒杀消息: orderNo={}, userId={}, productId={}",
                message.getOrderNo(), message.getUserId(), message.getProductId());

        try {
            // 1. 查询商品信息（通过 Feign 调用商品服务）
            var productResult = productClient.deductStock(message.getProductId(), 0);
            // 注意：这里只是验证商品存在，实际库存已在 Redis 预扣减

            // 2. 创建订单记录
            Order order = new Order();
            order.setOrderNo(message.getOrderNo());
            order.setUserId(message.getUserId());
            order.setProductId(message.getProductId());
            order.setProductName("秒杀商品-" + message.getProductId());
            order.setQuantity(message.getQuantity());
            order.setTotalAmount(BigDecimal.ZERO); // 秒杀价格由商品服务计算
            order.setStatus(1); // 已支付（秒杀直接标记已支付）
            order.setAddress("");
            order.setRemark("秒杀订单");
            order.setCreatedDate(LocalDateTime.now());
            order.setUpdatedDate(LocalDateTime.now());

            orderRepository.save(order);
            log.info("秒杀订单创建成功: orderNo={}, userId={}", message.getOrderNo(), message.getUserId());

        } catch (Exception e) {
            log.error("秒杀订单创建失败: orderNo={}, error={}", message.getOrderNo(), e.getMessage(), e);
            // 消费失败，消息会进入死信队列，后续可人工处理或定时重试
            // TODO: 回滚 Redis 库存 + 清除用户购买标记
            throw e; // 抛出异常触发重试
        }
    }
}
