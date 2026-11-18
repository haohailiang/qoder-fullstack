package com.ecommerce.order.repository;

import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.enums.OrderStatus;
import com.ecommerce.order.repository.mapper.OrderMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class OrderRepository {

    private final OrderMapper orderMapper;

    public OrderRepository(OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    public List<Order> findByUserId(Long userId) {
        return orderMapper.selectByUserId(userId);
    }

    public Order findById(Long id) {
        return orderMapper.selectById(id);
    }

    public Order findByOrderNo(String orderNo) {
        return orderMapper.selectByOrderNo(orderNo);
    }

    public Order save(Order order) {
        orderMapper.insert(order);
        return order;
    }

    public int updateStatus(Long id, OrderStatus status) {
        return orderMapper.updateStatus(id, status);
    }

    /**
     * 条件更新：仅当订单当前状态为 from 时才流转到 to。
     *
     * @return 受影响行数，0 表示状态已被其他流程改变（幂等/并发保护）
     */
    public int updateStatusIfCurrent(Long id, OrderStatus from, OrderStatus to) {
        return orderMapper.updateStatusIfCurrent(id, from, to);
    }

    public List<Order> findAll() {
        return orderMapper.selectAll();
    }
}
