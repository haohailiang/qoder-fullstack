package com.ecommerce.product.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 秒杀队列配置
 */
@Configuration
public class SeckillRabbitConfig {

    public static final String SECKILL_EXCHANGE = "seckill.exchange";
    public static final String SECKILL_QUEUE = "seckill.order.queue";
    public static final String SECKILL_ROUTING_KEY = "seckill.order";
    public static final String SECKILL_DLX_EXCHANGE = "seckill.dlx.exchange";
    public static final String SECKILL_DLX_QUEUE = "seckill.dlx.queue";
    public static final String SECKILL_DLX_ROUTING_KEY = "seckill.dlx";

    /** 死信交换机 */
    @Bean
    public DirectExchange seckillDlxExchange() {
        return new DirectExchange(SECKILL_DLX_EXCHANGE, true, false);
    }

    /** 死信队列 */
    @Bean
    public Queue seckillDlxQueue() {
        return QueueBuilder.durable(SECKILL_DLX_QUEUE).build();
    }

    /** 死信队列绑定到死信交换机 */
    @Bean
    public Binding seckillDlxBinding(Queue seckillDlxQueue, DirectExchange seckillDlxExchange) {
        return BindingBuilder.bind(seckillDlxQueue).to(seckillDlxExchange).with(SECKILL_DLX_ROUTING_KEY);
    }

    /** 秒杀交换机（Direct 类型） */
    @Bean
    public DirectExchange seckillExchange() {
        return new DirectExchange(SECKILL_EXCHANGE, true, false);
    }

    /** 秒杀队列（绑定死信交换机） */
    @Bean
    public Queue seckillQueue() {
        return QueueBuilder.durable(SECKILL_QUEUE)
                .withArgument("x-dead-letter-exchange", SECKILL_DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", SECKILL_DLX_ROUTING_KEY)
                .build();
    }

    /** 队列绑定到交换机 */
    @Bean
    public Binding seckillBinding(Queue seckillQueue, DirectExchange seckillExchange) {
        return BindingBuilder.bind(seckillQueue).to(seckillExchange).with(SECKILL_ROUTING_KEY);
    }
}
