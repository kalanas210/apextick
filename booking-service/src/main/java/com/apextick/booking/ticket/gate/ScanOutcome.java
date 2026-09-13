package com.apextick.booking.ticket.gate;

/** What one scan at a gate came to. */
public enum ScanOutcome {
    ADMITTED,
    ALREADY_USED,
    WRONG_EVENT,
    TICKET_CANCELLED,
    /** Not a scan: an admin reversing an admission, with the reason on the record. */
    UNADMITTED
}
