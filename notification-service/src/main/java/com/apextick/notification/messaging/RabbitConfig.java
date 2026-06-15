package com.apextick.notification.messaging;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String SEAT_HELD_QUEUE = "seat-held-notifications";

    @Bean
    public Queue seatHeldQueue() {
        return new Queue(SEAT_HELD_QUEUE, true);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}