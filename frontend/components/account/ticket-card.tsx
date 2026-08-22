"use client";

import { useState } from "react";
import { QRCodeSVG } from "qrcode.react";
import { api, authHeaders } from "@/lib/api";
import { useAccessToken } from "@/hooks/useSession";
import { cn } from "@/lib/cn";
import type { Ticket } from "@/lib/types";

/**
 * One issued ticket: seat details, its QR, and a PDF download.
 *
 * The QR encodes the same `qrToken` the backend prints into the PDF, so either
 * scans to the same ticket at the gate. The PDF is fetched as a blob rather
 * than linked, because the endpoint needs a bearer token that a plain anchor
 * cannot send.
 */
export function TicketCard({ ticket }: { ticket: Ticket }) {
  const token = useAccessToken();
  const [downloading, setDownloading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const used = ticket.status === "USED";
  const cancelled = ticket.status === "CANCELLED";

  const download = async () => {
    if (!token) return;
    setDownloading(true);
    setError(null);
    try {
      const response = await api.get(ticket.pdfUrl, {
        headers: authHeaders(token),
        responseType: "blob",
      });
      const url = URL.createObjectURL(response.data as Blob);
      window.open(url, "_blank", "noopener");
      // give the new tab a moment to take the blob before revoking it
      setTimeout(() => URL.revokeObjectURL(url), 60_000);
    } catch {
      setError("Could not fetch that ticket. Try again.");
    } finally {
      setDownloading(false);
    }
  };

  return (
    <div
      className={cn(
        "flex flex-col gap-5 rounded-2xl border border-line bg-ink-2 p-5 sm:flex-row sm:items-center",
        (used || cancelled) && "opacity-60",
      )}
    >
      <div className="grid h-28 w-28 shrink-0 place-items-center rounded-xl bg-bone p-2">
        <QRCodeSVG
          value={ticket.qrToken}
          size={96}
          level="M"
          bgColor="#f4f2ec"
          fgColor="#0b0b0c"
          title={`QR code for seat ${ticket.seatLabel}`}
        />
      </div>

      <div className="min-w-0 flex-1">
        <p className="font-display text-base font-semibold tracking-tight text-bone">
          {ticket.sectionName} <span className="tnum text-muted">· {ticket.seatLabel}</span>
        </p>
        <p className="mt-1 text-[0.78rem] text-muted">{ticket.tierName}</p>
        <p className="mt-2 font-mono text-[0.62rem] uppercase tracking-[0.14em] text-faint">
          {cancelled ? "Cancelled" : used ? "Already scanned" : "Valid · admits one"}
        </p>
        {error && (
          <p role="alert" className="mt-2 text-[0.74rem] text-[#ff6b6b]">
            {error}
          </p>
        )}
      </div>

      <button
        type="button"
        onClick={download}
        disabled={downloading || !token}
        className="inline-flex h-10 shrink-0 items-center justify-center gap-2 rounded-full border border-line-2 px-5 text-[0.82rem] text-bone transition-colors hover:border-bone disabled:opacity-60"
      >
        {downloading ? (
          <>
            <span className="h-3.5 w-3.5 animate-spin rounded-full border-2 border-bone/30 border-t-bone" />
            Building…
          </>
        ) : (
          "Download PDF"
        )}
      </button>
    </div>
  );
}
