package com.ecommerce.order.repository.mapper;

import com.ecommerce.order.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OrderMapper {

    List<Order> selectByUserId(@Param("userId") Long userId);

    Order selectById(@Param("id") Long id);

    Order selectByOrderNo(@Param("orderNo") String orderNo);

    int insert(Order order);

    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    List<Order> selectAll();
}
