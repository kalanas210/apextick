package com.apextick.booking.payment;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Asks again for the refunds a provider refused. REFUND_REQUIRED used to be written by two code
 * paths and read by none, so a charge the provider would not give back stayed owed for as long as
 * nobody went looking. Every refund still owed is now retried on a schedule, and how many are owed
 * is a gauge an alert can watch.
 */
@Component
public class RefundReconciler {

    private static final Logger log = LoggerFactory.getLogger(RefundReconciler.class);

    private final PaymentRepository payments;
    private final RefundService refunds;
    private final Duration backoff;
    private final int maxAttempts;
    private final int batch;

    public RefundReconciler(PaymentRepository payments, RefundService refunds, MeterRegistry meters,
                            @Value("${app.payment.refund-retry.backoff:PT10M}") Duration backoff,
                            @Value("${app.payment.refund-retry.max-attempts:12}") int maxAttempts,
                            @Value("${app.payment.refund-retry.batch:50}") int batch) {
        this.payments = payments;
        this.refunds = refunds;
        this.backoff = backoff;
        this.maxAttempts = maxAttempts;
        this.batch = batch;
        // counts every refund still owed, including the ones past their last automatic try: those
        // are exactly the ones a person has to look at
        Gauge.builder("apextick.refunds.owed", payments, repo -> repo.countByStatus(PaymentStatus.REFUND_REQUIRED))
                .description("Charges whose refund the payment provider has not accepted yet")
                .register(meters);
    }

    /**
     * Retries the refunds that are due: last asked for longer ago than the backoff, and refused
     * fewer than the maximum number of times. After that a refund stops being asked for on its own
     * -- a provider refusing a dozen times is not going to change its mind on the thirteenth -- and
     * waits for someone to retry it from the order in the admin console.
     *
     * @return how many of the refunds retried went through
     */
    @Scheduled(fixedDelayString = "${app.payment.refund-retry.interval:PT5M}",
               initialDelayString = "${app.payment.refund-retry.interval:PT5M}")
    @Transactional(propagation = Propagation.NEVER)
    public int retryDue() {
        List<UUID> due = payments.findRefundsDue(Instant.now().minus(backoff), maxAttempts,
                PageRequest.of(0, batch));
        int refunded = 0;
        for (UUID paymentId : due) {
            try {
                if (refunds.retry(paymentId)) {
                    refunded++;
                }
            } catch (RuntimeException e) {
                // one refund that cannot even be recorded must not stop the others being asked for
                log.error("Retrying the refund of payment {} failed", paymentId, e);
            }
        }
        if (!due.isEmpty()) {
            log.info("Asked again for {} refunds still owed; {} went through", due.size(), refunded);
        }
        return refunded;
    }
}
