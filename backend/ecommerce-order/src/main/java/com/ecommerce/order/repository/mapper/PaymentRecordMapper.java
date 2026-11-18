package com.ecommerce.order.repository.mapper;

import com.ecommerce.order.entity.PaymentRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface PaymentRecordMapper {

    PaymentRecord selectByChannelAndOrderNo(@Param("channel") String channel, @Param("orderNo") String orderNo);

    /** 创建支付流水：唯一键冲突时静默忽略，保证重复发起支付不报错 */
    int insertIgnore(PaymentRecord record);

    /** 标记支付成功：已支付流水不会被再次覆盖（幂等） */
    int markPaid(@Param("channel") String channel,
                 @Param("orderNo") String orderNo,
                 @Param("tradeNo") String tradeNo,
                 @Param("notifyPayload") String notifyPayload,
                 @Param("paidTime") LocalDateTime paidTime);

    /** 标记交易关闭：已支付流水不会被降级（幂等） */
    int markClosed(@Param("channel") String channel, @Param("orderNo") String orderNo);
}