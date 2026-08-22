package com.apextick.booking.realtime;

/** Envelope relayed over Redis pub/sub so every instance re-broadcasts to its local STOMP broker. */
public record RealtimeMessage(String destination, String userSub, Object payload) {
}
