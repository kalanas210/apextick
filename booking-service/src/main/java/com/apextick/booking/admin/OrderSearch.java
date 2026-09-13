package com.apextick.booking.admin;

import com.apextick.booking.order.Order;
import com.apextick.booking.order.OrderStatus;
import com.apextick.booking.payment.Payment;
import com.apextick.booking.payment.PaymentStatus;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The box office's order search. A customer on the phone has an order number, or only their
 * email address, and the console used to be able to filter by status and nothing else.
 */
final class OrderSearch {

    private OrderSearch() {
    }

    /**
     * @param status     only orders in this state, or any when {@code null}
     * @param text       part of the order number, or of the customer's email or name
     * @param refundOwed only orders with a refund the provider has not accepted yet
     */
    static Specification<Order> matching(OrderStatus status, String text, boolean refundOwed) {
        return (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            if (status != null) {
                where.add(cb.equal(root.get("status"), status));
            }
            if (text != null && !text.isBlank()) {
                String pattern = "%" + escapeLike(text.strip().toLowerCase(Locale.ROOT)) + "%";
                where.add(cb.or(
                        cb.like(cb.lower(root.<String>get("orderNumber")), pattern, '\\'),
                        cb.like(cb.lower(root.<String>get("userEmail")), pattern, '\\'),
                        cb.like(cb.lower(root.<String>get("userName")), pattern, '\\')));
            }
            if (refundOwed) {
                Subquery<UUID> owed = query.subquery(UUID.class);
                Root<Payment> payment = owed.from(Payment.class);
                owed.select(payment.<UUID>get("id")).where(
                        cb.equal(payment.get("order"), root),
                        cb.equal(payment.get("status"), PaymentStatus.REFUND_REQUIRED));
                where.add(cb.exists(owed));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
    }

    /** A % or _ someone typed is looked for, not taken as a wildcard that matches every order. */
    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
