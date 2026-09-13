"use client";

import Link from "next/link";
import { useState } from "react";
import type { FormEvent, ReactNode } from "react";
import { cn } from "@/lib/cn";
import { apiErrorMessage, apiStatus } from "@/lib/api";
import { formatInstant, formatPrice, relativeTime } from "@/lib/format";
import {
  ORDER_LABEL,
  ORDER_TONE,
  PAYMENT_LABEL,
  PAYMENT_TONE,
  TICKET_LABEL,
  TICKET_TONE,
  pillClass,
} from "@/lib/status";
import { useAdminOrder, useRefundOrder, useRetryRefund } from "@/hooks/useAdmin";
import { AdminHeader } from "./admin-shell";
import { Button } from "@/components/ui/button";
import { Field, Input } from "@/components/ui/field";
import { Notice } from "@/components/ui/notice";
import type { AdminOrderDetail, AdminPayment, AdminTicket, RefundBlocker } from "@/lib/types";

/** Why the console will not refund an order, said to the operator looking at it. */
const BLOCKED: Record<RefundBlocker, string> = {
  NOT_PAID: "Only a paid order can be refunded.",
  NO_SETTLED_PAYMENT: "This order has no settled payment to refund.",
  TICKETS_USED:
    "A ticket on this order has been used at the gate. If letting its holder in was a mistake, undo the admission from the scanner, then refund.",
};

/** One order in full, for the box office: who bought what, where every ticket stands, and its refund. */
export function OrderInspector({ id }: { id: string }) {
  const { data: detail, isLoading, error } = useAdminOrder(id);

  if (isLoading) {
    return (
      <div className="grid place-items-center py-24" aria-busy="true">
        <span className="h-7 w-7 animate-spin rounded-full border-2 border-line-2 border-t-accent" />
        <span className="sr-only">Loading the order…</span>
      </div>
    );
  }

  if (error || !detail) {
    return (
      <div className="rounded-2xl border border-line bg-ink-2 p-8 text-center">
        <h2 className="font-display text-xl tracking-tight">
          {apiStatus(error) === 404 ? "Order not found" : "Could not load this order"}
        </h2>
        <p className="mx-auto mt-2 max-w-sm text-[0.86rem] text-muted">
          {apiErrorMessage(error, "Check the link, or find the order from the list.")}
        </p>
        <div className="mt-6 flex justify-center">
          <Button href="/admin/orders" size="md">
            All orders
          </Button>
        </div>
      </div>
    );
  }

  const { order } = detail;

  return (
    <>
      <AdminHeader
        kicker={
          <Link href="/admin/orders" className="ulink">
            Orders
          </Link>
        }
        title={order.orderNumber}
        description={`${order.eventName} · ${formatInstant(order.startsAt)}`}
        action={<span className={cn(pillClass, ORDER_TONE[order.status])}>{ORDER_LABEL[order.status]}</span>}
      />

      <div className="mt-8 grid gap-10 lg:grid-cols-12 lg:gap-12">
        <div className="space-y-8 lg:col-span-7">
          <Section title="Customer">
            <dl className="space-y-1.5 text-[0.84rem]">
              <Row label="Name" value={order.userName ?? "—"} />
              <Row
                label="Email"
                value={
                  order.userEmail ? (
                    <a href={`mailto:${order.userEmail}`} className="ulink">
                      {order.userEmail}
                    </a>
                  ) : (
                    "—"
                  )
                }
              />
              <Row label="Account" value={<span className="font-mono text-[0.72rem] text-faint">{detail.userSub}</span>} />
            </dl>
          </Section>

          <Section title={`Tickets (${detail.tickets.length})`}>
            {detail.tickets.length ? (
              <ul className="space-y-2">
                {detail.tickets.map((ticket) => (
                  <TicketRow key={ticket.id} ticket={ticket} />
                ))}
              </ul>
            ) : (
              <p className="text-[0.84rem] text-muted">No tickets: the order was never paid.</p>
            )}
          </Section>

          <Section title="Payments">
            {detail.payments.length ? (
              <ul className="space-y-3">
                {detail.payments.map((payment) => (
                  <PaymentCard key={payment.id} payment={payment} />
                ))}
              </ul>
            ) : (
              <p className="text-[0.84rem] text-muted">Nobody has tried to pay for this order.</p>
            )}
          </Section>
        </div>

        <aside className="lg:col-span-5">
          {/* Capped and scrollable, like every admin sticky column: the refund form must never
              be stranded below the fold. */}
          <div className="space-y-6 lg:sticky lg:top-8 lg:max-h-[calc(100vh-4rem)] lg:overflow-y-auto lg:pr-1 [scrollbar-width:thin]">
            <Summary detail={detail} />
            <RefundPanel detail={detail} />
          </div>
        </aside>
      </div>
    </>
  );
}

