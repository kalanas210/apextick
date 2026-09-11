package com.apextick.booking.messaging;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Producer side of the topology: only the exchange the outbox publishes to. Queues belong
 * to their consumers (notification-service declares and binds its own), so this service
 * declares none -- a queue nobody drains would just grow with every hold.
 * <p>
 * This service used to declare a durable {@code seat-held-notifications} queue bound to
 * {@code seat.held}. Dropping a declaration does not remove it from a broker that already
 * has it, so on an existing broker delete it once by hand (nothing ever read it):
 * {@code rabbitmqctl delete_queue seat-held-notifications}.
 */
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "apextick.events";

    @Bean
    public TopicExchange apextickExchange() {
        return new TopicExchange(EXCHANGE);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
