package com.apextick.booking.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "apextick.events";
    public static final String SEAT_HELD_ROUTING_KEY = "seat.held";
    public static final String SEAT_HELD_QUEUE = "seat-held-notifications";

    @Bean
    public TopicExchange apextickExchange() {
        return new TopicExchange(EXCHANGE);
    }

    @Bean
    public Queue seatHeldQueue() {
        return new Queue(SEAT_HELD_QUEUE, true); // durable: survives a broker restart
    }

    @Bean
    public Binding seatHeldBinding(Queue seatHeldQueue, TopicExchange apextickExchange) {
        return BindingBuilder.bind(seatHeldQueue).to(apextickExchange).with(SEAT_HELD_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}