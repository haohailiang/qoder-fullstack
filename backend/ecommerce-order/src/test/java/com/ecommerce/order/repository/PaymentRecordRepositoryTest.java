package com.ecommerce.order.repository;

import com.ecommerce.order.entity.PaymentRecord;
import com.ecommerce.order.entity.enums.PaymentStatus;
import com.ecommerce.order.repository.mapper.PaymentRecordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PaymentRecordRepository 单元测试：确认支付流水读写正确透传到 Mapper。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentRecordRepository - 支付流水仓储")
class PaymentRecordRepositoryTest {

    private static final String CHANNEL = "ALIPAY";
    private static final String ORDER_NO = "ORD20250101120000ABCDEF";
    private static final String TRADE_NO = "2025010122001440000000000001";

    @Mock
    private PaymentRecordMapper paymentRecordMapper;

    @InjectMocks
    private PaymentRecordRepository paymentRecordRepository;

    private PaymentRecord record;

    @BeforeEach
    void setUp() {
        record = new PaymentRecord();
        record.setOrderNo(ORDER_NO);
        record.setChannel(CHANNEL);
        record.setAmount(new BigDecimal("199.98"));
        record.setStatus(PaymentStatus.CREATED);
    }

    @Nested
    @DisplayName("findByOrderNo - 查询流水")
    class FindByOrderNo {

        @Test
        @DisplayName("存在：返回流水记录")
        void shouldReturnRecord() {
            when(paymentRecordMapper.selectByChannelAndOrderNo(CHANNEL, ORDER_NO)).thenReturn(record);

            assertThat(paymentRecordRepository.findByOrderNo(CHANNEL, ORDER_NO).getStatus())
                    .isEqualTo(PaymentStatus.CREATED);
        }

        @Test
        @DisplayName("不存在：返回 null")
        void shouldReturnNull() {
            when(paymentRecordMapper.selectByChannelAndOrderNo(CHANNEL, "NONE")).thenReturn(null);

            assertThat(paymentRecordRepository.findByOrderNo(CHANNEL, "NONE")).isNull();
        }
    }

    @Nested
    @DisplayName("insertIgnore - 幂等写入流水")
    class InsertIgnore {

        @Test
        @DisplayName("正常场景：返回受影响行数并透传实体")
        void shouldInsertRecord() {
            when(paymentRecordMapper.insertIgnore(record)).thenReturn(1);

            assertThat(paymentRecordRepository.insertIgnore(record)).isEqualTo(1);
            verify(paymentRecordMapper).insertIgnore(record);
        }

        @Test
        @DisplayName("重复创建支付：唯一键冲突被忽略，返回 0 行")
        void shouldReturnZeroWhenDuplicated() {
            when(paymentRecordMapper.insertIgnore(record)).thenReturn(0);

            assertThat(paymentRecordRepository.insertIgnore(record)).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("markPaid / markClosed - 更新流水状态")
    class UpdateStatus {

        @Test
        @DisplayName("markPaid：回填交易号、回调原文与支付时间")
        void shouldMarkPaid() {
            LocalDateTime paidTime = LocalDateTime.of(2025, 1, 1, 12, 0, 0);
            when(paymentRecordMapper.markPaid(CHANNEL, ORDER_NO, TRADE_NO, "payload", paidTime)).thenReturn(1);

            assertThat(paymentRecordRepository.markPaid(CHANNEL, ORDER_NO, TRADE_NO, "payload", paidTime))
                    .isEqualTo(1);
            verify(paymentRecordMapper).markPaid(CHANNEL, ORDER_NO, TRADE_NO, "payload", paidTime);
        }

        @Test
        @DisplayName("markClosed：交易关闭")
        void shouldMarkClosed() {
            when(paymentRecordMapper.markClosed(CHANNEL, ORDER_NO)).thenReturn(1);

            assertThat(paymentRecordRepository.markClosed(CHANNEL, ORDER_NO)).isEqualTo(1);
            verify(paymentRecordMapper).markClosed(CHANNEL, ORDER_NO);
        }
    }
}