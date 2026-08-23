package com.apextick.booking.ticket;

import com.apextick.booking.catalog.Event;
import com.apextick.booking.order.Order;
import com.apextick.booking.order.OrderItem;
import com.apextick.booking.security.CurrentUser;
import com.apextick.booking.storage.TicketStorage;
import com.apextick.booking.web.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Renders ticket PDFs and caches them in object storage. The PDF is a derived
 * artefact: it is generated eagerly after a booking is confirmed (off the payment
 * path), but the download endpoint regenerates on a storage miss, so a failed or
 * skipped upload can never cost a customer their ticket.
 */
@Service
public class TicketPdfService {

    public static final String CONTENT_TYPE = "application/pdf";

    private final TicketRepository tickets;
    private final TicketStorage storage;
    private final TicketPdfRenderer renderer;

    public TicketPdfService(TicketRepository tickets, TicketStorage storage, TicketPdfRenderer renderer) {
        this.tickets = tickets;
        this.storage = storage;
        this.renderer = renderer;
    }

    /** Ticket PDF for a download request, enforcing ownership (admins may fetch any). */
    @Transactional
    public byte[] pdfFor(UUID ticketId, CurrentUser user) {
        Ticket ticket = tickets.findById(ticketId)
                .orElseThrow(() -> new NotFoundException("Ticket", ticketId));
        if (!user.isAdmin() && !user.sub().equals(ticket.getOrder().getUserSub())) {
            throw new NotFoundException("Ticket", ticketId); // don't leak that it exists
        }
        return pdfOf(ticket);
    }

    /** Pre-renders every ticket on a confirmed order so the first download is instant. */
    @Transactional
    public void generateForOrder(UUID orderId) {
        tickets.findByOrderId(orderId).forEach(this::pdfOf);
    }

    private byte[] pdfOf(Ticket ticket) {
        if (ticket.getS3Key() != null) {
            Optional<byte[]> cached = storage.get(ticket.getS3Key());
            if (cached.isPresent()) {
                return cached.get();
            }
        }
        byte[] pdf = renderer.render(dataOf(ticket));
        String key = keyFor(ticket);
        storage.put(key, pdf, CONTENT_TYPE);
        ticket.setS3Key(key);
        ticket.setPdfGeneratedAt(Instant.now());
        return pdf;
    }

    private static String keyFor(Ticket ticket) {
        return "tickets/" + ticket.getEventId() + "/" + ticket.getId() + ".pdf";
    }

    private static TicketPdfData dataOf(Ticket ticket) {
        Order order = ticket.getOrder();
        Event event = order.getEvent();
        OrderItem item = ticket.getOrderItem();
        return new TicketPdfData(
                ticket.getId().toString(), ticket.getQrToken(), order.getOrderNumber(), order.getUserName(),
                event.getName(), event.getVenue(), event.getCity(), event.getCountry(),
                event.getStartsAt(), event.getTimeZone(),
                item.getSeatLabel(), item.getSectionName(), item.getTierName(),
                item.getUnitPrice(), order.getCurrency());
    }
}