function Summary({ detail }: { detail: AdminOrderDetail }) {
  const { order } = detail;
  const price = (value: number) => formatPrice(value, order.currency, { keepMinorUnits: true });
  return (
    <section className="rounded-2xl border border-line bg-ink-2 p-6">
      <h2 className="kicker">Summary</h2>
      <dl className="mt-4 space-y-1.5 text-[0.84rem]">
        <Row label="Subtotal" value={price(order.subtotal)} />
        <Row label="Booking fee" value={price(order.fee)} />
        <Row label="Total" value={<span className="text-bone">{price(order.total)}</span>} />
        <Row label="Ordered" value={formatInstant(order.createdAt)} />
        {order.paidAt && <Row label="Paid" value={formatInstant(order.paidAt)} />}
        {order.cancelledAt && (
          <Row label="Cancelled" value={`${formatInstant(order.cancelledAt)}${order.cancelReason ? ` · ${order.cancelReason}` : ""}`} />
        )}
        {order.refundedAt && <Row label="Refunded" value={formatInstant(order.refundedAt)} />}
      </dl>
      {detail.refundReason && (
        <p className="mt-4 border-t border-line pt-4 text-[0.8rem] text-muted">
          “{detail.refundReason}”
          <span className="block text-[0.72rem] text-faint">
            {detail.refundedBy ? `Refunded by ${detail.refundedBy}` : "Refunded from the payment provider"}
          </span>
        </p>
      )}
    </section>
  );
}

/**
 * The refund. The API refuses anything the order cannot take, so this only has to say why when
 * it will not offer one, and what happened when it did -- including a provider that refused.
 */
function RefundPanel({ detail }: { detail: AdminOrderDetail }) {
  const { order } = detail;
  const refund = useRefundOrder(order.id);
  const retry = useRetryRefund(order.id);
  const [reason, setReason] = useState("");
  const [missing, setMissing] = useState(false);

  const owed = detail.payments.filter((p) => p.status === "REFUND_REQUIRED");
  const busy = refund.isPending || retry.isPending;

  const submit = (e: FormEvent) => {
    e.preventDefault();
    const why = reason.trim();
    if (!why) {
      setMissing(true);
      return;
    }
    retry.reset();
    refund.mutate(why, { onSuccess: () => setReason("") });
  };

  const failed = refund.error ?? retry.error;

  return (
    <section className="rounded-2xl border border-line bg-ink-2 p-6">
      <h2 className="kicker">Refund</h2>

      {failed && (
        <Notice tone="error" className="mt-4">
          {apiErrorMessage(failed, "The refund could not be made.")}
        </Notice>
      )}

      {detail.refundBlockedBy === null ? (
        <form onSubmit={submit} className="mt-4 space-y-4">
          <p className="text-[0.82rem] text-muted">
            Refunds {formatPrice(order.total, order.currency, { keepMinorUnits: true })} to the card that paid,
            voids the {detail.tickets.length === 1 ? "ticket" : `${detail.tickets.length} tickets`} and puts the
            seats back on sale.
          </p>
          <Field
            label="Reason"
            required
            hint="Kept on the order's record."
            error={missing ? "Say why the order is being refunded." : undefined}
          >
            {(a) => (
              <Input
                {...a}
                value={reason}
                onChange={(e) => {
                  setReason(e.target.value);
                  setMissing(false);
                }}
                placeholder="Customer cannot attend"
                maxLength={255}
              />
            )}
          </Field>
          <Button
            type="submit"
            size="sm"
            variant="outline"
            className={cn(
              "border-[#ff6b6b]/50 text-[#ff6b6b] hover:border-[#ff6b6b] hover:bg-[#ff6b6b]/10",
              busy && "pointer-events-none opacity-60",
            )}
          >
            {refund.isPending ? "Refunding…" : "Refund order"}
          </Button>
        </form>
      ) : owed.length ? (
        <div className="mt-4 space-y-4">
          <p className="text-[0.82rem] text-muted">
            {owed.length === 1 ? "A refund is" : `${owed.length} refunds are`} still owed on this order. They are asked
            for again automatically; ask now if the customer is waiting.
          </p>
          <Button
            onClick={() => {
              refund.reset();
              retry.mutate();
            }}
            size="sm"
            variant="outline"
            className={cn(busy && "pointer-events-none opacity-60")}
          >
            {retry.isPending ? "Asking…" : "Retry refund now"}
          </Button>
        </div>
      ) : (
        <p className="mt-4 text-[0.82rem] text-muted">
          {order.status === "REFUNDED" ? "This order has been refunded." : BLOCKED[detail.refundBlockedBy]}
        </p>
      )}
    </section>
  );
}

