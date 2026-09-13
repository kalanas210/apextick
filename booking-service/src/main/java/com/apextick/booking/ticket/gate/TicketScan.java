package com.apextick.booking.ticket.gate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** One line of the gate's record: a scan and what came of it, or an admission undone. */
@Entity
@Table(name = "ticket_scans")
@Getter
@Setter
@NoArgsConstructor
public class TicketScan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;

    /** The event the gate was admitting to, which a wrong-event scan does not share with its ticket. */
    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScanOutcome outcome;

    @Column
    private String gate;

    /** Keycloak {@code sub} of the steward who scanned, or the admin who undid an admission. */
    @Column(name = "actor_sub", nullable = false)
    private String actorSub;

    @Column
    private String reason;

    @Column(name = "scanned_at", nullable = false)
    private Instant scannedAt;
}
