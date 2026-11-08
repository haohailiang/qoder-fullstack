package com.ecommerce.order.service;

import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.enums.OrderStatus;

import java.util.List;

public interface OrderService {

    /** 创建订单 */
    Order createOrder(Long userId, Order order);

    /** 查询用户的订单列表 */
    List<Order> getOrdersByUserId(Long userId);

    /** 根据订单号查询 */
    Order getOrderByOrderNo(String orderNo);

    /** 查询订单详情 */
    Order getOrderById(Long id);

    /** 取消订单（恢复库存） */
    void cancelOrder(Long orderId, Long userId);

    /** 更新订单状态 */
    void updateOrderStatus(Long orderId, OrderStatus status);

    /** 查询所有订单（管理用） */
    List<Order> getAllOrders();
}
