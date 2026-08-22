package com.apextick.booking.hold;

import com.apextick.booking.realtime.RealtimePublisher;
import com.apextick.booking.realtime.RedisRealtimeSubscriber;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class RedisListenerConfig {

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            HoldExpiryListener holdExpiryListener,
            RedisRealtimeSubscriber realtimeSubscriber) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        // seat-hold:{id} TTL expiry -> release the seat
        container.addMessageListener(holdExpiryListener, new ChannelTopic("__keyevent@0__:expired"));
        // cross-instance realtime fan-out -> local STOMP broker
        container.addMessageListener(realtimeSubscriber, new ChannelTopic(RealtimePublisher.CHANNEL));
        return container;
    }
}
