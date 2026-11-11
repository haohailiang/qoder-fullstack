package com.ecommerce.order.consumer;

import com.ecommerce.common.BusinessException;
import com.ecommerce.common.SeckillMessage;
import com.ecommerce.order.client.ProductClient;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.enums.OrderStatus;
import com.ecommerce.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static com.ecommerce.common.config.SeckillRabbitConfig.SECKILL_QUEUE;

/**
 * 秒杀订单消费者：监听秒杀队列，异步创建订单。
 *
 * <p>确认方式为 AUTO（见 application.yml）：正常返回即 ack，抛出异常则由
 * spring-rabbit 重试 3 次，仍失败则进入死信队列，由 {@link SeckillDlqConsumer} 回滚 Redis 预扣库存。
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

        // 1. 幂等检查：重试或重复投递时同一 orderNo 只应产生一笔订单
        if (orderRepository.findByOrderNo(message.getOrderNo()) != null) {
            log.warn("秒杀订单已存在，跳过重复消费: orderNo={}", message.getOrderNo());
            return;
        }

        // 2. 扣减 MySQL 真实库存：Redis 只承担流量削峰，数据库才是最终库存口径
        boolean deducted;
        try {
            var productResult = productClient.deductStock(message.getProductId(), message.getQuantity());
            deducted = productResult != null && Boolean.TRUE.equals(productResult.getData());
        } catch (Exception e) {
            log.error("扣减商品库存异常: orderNo={}, productId={}, error={}",
                    message.getOrderNo(), message.getProductId(), e.getMessage());
            throw new BusinessException(500, "扣减商品库存异常，待重试");
        }
        if (!deducted) {
            log.error("商品库存不足或商品不存在，转入死信补偿: orderNo={}, productId={}",
                    message.getOrderNo(), message.getProductId());
            throw new BusinessException(400, "商品库存不足或商品不存在");
        }

        // 3. 创建订单记录
        Order order = new Order();
        order.setOrderNo(message.getOrderNo());
        order.setUserId(message.getUserId());
        order.setProductId(message.getProductId());
        order.setProductName(message.getProductName() != null
                ? message.getProductName() : "秒杀商品-" + message.getProductId());
        order.setQuantity(message.getQuantity());
        // 金额 = 发送端快照的秒杀单价 × 数量，无价格快照时兜底为 0
        BigDecimal unitPrice = message.getPrice() == null ? BigDecimal.ZERO : message.getPrice();
        order.setTotalAmount(unitPrice.multiply(BigDecimal.valueOf(message.getQuantity())));
        order.setStatus(OrderStatus.PAID); // 已支付（秒杀直接标记已支付）
        order.setAddress("");
        order.setRemark("秒杀订单");
        order.setCreatedDate(LocalDateTime.now());
        order.setUpdatedDate(LocalDateTime.now());

        try {
            orderRepository.save(order);
            log.info("秒杀订单创建成功: orderNo={}, userId={}, totalAmount={}",
                    message.getOrderNo(), message.getUserId(), order.getTotalAmount());
        } catch (DuplicateKeyException e) {
            // 唯一索引 uk_order_no 兜底：重复消息不再抛异常，避免同一消息反复重试直至死信
            log.warn("秒杀订单唯一索引冲突，视为已处理: orderNo={}", message.getOrderNo());
        }
    }
}
