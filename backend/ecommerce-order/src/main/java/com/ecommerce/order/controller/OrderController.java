package com.ecommerce.order.controller;

import com.ecommerce.common.Result;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.security.TrustedAuthenticationFilter;
import com.ecommerce.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/orders")
@Tag(name = "订单管理", description = "订单创建、查询、取消、状态更新等接口")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /** 创建订单 */
    @PostMapping
    @Operation(summary = "创建订单", description = "创建新订单，需要提供商品 ID、数量、地址等信息")
    public Result<Order> createOrder(@Valid @RequestBody Order order, HttpServletRequest request) {
        Long userId = getUserIdFromHeader(request);
        return Result.success(orderService.createOrder(userId, order));
    }

    /** 查询当前用户的订单列表 */
    @GetMapping("/my")
    @Operation(summary = "查询我的订单", description = "查询当前登录用户的所有订单")
    public Result<List<Order>> getMyOrders(HttpServletRequest request) {
        Long userId = getUserIdFromHeader(request);
        return Result.success(orderService.getOrdersByUserId(userId));
    }

    /** 根据订单号查询 */
    @GetMapping("/{orderNo}")
    @Operation(summary = "查询订单详情", description = "根据订单号查询单个订单详细信息")
    public Result<Order> getOrderByOrderNo(@Parameter(description = "订单号") @PathVariable String orderNo) {
        return Result.success(orderService.getOrderByOrderNo(orderNo));
    }

    /** 查询所有订单（管理用） */
    @GetMapping
    @Operation(summary = "查询所有订单", description = "管理接口，查询所有订单列表")
    public Result<List<Order>> getAllOrders() {
        return Result.success(orderService.getAllOrders());
    }

    /** 取消订单 */
    @PostMapping("/{orderId}/cancel")
    @Operation(summary = "取消订单", description = "取消指定订单，仅待支付状态可取消")
    public Result<Void> cancelOrder(@Parameter(description = "订单 ID") @PathVariable Long orderId, HttpServletRequest request) {
        Long userId = getUserIdFromHeader(request);
        orderService.cancelOrder(orderId, userId);
        return Result.success(null);
    }

    /** 更新订单状态 */
    @PutMapping("/{orderId}/status")
    @Operation(summary = "更新订单状态", description = "管理接口，更新指定订单的状态")
    public Result<Void> updateStatus(@Parameter(description = "订单 ID") @PathVariable Long orderId, @Parameter(description = "目标状态") @RequestParam Integer status) {
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
