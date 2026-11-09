package com.ecommerce.order.entity.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OrderStatus 枚举单元测试：验证状态码与枚举值的映射关系、
 * fromCode 查找逻辑以及 getDescriptionByCode 的兜底行为。
 */
class OrderStatusTest {

    // ──────────────────── 枚举基本属性 ────────────────────

    @Nested
    @DisplayName("枚举基本属性")
    class EnumProperties {

        @Test
        @DisplayName("PENDING_PAYMENT：code=0, description='待支付'")
        void pendingPayment() {
            assertThat(OrderStatus.PENDING_PAYMENT.getCode()).isEqualTo(0);
            assertThat(OrderStatus.PENDING_PAYMENT.getDescription()).isEqualTo("待支付");
        }

        @Test
        @DisplayName("PAID：code=1, description='已支付'")
        void paid() {
            assertThat(OrderStatus.PAID.getCode()).isEqualTo(1);
            assertThat(OrderStatus.PAID.getDescription()).isEqualTo("已支付");
        }

        @Test
        @DisplayName("SHIPPED：code=2, description='已发货'")
        void shipped() {
            assertThat(OrderStatus.SHIPPED.getCode()).isEqualTo(2);
            assertThat(OrderStatus.SHIPPED.getDescription()).isEqualTo("已发货");
        }

        @Test
        @DisplayName("COMPLETED：code=3, description='已完成'")
        void completed() {
            assertThat(OrderStatus.COMPLETED.getCode()).isEqualTo(3);
            assertThat(OrderStatus.COMPLETED.getDescription()).isEqualTo("已完成");
        }

        @Test
        @DisplayName("CANCELLED：code=4, description='已取消'")
        void cancelled() {
            assertThat(OrderStatus.CANCELLED.getCode()).isEqualTo(4);
            assertThat(OrderStatus.CANCELLED.getDescription()).isEqualTo("已取消");
        }

        @Test
        @DisplayName("枚举值总数为 5")
        void enumCount() {
            assertThat(OrderStatus.values()).hasSize(5);
        }
    }

    // ──────────────────── fromCode ────────────────────

    @Nested
    @DisplayName("fromCode - 根据状态码查找枚举值")
    class FromCode {

        @Test
        @DisplayName("code=0 返回 PENDING_PAYMENT")
        void fromCode0() {
            assertThat(OrderStatus.fromCode(0)).isEqualTo(OrderStatus.PENDING_PAYMENT);
        }

        @Test
        @DisplayName("code=1 返回 PAID")
        void fromCode1() {
            assertThat(OrderStatus.fromCode(1)).isEqualTo(OrderStatus.PAID);
        }

        @Test
        @DisplayName("code=2 返回 SHIPPED")
        void fromCode2() {
            assertThat(OrderStatus.fromCode(2)).isEqualTo(OrderStatus.SHIPPED);
        }

        @Test
        @DisplayName("code=3 返回 COMPLETED")
        void fromCode3() {
            assertThat(OrderStatus.fromCode(3)).isEqualTo(OrderStatus.COMPLETED);
        }

        @Test
        @DisplayName("code=4 返回 CANCELLED")
        void fromCode4() {
            assertThat(OrderStatus.fromCode(4)).isEqualTo(OrderStatus.CANCELLED);
        }

        @Test
        @DisplayName("无效 code=99 返回 null")
        void fromInvalidCode() {
            assertThat(OrderStatus.fromCode(99)).isNull();
        }

        @Test
        @DisplayName("负数 code=-1 返回 null")
        void fromNegativeCode() {
            assertThat(OrderStatus.fromCode(-1)).isNull();
        }
    }

    // ──────────────────── getDescriptionByCode ────────────────────

    @Nested
    @DisplayName("getDescriptionByCode - 根据状态码获取描述")
    class GetDescriptionByCode {

        @Test
        @DisplayName("code=0 返回 '待支付'")
        void descriptionFor0() {
            assertThat(OrderStatus.getDescriptionByCode(0)).isEqualTo("待支付");
        }

        @Test
        @DisplayName("code=1 返回 '已支付'")
        void descriptionFor1() {
            assertThat(OrderStatus.getDescriptionByCode(1)).isEqualTo("已支付");
        }

        @Test
        @DisplayName("code=2 返回 '已发货'")
        void descriptionFor2() {
            assertThat(OrderStatus.getDescriptionByCode(2)).isEqualTo("已发货");
        }

        @Test
        @DisplayName("code=3 返回 '已完成'")
        void descriptionFor3() {
            assertThat(OrderStatus.getDescriptionByCode(3)).isEqualTo("已完成");
        }

        @Test
        @DisplayName("code=4 返回 '已取消'")
        void descriptionFor4() {
            assertThat(OrderStatus.getDescriptionByCode(4)).isEqualTo("已取消");
        }

        @Test
        @DisplayName("无效 code 返回 '未知状态'")
        void descriptionForInvalidCode() {
            assertThat(OrderStatus.getDescriptionByCode(99)).isEqualTo("未知状态");
        }

        @Test
        @DisplayName("负数 code 返回 '未知状态'")
        void descriptionForNegativeCode() {
            assertThat(OrderStatus.getDescriptionByCode(-1)).isEqualTo("未知状态");
        }
    }

    // ──────────────────── valueOf ────────────────────

    @Nested
    @DisplayName("valueOf - 根据枚举名称查找")
    class ValueOf {

        @Test
        @DisplayName("valueOf('PENDING_PAYMENT') 返回正确枚举")
        void valueOfPendingPayment() {
            assertThat(OrderStatus.valueOf("PENDING_PAYMENT")).isEqualTo(OrderStatus.PENDING_PAYMENT);
        }

        @Test
        @DisplayName("valueOf('CANCELLED') 返回正确枚举")
        void valueOfCancelled() {
            assertThat(OrderStatus.valueOf("CANCELLED")).isEqualTo(OrderStatus.CANCELLED);
        }
    }
}
