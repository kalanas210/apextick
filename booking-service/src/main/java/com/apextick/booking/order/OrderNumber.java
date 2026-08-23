package com.apextick.booking.order;

import java.security.SecureRandom;

/** Human-friendly order reference, e.g. APX-7K3D9Q. Uniqueness enforced by a DB constraint. */
final class OrderNumber {

    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private OrderNumber() {
    }

    static String next() {
        StringBuilder sb = new StringBuilder("APX-");
        for (int i = 0; i < 6; i++) {
            sb.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }
}
