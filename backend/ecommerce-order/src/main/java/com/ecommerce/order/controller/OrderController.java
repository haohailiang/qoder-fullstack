package com.ecommerce.order.controller;

import com.ecommerce.common.Result;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.security.TrustedAuthenticationFilter;
import com.ecommerce.order.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /** 创建订单 */
    @PostMapping
    public Result<Order> createOrder(@Valid @RequestBody Order order, HttpServletRequest request) {
        Long userId = getUserIdFromHeader(request);
        return Result.success(orderService.createOrder(userId, order));
    }

    /** 查询当前用户的订单列表 */
    @GetMapping("/my")
    public Result<List<Order>> getMyOrders(HttpServletRequest request) {
        Long userId = getUserIdFromHeader(request);
        return Result.success(orderService.getOrdersByUserId(userId));
    }

    /** 根据订单号查询 */
    @GetMapping("/{orderNo}")
    public Result<Order> getOrderByOrderNo(@PathVariable String orderNo) {
        return Result.success(orderService.getOrderByOrderNo(orderNo));
    }

    /** 查询所有订单（管理用） */
    @GetMapping
    public Result<List<Order>> getAllOrders() {
        return Result.success(orderService.getAllOrders());
    }

    /** 取消订单 */
    @PostMapping("/{orderId}/cancel")
    public Result<Void> cancelOrder(@PathVariable Long orderId, HttpServletRequest request) {
        Long userId = getUserIdFromHeader(request);
        orderService.cancelOrder(orderId, userId);
        return Result.success(null);
    }

    /** 更新订单状态 */
    @PutMapping("/{orderId}/status")
    public Result<Void> updateStatus(@PathVariable Long orderId, @RequestParam Integer status) {
        orderService.updateOrderStatus(orderId, status);
        return Result.success(null);
    }

    private Long getUserIdFromHeader(HttpServletRequest request) {
        String userIdStr = request.getHeader(TrustedAuthenticationFilter.HEADER_USER_ID);
        if (userIdStr == null) {
            throw new com.ecommerce.common.BusinessException(401, "未授权访问");
        }
        return Long.parseLong(userIdStr);
    }
}
