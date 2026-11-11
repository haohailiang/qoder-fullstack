package com.ecommerce.order.controller;

import com.ecommerce.common.BusinessException;
import com.ecommerce.common.Result;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.enums.OrderStatus;
import com.ecommerce.common.security.TrustedAuthenticationFilter;
import com.ecommerce.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
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

    /**
     * 根据订单号查询
     * <p>归属校验：普通用户只能查看自己的订单，ADMIN 可查看全部（与 cancelOrder 的归属约定保持一致）
     */
    @GetMapping("/{orderNo}")
    @Operation(summary = "查询订单详情", description = "根据订单号查询单个订单详细信息，仅订单归属人或 ADMIN 可见")
    public Result<Order> getOrderByOrderNo(@Parameter(description = "订单号") @PathVariable String orderNo,
                                           HttpServletRequest request) {
        Order order = orderService.getOrderByOrderNo(orderNo);
        if (!isAdmin(request) && !order.getUserId().equals(getUserIdFromHeader(request))) {
            throw new BusinessException(403, "无权查看此订单");
        }
        return Result.success(order);
    }

    /** 查询所有订单（管理用，仅 ADMIN） */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
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

    /** 更新订单状态（管理用，仅 ADMIN） */
    @PutMapping("/{orderId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "更新订单状态", description = "管理接口，更新指定订单的状态。状态值：0-待支付，1-已支付，2-已发货，3-已完成，4-已取消")
    public Result<Void> updateStatus(@Parameter(description = "订单 ID") @PathVariable Long orderId,
                                     @Parameter(description = "目标状态（0-4）") @RequestParam Integer status) {
        OrderStatus orderStatus = OrderStatus.fromCode(status);
        if (orderStatus == null) {
            throw new BusinessException(400, "无效的订单状态值: " + status);
        }
        orderService.updateOrderStatus(orderId, orderStatus);
        return Result.success(null);
    }

    private Long getUserIdFromHeader(HttpServletRequest request) {
        String userIdStr = request.getHeader(TrustedAuthenticationFilter.HEADER_USER_ID);
        if (userIdStr == null) {
            throw new BusinessException(401, "未授权访问");
        }
        return Long.parseLong(userIdStr);
    }

    /** 是否拥有管理角色（角色由网关从 JWT 解析后注入 X-User-Role） */
    private boolean isAdmin(HttpServletRequest request) {
        return "ADMIN".equalsIgnoreCase(request.getHeader(TrustedAuthenticationFilter.HEADER_USER_ROLE));
    }
}
