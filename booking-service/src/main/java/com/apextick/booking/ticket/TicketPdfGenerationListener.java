package com.apextick.booking.ticket;

import com.apextick.booking.order.OrderConfirmedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Pre-renders ticket PDFs once a booking commits. Runs on its own thread pool so the
 * customer's payment response is never blocked by rendering or an object-store upload;
 * failures are logged and left for the download endpoint to regenerate on demand.
 */
@Component
public class TicketPdfGenerationListener {

    private static final Logger log = LoggerFactory.getLogger(TicketPdfGenerationListener.class);

    private final TicketPdfService pdfs;

    public TicketPdfGenerationListener(TicketPdfService pdfs) {
        this.pdfs = pdfs;
    }

    @Async("ticketPdfExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderConfirmed(OrderConfirmedEvent event) {
        try {
            pdfs.generateForOrder(event.orderId());
        } catch (RuntimeException e) {
            log.warn("Pre-rendering ticket PDFs failed for order {}; they will be built on first download",
                    event.orderId(), e);
        }
    }
}
