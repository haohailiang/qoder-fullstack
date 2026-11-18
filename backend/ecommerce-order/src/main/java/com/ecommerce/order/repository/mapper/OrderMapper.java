package com.ecommerce.order.repository.mapper;

import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.enums.OrderStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OrderMapper {

    List<Order> selectByUserId(@Param("userId") Long userId);

    Order selectById(@Param("id") Long id);

    Order selectByOrderNo(@Param("orderNo") String orderNo);

    int insert(Order order);

    int updateStatus(@Param("id") Long id, @Param("status") OrderStatus status);

    /** 仅当当前状态等于 from 时才更新为 to，用于支付回调的幂等状态流转 */
    int updateStatusIfCurrent(@Param("id") Long id,
                              @Param("from") OrderStatus from,
                              @Param("to") OrderStatus to);

    List<Order> selectAll();
}
