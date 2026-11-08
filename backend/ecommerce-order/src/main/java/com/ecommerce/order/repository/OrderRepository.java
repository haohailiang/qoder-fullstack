package com.ecommerce.order.repository;

import com.ecommerce.order.entity.Order;
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

    public int updateStatus(Long id, Integer status) {
        return orderMapper.updateStatus(id, status);
    }

    public List<Order> findAll() {
        return orderMapper.selectAll();
    }
}
