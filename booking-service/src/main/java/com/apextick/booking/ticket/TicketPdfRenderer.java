package com.apextick.booking.ticket;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageConfig;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Renders a single-page A4 e-ticket: event header, seat details, and a QR code
 * carrying the ticket's {@code qrToken} for gate scanning. Uses the ApexTick
 * palette so the PDF matches the web UI.
 */
@Component
public class TicketPdfRenderer {

    private static final Color INK = new Color(0x0b, 0x0b, 0x0c);
    private static final Color BONE = new Color(0xf4, 0xf2, 0xec);
    private static final Color ACCENT = new Color(0xc9, 0xf2, 0x3f);
    private static final Color MUTED = new Color(0x9a, 0x95, 0x8a);

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("HH:mm z", Locale.ENGLISH);

    public byte[] render(TicketPdfData d) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 48, 48, 48, 48);
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            doc.open();
            paintBackground(writer);

            doc.add(brandRow());
            doc.add(spacer(18));
            doc.add(text(d.eventName(), font(22, Font.BOLD, BONE)));
            doc.add(spacer(4));
            doc.add(text(venueLine(d), font(11, Font.NORMAL, MUTED)));
            doc.add(spacer(2));
            doc.add(text(whenLine(d), font(11, Font.NORMAL, MUTED)));
            doc.add(spacer(22));
            doc.add(detailsTable(d));
            doc.add(spacer(24));
            doc.add(qrBlock(d));
            doc.add(spacer(18));
            doc.add(text("Order " + d.orderNumber() + "  ·  Ticket " + d.ticketId(),
                    font(8, Font.NORMAL, MUTED)));
            doc.add(spacer(4));
            doc.add(text("Present this QR code at the gate. Admits one.", font(8, Font.NORMAL, MUTED)));

            doc.close();
            return out.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("Failed to render ticket PDF for " + d.ticketId(), e);
        }
    }

    /** Fill the page with the dark brand ink behind the text. */
    private static void paintBackground(PdfWriter writer) {
        Rectangle page = writer.getPageSize();
        var canvas = writer.getDirectContentUnder();
        canvas.setColorFill(INK);
        canvas.rectangle(0, 0, page.getWidth(), page.getHeight());
        canvas.fill();
    }

    private static PdfPTable brandRow() throws DocumentException {
        PdfPTable table = borderless(2);
        table.setWidths(new float[]{1f, 1f});
        table.addCell(cell(new Phrase("APEXTICK", font(13, Font.BOLD, ACCENT)), Element.ALIGN_LEFT));
        table.addCell(cell(new Phrase("E-TICKET", font(9, Font.NORMAL, MUTED)), Element.ALIGN_RIGHT));
        return table;
    }

    private static PdfPTable detailsTable(TicketPdfData d) throws DocumentException {
        PdfPTable table = borderless(4);
        table.setWidths(new float[]{1f, 1f, 1f, 1f});
        for (String label : new String[]{"SECTION", "SEAT", "TIER", "PRICE"}) {
            table.addCell(cell(new Phrase(label, font(8, Font.NORMAL, MUTED)), Element.ALIGN_LEFT));
        }
        String price = d.price() == null ? "—" : d.currency() + " " + d.price().toPlainString();
        for (String value : new String[]{d.sectionName(), d.seatLabel(), d.tierName(), price}) {
            table.addCell(cell(new Phrase(value == null ? "—" : value, font(13, Font.BOLD, BONE)),
                    Element.ALIGN_LEFT));
        }
        return table;
    }

    private PdfPTable qrBlock(TicketPdfData d) throws DocumentException {
        PdfPTable table = borderless(1);
        try {
            Image qr = Image.getInstance(qrPng(d.qrToken()));
            qr.scaleToFit(190, 190);
            PdfPCell cell = new PdfPCell(qr, false);
            cell.setBorder(Rectangle.NO_BORDER);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setPaddingBottom(8);
            table.addCell(cell);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to build the ticket QR code", e);
        }
        table.addCell(cell(new Phrase(d.qrToken(), font(8, Font.NORMAL, MUTED)), Element.ALIGN_CENTER));
        return table;
    }

    /** QR as PNG bytes, drawn in brand colours so it stays legible on the dark page. */
    private byte[] qrPng(String token) throws IOException {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.MARGIN, 2);
        try {
            BitMatrix matrix = new QRCodeWriter().encode(token, BarcodeFormat.QR_CODE, 512, 512, hints);
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", png,
                    new MatrixToImageConfig(INK.getRGB(), BONE.getRGB()));
            return png.toByteArray();
        } catch (WriterException e) {
            throw new IllegalStateException("Failed to encode the ticket QR code", e);
        }
    }

    private static String venueLine(TicketPdfData d) {
        StringBuilder sb = new StringBuilder(d.venue() == null ? "" : d.venue());
        if (d.city() != null && !d.city().isBlank()) {
            sb.append(sb.isEmpty() ? "" : ", ").append(d.city());
        }
        if (d.country() != null && !d.country().isBlank()) {
            sb.append(sb.isEmpty() ? "" : ", ").append(d.country());
        }
        return sb.toString();
    }

    private static String whenLine(TicketPdfData d) {
        if (d.startsAt() == null) {
            return "";
        }
        ZoneId zone = zoneOf(d.timeZone());
        var local = d.startsAt().atZone(zone);
        return DATE.format(local) + "  ·  " + TIME.format(local);
    }

    private static ZoneId zoneOf(String timeZone) {
        if (timeZone == null || timeZone.isBlank()) {
            return ZoneId.of("UTC");
        }
        try {
            return ZoneId.of(timeZone);
        } catch (Exception e) {
            return ZoneId.of("UTC");
        }
    }

    private static PdfPTable borderless(int columns) {
        PdfPTable table = new PdfPTable(columns);
        table.setWidthPercentage(100);
        return table;
    }

    private static PdfPCell cell(Phrase phrase, int alignment) {
        PdfPCell cell = new PdfPCell(phrase);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setHorizontalAlignment(alignment);
        cell.setPaddingBottom(6);
        return cell;
    }

    private static Paragraph text(String value, Font font) {
        return new Paragraph(value == null ? "" : value, font);
    }

    private static Paragraph spacer(float height) {
        Paragraph p = new Paragraph(" ");
        p.setLeading(height);
        return p;
    }

    private static Font font(float size, int style, Color color) {
        return new Font(Font.HELVETICA, size, style, color);
    }
}