function TicketRow({ ticket }: { ticket: AdminTicket }) {
  return (
    <li className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-line bg-ink px-3 py-2.5">
      <span className="text-[0.84rem] text-bone">
        {ticket.sectionName}
        <span className="tnum text-muted"> · {ticket.seatLabel}</span>
        <span className="text-faint"> · {ticket.tierName}</span>
      </span>
      <span className="flex items-center gap-3">
        {ticket.usedAt && (
          <span className="text-[0.72rem] text-faint">
            {relativeTime(ticket.usedAt)}
            {ticket.usedGate ? ` at ${ticket.usedGate}` : ""}
          </span>
        )}
        <span className={cn(pillClass, TICKET_TONE[ticket.status])}>{TICKET_LABEL[ticket.status]}</span>
      </span>
    </li>
  );
}

function PaymentCard({ payment }: { payment: AdminPayment }) {
  const owedBack = formatPrice(payment.refundAmount ?? payment.amount, payment.refundCurrency ?? payment.currency, {
    keepMinorUnits: true,
  });
  return (
    <li className="rounded-2xl border border-line bg-ink-2 p-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <span className="text-[0.86rem] text-bone">
          {formatPrice(payment.amount, payment.currency, { keepMinorUnits: true })}
          <span className="text-muted"> · {payment.provider.toLowerCase()}</span>
          {payment.cardLast4 && (
            <span className="text-faint">
              {" "}
              · {payment.cardBrand ?? "Card"} ending {payment.cardLast4}
            </span>
          )}
        </span>
        <span className={cn(pillClass, PAYMENT_TONE[payment.status])}>{PAYMENT_LABEL[payment.status]}</span>
      </div>
      <dl className="mt-3 space-y-1 text-[0.78rem]">
        <Row label="Started" value={formatInstant(payment.createdAt)} />
        {payment.confirmedAt && <Row label="Charged" value={formatInstant(payment.confirmedAt)} />}
        {payment.providerRef && (
          <Row label="Reference" value={<span className="font-mono text-[0.72rem]">{payment.providerRef}</span>} />
        )}
        {payment.failureMessage && <Row label="Note" value={payment.failureMessage} />}
        {payment.status === "REFUNDED" && (
          <Row label="Refunded" value={`${owedBack} · ${formatInstant(payment.refundedAt)}`} />
        )}
        {payment.refundRef && (
          <Row label="Refund reference" value={<span className="font-mono text-[0.72rem]">{payment.refundRef}</span>} />
        )}
      </dl>
      {payment.status === "REFUND_REQUIRED" && (
        <Notice tone="error" className="mt-4">
          {owedBack} is owed back. Asked {payment.refundAttempts} time{payment.refundAttempts === 1 ? "" : "s"}
          {payment.refundLastAttemptAt ? `, last ${relativeTime(payment.refundLastAttemptAt)}` : ""}
          {payment.refundError ? `; the provider said: ${payment.refundError}` : "."}
        </Notice>
      )}
    </li>
  );
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section>
      <h2 className="kicker mb-3">{title}</h2>
      {children}
    </section>
  );
}

function Row({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div className="flex justify-between gap-4">
      <dt className="shrink-0 text-muted">{label}</dt>
      <dd className="min-w-0 text-right text-bone/90 [overflow-wrap:anywhere]">{value}</dd>
    </div>
  );
}
