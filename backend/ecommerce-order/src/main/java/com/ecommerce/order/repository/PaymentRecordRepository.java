package com.ecommerce.order.repository;

import com.ecommerce.order.entity.PaymentRecord;
import com.ecommerce.order.repository.mapper.PaymentRecordMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public class PaymentRecordRepository {

    private final PaymentRecordMapper paymentRecordMapper;

    public PaymentRecordRepository(PaymentRecordMapper paymentRecordMapper) {
        this.paymentRecordMapper = paymentRecordMapper;
    }

    public PaymentRecord findByOrderNo(String channel, String orderNo) {
        return paymentRecordMapper.selectByChannelAndOrderNo(channel, orderNo);
    }

    public int insertIgnore(PaymentRecord record) {
        return paymentRecordMapper.insertIgnore(record);
    }

    public int markPaid(String channel, String orderNo, String tradeNo, String notifyPayload, LocalDateTime paidTime) {
        return paymentRecordMapper.markPaid(channel, orderNo, tradeNo, notifyPayload, paidTime);
    }

    public int markClosed(String channel, String orderNo) {
        return paymentRecordMapper.markClosed(channel, orderNo);
    }
}