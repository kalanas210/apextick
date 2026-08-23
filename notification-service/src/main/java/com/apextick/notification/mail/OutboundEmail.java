package com.apextick.notification.mail;

public record OutboundEmail(String to, String subject, String html) {
}
