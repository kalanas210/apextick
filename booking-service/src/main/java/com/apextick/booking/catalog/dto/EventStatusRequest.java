package com.apextick.booking.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of the status patch. A record rather than a raw Map so a missing status is a
 * 400 with a field error instead of a null reaching EventStatus.fromCode as a 500 —
 * and so the one field the endpoint accepts shows up in the OpenAPI document.
 *
 * @param reason for a cancellation: what the event's ticket holders are told
 */
public record EventStatusRequest(@NotBlank String status, @Size(max = 255) String reason) {
}
