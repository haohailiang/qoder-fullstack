package com.ecommerce.order.service;

import com.ecommerce.order.dto.PaymentCreateResponse;
import com.ecommerce.order.dto.PaymentStatusResponse;
import com.ecommerce.order.entity.Order;

import java.util.Map;

/**
 * 支付服务：创建支付、处理异步通知、主动查询支付结果
 */
public interface PaymentService {

    /**
     * 为待支付订单创建支付宝支付，返回收银台地址。
     *
     * @param order 订单（调用方已完成归属校验）
     * @return 支付跳转信息
     */
    PaymentCreateResponse createPayment(Order order);

    /**
     * 查询支付状态，sync=true 时先向支付宝主动查询并同步本地状态。
     */
    PaymentStatusResponse queryStatus(Order order, boolean sync);

    /**
     * 处理支付宝异步通知。
     *
     * @param params 支付宝 POST 表单参数
     * @return 纯文本 success / failure（支付宝据此决定是否重试）
     */
    String handleNotify(Map<String, String> params);
}