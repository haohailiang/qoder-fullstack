package com.ecommerce.order.service.impl;

import com.ecommerce.common.BusinessException;
import com.ecommerce.order.client.ProductClient;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.enums.OrderStatus;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.order.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
public class OrderServiceImpl implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);
    private static final DateTimeFormatter ORDER_NO_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final OrderRepository orderRepository;
    private final ProductClient productClient;

    public OrderServiceImpl(OrderRepository orderRepository, ProductClient productClient) {
        this.orderRepository = orderRepository;
        this.productClient = productClient;
    }

    @Override
    @Transactional
    public Order createOrder(Long userId, Order order) {
        String orderNo = generateOrderNo();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setCreatedDate(LocalDateTime.now());
        order.setUpdatedDate(LocalDateTime.now());

        // 调用商品服务扣减库存
        boolean deducted;
        try {
            var result = productClient.deductStock(order.getProductId(), order.getQuantity());
            deducted = result != null && result.getData() != null && result.getData();
        } catch (Exception e) {
            log.error("调用商品服务扣减库存失败: productId={}, quantity={}, error={}",
                    order.getProductId(), order.getQuantity(), e.getMessage());
            throw new BusinessException(500, "库存扣减失败，请稍后重试");
        }

        if (!deducted) {
            throw new BusinessException(400, "库存不足或商品不存在");
        }

        Order saved = orderRepository.save(order);
        log.info("订单创建成功: orderNo={}, userId={}, productId={}", orderNo, userId, order.getProductId());
        return saved;
    }

    @Override
    public List<Order> getOrdersByUserId(Long userId) {
        return orderRepository.findByUserId(userId);
    }

    @Override
    public Order getOrderByOrderNo(String orderNo) {
        Order order = orderRepository.findByOrderNo(orderNo);
        if (order == null) {
            throw new BusinessException(404, "订单不存在: " + orderNo);
        }
        return order;
    }

    @Override
    public Order getOrderById(Long id) {
        Order order = orderRepository.findById(id);
        if (order == null) {
            throw new BusinessException(404, "订单不存在: " + id);
        }
        return order;
    }

    @Override
    @Transactional
    public void cancelOrder(Long orderId, Long userId) {
        Order order = getOrderById(orderId);
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权操作此订单");
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new BusinessException(400, "当前订单状态不可取消");
        }
        orderRepository.updateStatus(orderId, OrderStatus.CANCELLED);
        log.info("订单已取消: orderId={}, orderNo={}", orderId, order.getOrderNo());
        // TODO: Phase 3 通过 MQ 通知商品服务恢复库存
    }

    @Override
    public void updateOrderStatus(Long orderId, OrderStatus status) {
        getOrderById(orderId);
        orderRepository.updateStatus(orderId, status);
        log.info("订单状态更新: orderId={}, newStatus={}", orderId, status.getDescription());
    }

    @Override
    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    private String generateOrderNo() {
        String timestamp = LocalDateTime.now().format(ORDER_NO_FORMAT);
        String uuid = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        return "ORD" + timestamp + uuid;
    }
}
