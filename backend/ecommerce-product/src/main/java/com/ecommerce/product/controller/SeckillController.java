package com.ecommerce.product.controller;

import com.ecommerce.common.BusinessException;
import com.ecommerce.common.Result;
import com.ecommerce.common.SeckillMessage;
import com.ecommerce.product.entity.Product;
import com.ecommerce.common.security.TrustedAuthenticationFilter;
import com.ecommerce.product.service.SeckillStockService;
import com.ecommerce.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.ecommerce.common.config.SeckillRabbitConfig.*;

@RestController
@RequestMapping("/products/seckill")
@Tag(name = "秒杀管理", description = "秒杀库存管理、抢购等接口")
public class SeckillController {

    private static final Logger log = LoggerFactory.getLogger(SeckillController.class);

    private final SeckillStockService seckillStockService;
    private final ProductService productService;
    private final RabbitTemplate rabbitTemplate;

    public SeckillController(SeckillStockService seckillStockService,
                             ProductService productService,
                             RabbitTemplate rabbitTemplate) {
        this.seckillStockService = seckillStockService;
        this.productService = productService;
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * 初始化秒杀库存（管理接口，秒杀开始前调用）
     */
    @PostMapping("/init/{productId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "初始化秒杀库存", description = "管理接口，秒杀开始前调用，初始化指定商品的秒杀库存")
    public Result<Void> initStock(@Parameter(description = "商品 ID") @PathVariable Long productId,
                                  @Parameter(description = "库存数量") @RequestParam Integer stock) {
        seckillStockService.initStock(productId, stock);
        log.info("秒杀库存初始化: productId={}, stock={}", productId, stock);
        return Result.success(null);
    }

    /**
     * 秒杀抢购接口
     * POST /products/seckill/{productId}?quantity=1
     */
    @PostMapping("/{productId}")
    @Operation(summary = "秒杀抢购", description = "参与指定商品的秒杀活动，扣减库存并生成订单")
    public Result<Map<String, Object>> seckill(@Parameter(description = "商品 ID") @PathVariable Long productId,
                                                @Parameter(description = "购买数量") @RequestParam(defaultValue = "1") Integer quantity,
                                                HttpServletRequest request) {
        // 1. 获取用户信息（从网关注入的请求头）
        Long userId = getUserIdFromHeader(request);

        // 2. 校验商品是否存在且为秒杀商品
        Product product = productService.getProductById(productId);
        if (product.getIsSeckill() == null || product.getIsSeckill() != 1) {
            throw new BusinessException(400, "该商品不是秒杀商品");
        }

        // 3. 校验秒杀时间
        LocalDateTime now = LocalDateTime.now();
        if (product.getSeckillStartTime() == null || product.getSeckillEndTime() == null) {
            throw new BusinessException(400, "秒杀时间未配置");
        }
        if (now.isBefore(product.getSeckillStartTime())) {
            throw new BusinessException(400, "秒杀尚未开始");
        }
        if (now.isAfter(product.getSeckillEndTime())) {
            throw new BusinessException(400, "秒杀已结束");
        }

        // 4. 检查是否已购买（防重复）
        if (seckillStockService.hasBought(productId, userId)) {
            throw new BusinessException(400, "您已参与过该秒杀活动，不可重复购买");
        }

        // 5. 执行 Lua 脚本预扣减库存
        // 返回值语义：>=0 为扣减后剩余库存（0 = 刚好售罄，仍属成功），-1 未初始化，-2 库存不足
        Long remaining = seckillStockService.tryDecrStock(productId, quantity);
        if (remaining == null || remaining == SeckillStockService.RESULT_NOT_INITIALIZED) {
            throw new BusinessException(500, "秒杀库存未初始化");
        }
        if (remaining == SeckillStockService.RESULT_INSUFFICIENT || remaining < 0) {
            throw new BusinessException(400, "秒杀库存不足，已售罄");
        }

        // 6. 生成订单号，组装消息（商品名称与成交价在此刻快照，避免消费端二次跨服务取数）
        String orderNo = generateOrderNo();
        SeckillMessage message = new SeckillMessage();
        message.setUserId(userId);
        message.setProductId(productId);
        message.setQuantity(quantity);
        message.setProductName(product.getName());
        BigDecimal dealPrice = product.getSeckillPrice() != null ? product.getSeckillPrice() : product.getPrice();
        message.setPrice(dealPrice);
        message.setOrderNo(orderNo);
        message.setCreateTime(LocalDateTime.now());

        // 7. 发送 MQ：失败必须回滚 Redis 预扣的库存，否则库存凭空蒸发；
        //    用户购买标记在发送成功后才写入，保证失败时用户可以重试
        try {
            rabbitTemplate.convertAndSend(SECKILL_EXCHANGE, SECKILL_ROUTING_KEY, message);
        } catch (AmqpException e) {
            log.error("秒杀消息发送失败，回滚 Redis 库存: orderNo={}, productId={}, error={}",
                    orderNo, productId, e.getMessage(), e);
            seckillStockService.rollbackStock(productId, quantity);
            throw new BusinessException(500, "抢购失败，请重试");
        }

        // 8. 标记用户已购买（防重复）
        seckillStockService.markBought(productId, userId);
        log.info("秒杀消息已发送: orderNo={}, userId={}, productId={}, remaining={}",
                orderNo, userId, productId, remaining);

        // 9. 返回排队中状态
        Map<String, Object> result = new HashMap<>();
        result.put("orderNo", orderNo);
        result.put("status", "QUEUING");
        result.put("message", "抢购成功，正在排队中，请查询订单获取最终结果");
        return Result.success(result);
    }

    /**
     * 秒杀失败补偿（服务间内部接口）：
     * 订单服务消费失败并进入死信队列后调用，回滚 Redis 预扣库存并清除购买标记。
     * 网关已屏蔽外部访问该路径，仅允许下游服务直连调用。
     */
    @PostMapping("/rollback")
    @Operation(summary = "秒杀库存回滚", description = "内部接口：订单落库失败时回滚 Redis 预扣库存与用户购买标记")
    public Result<Void> rollback(@Parameter(description = "商品 ID") @RequestParam Long productId,
                                @Parameter(description = "回滚数量") @RequestParam Integer quantity,
                                @Parameter(description = "用户 ID") @RequestParam Long userId) {
        seckillStockService.rollbackStock(productId, quantity);
        seckillStockService.clearBought(productId, userId);
        log.warn("秒杀库存已回滚: productId={}, quantity={}, userId={}", productId, quantity, userId);
        return Result.success(null);
    }

    /**
     * 查询秒杀库存
     */
    @GetMapping("/stock/{productId}")
    @Operation(summary = "查询秒杀库存", description = "查询指定商品的秒杀剩余库存")
    public Result<Integer> getSeckillStock(@Parameter(description = "商品 ID") @PathVariable Long productId) {
        Integer stock = seckillStockService.getStock(productId);
        return Result.success(stock);
    }

    private Long getUserIdFromHeader(HttpServletRequest request) {
        String userIdStr = request.getHeader(TrustedAuthenticationFilter.HEADER_USER_ID);
        if (userIdStr == null) {
            throw new BusinessException(401, "未授权访问");
        }
        return Long.parseLong(userIdStr);
    }

    private String generateOrderNo() {
        String timestamp = LocalDateTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String uuid = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        return "SK" + timestamp + uuid;
    }
}
