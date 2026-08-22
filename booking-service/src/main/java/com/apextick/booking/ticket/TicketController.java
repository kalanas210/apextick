package com.apextick.booking.ticket;

import com.apextick.booking.security.CurrentUser;
import com.apextick.booking.ticket.dto.TicketResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Tickets")
public class TicketController {

    private final TicketService tickets;
    private final TicketPdfService pdfs;

    public TicketController(TicketService tickets, TicketPdfService pdfs) {
        this.tickets = tickets;
        this.pdfs = pdfs;
    }

    @GetMapping("/api/orders/{orderId}/tickets")
    @Operation(summary = "Tickets issued for an order")
    public List<TicketResponse> forOrder(@PathVariable UUID orderId, CurrentUser user) {
        return tickets.forOrder(orderId, user);
    }

    @GetMapping("/api/tickets/{id}")
    @Operation(summary = "A single ticket")
    public TicketResponse get(@PathVariable UUID id, CurrentUser user) {
        return tickets.get(id, user);
    }

    @GetMapping(value = "/api/tickets/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Download a ticket as a PDF with its QR code")
    public ResponseEntity<byte[]> pdf(@PathVariable UUID id, CurrentUser user) {
        byte[] pdf = pdfs.pdfFor(id, user);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename("apextick-ticket-" + id + ".pdf").build().toString())
                .body(pdf);
    }
}
