package com.apextick.booking.ticket;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Offline checks on the rendered ticket: valid PDF, real content, scannable QR. */
class TicketPdfRendererTest {

    private final TicketPdfRenderer renderer = new TicketPdfRenderer();

    private static TicketPdfData data(String qrToken) {
        return new TicketPdfData(
                "11111111-2222-3333-4444-555555555555", qrToken, "APX-000123", "Kalana Sandakelum",
                "India v Australia", "Wankhede Stadium", "Mumbai", "India",
                Instant.parse("2026-02-21T13:30:00Z"), "Asia/Kolkata",
                "A12", "North Stand", "Premium", new BigDecimal("4500.00"), "INR");
    }

    @Test
    void renders_a_valid_pdf_document() {
        byte[] pdf = renderer.render(data("tok-abc123"));

        assertThat(pdf).isNotEmpty();
        // every PDF starts with the %PDF- header and ends with an EOF marker
        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        assertThat(new String(pdf, StandardCharsets.ISO_8859_1)).contains("%%EOF");
    }

    @Test
    void prints_the_event_seat_and_order_details_on_a_single_page() throws Exception {
        PdfReader reader = new PdfReader(renderer.render(data("tok-abc123")));
        assertThat(reader.getNumberOfPages()).isEqualTo(1);

        String text = new PdfTextExtractor(reader).getTextFromPage(1);

        assertThat(text).contains("APEXTICK");
        assertThat(text).contains("India v Australia");
        assertThat(text).contains("Wankhede Stadium", "Mumbai", "India");
        assertThat(text).contains("North Stand", "A12", "Premium");
        assertThat(text).contains("INR 4500.00");
        assertThat(text).contains("APX-000123");
        // start time is rendered in the event's own zone (13:30 UTC -> 19:00 IST)
        assertThat(text).contains("Saturday 21 February 2026").contains("19:00");
    }

    @Test
    void renders_a_qr_code_that_decodes_back_to_the_ticket_token() throws Exception {
        String token = "tok-scan-me-9f2c";
        byte[] png = invokeQrPng(token);

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        var result = new QRCodeReader().decode(
                new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image))));

        assertThat(result.getText()).isEqualTo(token);
    }

    /** The QR helper is private; reach it reflectively rather than widening the API for a test. */
    private byte[] invokeQrPng(String token) throws Exception {
        var method = TicketPdfRenderer.class.getDeclaredMethod("qrPng", String.class);
        method.setAccessible(true);
        return (byte[]) method.invoke(renderer, token);
    }
}
