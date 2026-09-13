"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import type { FormEvent } from "react";
import { cn } from "@/lib/cn";
import { apiErrorMessage, apiProblem, apiStatus } from "@/lib/api";
import { DEFAULT_FILTERS } from "@/lib/catalog";
import { formatInstant, relativeTime } from "@/lib/format";
import { kickoffLabel, refusalOf, type GateProblem, type ScanRefusal } from "@/lib/gate";
import { useEventList } from "@/hooks/useCatalog";
import { useScanTicket } from "@/hooks/useGate";
import { AdminHeader } from "./admin-shell";
import { Button } from "@/components/ui/button";
import { Field, Select, Textarea } from "@/components/ui/field";
import { Notice } from "@/components/ui/notice";
import { Camera, Check, X } from "@/components/ui/icons";
import type { Ticket } from "@/lib/types";

/** Fast enough to feel instant at a turnstile, slow enough not to pin a CPU. */
const SCAN_INTERVAL_MS = 250;
const LOG_LIMIT = 20;
/** The event this device's gate admits to outlives a reload: a steward sets it once a shift. */
const GATE_EVENT_KEY = "apextick.gate.eventId";

type Refused = ScanRefusal & { message: string };
type Outcome = { kind: "admitted"; ticket: Ticket } | Refused;

interface LogEntry {
  id: number;
  admitted: boolean;
  label: string;
  at: string;
}

type CameraState =
  | { kind: "idle" }
  | { kind: "running" }
  | { kind: "unsupported"; reason: string }
  | { kind: "denied"; reason: string };

function storedEventId(): number | null {
  try {
    const id = Number(window.localStorage.getItem(GATE_EVENT_KEY));
    return Number.isInteger(id) && id > 0 ? id : null;
  } catch {
    return null;
  }
}

function rememberEventId(id: number | null) {
  try {
    if (id === null) {
      window.localStorage.removeItem(GATE_EVENT_KEY);
    } else {
      window.localStorage.setItem(GATE_EVENT_KEY, String(id));
    }
  } catch {
    // storage is blocked: the steward picks the event again after a reload
  }
}

function logLabel(refused: Refused): string {
  switch (refused.kind) {
    case "already":
      return "Already scanned";
    case "wrong-event":
      return refused.name ? `Wrong event: ${refused.name}` : "Wrong event";
    case "event-closed":
      return "Gates closed";
    case "cancelled":
      return "Cancelled ticket";
    case "unknown":
      return "Unknown ticket";
    default:
      return "Rejected";
  }
}

export function TicketScanner() {
  const scan = useScanTicket();
  const { data: events, isLoading: eventsLoading } = useEventList(DEFAULT_FILTERS);
  const [eventId, setEventId] = useState<number | null>(() => storedEventId());
  const [token, setToken] = useState("");
  const [outcome, setOutcome] = useState<Outcome | null>(null);
  const [log, setLog] = useState<LogEntry[]>([]);
  const [camera, setCamera] = useState<CameraState>({ kind: "idle" });

  const gateEvent = events?.find((e) => e.id === eventId) ?? null;

  const videoRef = useRef<HTMLVideoElement>(null);
  // The stream lives in a ref, not state: stopCamera has to be callable from
  // unmount without the closure having gone stale, and a camera light that
  // never goes out is the classic bug here.
  const streamRef = useRef<MediaStream | null>(null);
  const loopRef = useRef<number | null>(null);
  // Bumped by every start and every stop. getUserMedia resolves long after it is
  // called -- often behind a permission prompt -- so a start that lands after the
  // operator has navigated away, or pressed Stop, or started again, has to be able
  // to tell that its stream is no longer wanted and hang it up itself.
  const sessionRef = useRef(0);
  // detect() on a large frame can outlast the interval between ticks; without this
  // two of them read the same QR code and the ticket is verified twice.
  const busyRef = useRef(false);

  const stopCamera = useCallback(() => {
    sessionRef.current += 1;
    if (loopRef.current !== null) {
      cancelAnimationFrame(loopRef.current);
      loopRef.current = null;
    }
    streamRef.current?.getTracks().forEach((t) => t.stop());
    streamRef.current = null;
    if (videoRef.current) videoRef.current.srcObject = null;
    setCamera((c) => (c.kind === "running" ? { kind: "idle" } : c));
  }, []);

  useEffect(() => stopCamera, [stopCamera]);

  const pushLog = useCallback((admitted: boolean, label: string) => {
    setLog((l) =>
      [{ id: Date.now(), admitted, label, at: new Date().toISOString() }, ...l].slice(0, LOG_LIMIT),
    );
  }, []);

  const submit = useCallback(
    (raw: string) => {
      const value = raw.trim();
      if (!value || eventId === null) return;
      scan.mutate(
        { qrToken: value, eventId },
        {
          onSuccess: (result) => {
            setOutcome({ kind: "admitted", ticket: result.ticket });
            pushLog(true, `${result.ticket.seatLabel} · ${result.ticket.sectionName}`);
            setToken("");
          },
          onError: (err) => {
            const refused: Refused = {
              ...refusalOf(apiStatus(err), apiProblem(err) as GateProblem | undefined),
              message: apiErrorMessage(err, "Could not verify that ticket."),
            };
            setOutcome(refused);
            pushLog(false, logLabel(refused));
          },
        },
      );
    },
    [scan, eventId, pushLog],
  );

  /** A different gate event starts a clean slate: nothing on screen was checked against it. */
  const chooseEvent = (id: number | null) => {
    stopCamera();
    setEventId(id);
    rememberEventId(id);
    setOutcome(null);
    setLog([]);
  };

  const startCamera = useCallback(async () => {
    const session = (sessionRef.current += 1);
    // Three separate things can be missing, and each needs its own sentence:
    // saying "camera unavailable" to all of them helps nobody.
    if (!window.isSecureContext) {
      setCamera({
        kind: "unsupported",
        reason:
          "Cameras only work on a secure origin. Open this over HTTPS (or on localhost) to scan.",
      });
      return;
    }
    if (!window.BarcodeDetector) {
      setCamera({
        kind: "unsupported",
        reason:
          "This browser has no barcode detector. Chrome and Edge do — or paste the token below.",
      });
      return;
    }

    let detector: BarcodeDetector;
    try {
      const formats = await BarcodeDetector.getSupportedFormats();
      if (!formats.includes("qr_code")) {
        setCamera({
          kind: "unsupported",
          reason: "This browser's barcode detector does not read QR codes.",
        });
        return;
      }
      detector = new BarcodeDetector({ formats: ["qr_code"] });
    } catch {
      setCamera({ kind: "unsupported", reason: "The barcode detector could not be started." });
      return;
    }

    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: "environment" },
      });
      if (session !== sessionRef.current) {
        // Stopped, restarted, or unmounted while the prompt was up: nobody owns
        // this stream, so hang it up here or the camera light never goes out.
        stream.getTracks().forEach((t) => t.stop());
        return;
      }
      streamRef.current = stream;
      if (videoRef.current) {
        videoRef.current.srcObject = stream;
        await videoRef.current.play();
      }
      setCamera({ kind: "running" });
    } catch (err) {
      const name = err instanceof DOMException ? err.name : "";
      setCamera({
        kind: "denied",
        reason:
          name === "NotAllowedError"
            ? "Camera permission was refused. Allow it in the address bar, then try again."
            : name === "NotFoundError"
              ? "No camera found on this device."
              : name === "NotReadableError"
                ? "The camera is already in use by another application."
                : "The camera could not be started.",
      });
      return;
    }

    let last = 0;
    const tick = async (time: number) => {
      loopRef.current = requestAnimationFrame(tick);
      if (time - last < SCAN_INTERVAL_MS) return;
      last = time;
      const video = videoRef.current;
      if (!video || video.readyState < 2) return;
      if (busyRef.current) return;
      busyRef.current = true;
      try {
        const found = await detector.detect(video);
        // Re-check the session: a slow detect can resolve after Stop, and the
        // ticket behind an already-cancelled scan must not be spent.
        if (found.length > 0 && session === sessionRef.current) {
          stopCamera();
          submit(found[0].rawValue);
        }
      } catch {
        // A frame that fails to decode is normal; the next one will do.
      } finally {
        busyRef.current = false;
      }
    };
    loopRef.current = requestAnimationFrame(tick);
  }, [stopCamera, submit]);

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    submit(token);
  };

  const admitted = log.filter((l) => l.admitted).length;
  const ready = eventId !== null;

  return (
    <>
      <AdminHeader
        kicker="Gate"
        title="Scan tickets"
        description="Choose the event this gate admits to, then point a camera at the QR code or paste the token."
        action={
          log.length > 0 ? (
            <p className="text-[0.8rem] text-muted">
              <span className="tnum text-accent">{admitted}</span> admitted ·{" "}
              <span className="tnum">{log.length - admitted}</span> rejected
            </p>
          ) : undefined
        }
      />

      <div className="mt-8 grid gap-10 lg:grid-cols-12 lg:gap-12">
        <div className="lg:col-span-7">
          <section>
            <h2 className="kicker mb-3">This gate admits to</h2>
            <Field
              label="Event"
              hint={
                gateEvent
                  ? `${kickoffLabel(gateEvent.startsAt, gateEvent.timeZone)} · ${gateEvent.stadium}`
                  : "A ticket for any other event is turned away, and stays unused."
              }
            >
              {(a) => (
                <Select
                  {...a}
                  value={eventId === null ? "" : String(eventId)}
                  onChange={(e) => chooseEvent(e.target.value ? Number(e.target.value) : null)}
                  options={(events ?? []).map((e) => ({ value: String(e.id), label: `${e.name} · ${e.date}` }))}
                  placeholder={eventsLoading ? "Loading events…" : "Choose the event at this gate"}
                  disabled={eventsLoading}
                />
              )}
            </Field>
          </section>

          <section className="mt-8">
            <h2 className="kicker mb-3">Camera</h2>
            <div className="overflow-hidden rounded-2xl border border-line bg-ink-2">
              <div className="relative aspect-video bg-ink">
                <video
                  ref={videoRef}
                  playsInline
                  muted
                  className={cn(
                    "h-full w-full object-cover",
                    camera.kind !== "running" && "invisible",
                  )}
                />
                {camera.kind !== "running" && (
                  <div className="absolute inset-0 grid place-items-center p-6 text-center">
                    <div>
                      <Camera className="mx-auto h-8 w-8 text-faint" />
                      <p className="mt-3 text-[0.82rem] text-muted">
                        {camera.kind === "idle"
                          ? ready
                            ? "Camera off."
                            : "Choose the event first, so every scan is checked against it."
                          : camera.reason}
                      </p>
                    </div>
                  </div>
                )}
              </div>
              <div className="flex items-center gap-3 border-t border-line px-5 py-3">
                {camera.kind === "running" ? (
                  <Button onClick={stopCamera} size="sm" variant="outline">
                    Stop camera
                  </Button>
                ) : (
                  <Button
                    onClick={() => void startCamera()}
                    size="sm"
                    variant="outline"
                    className={ready ? "" : "pointer-events-none opacity-60"}
                  >
                    Start camera
                  </Button>
                )}
                <p className="text-[0.75rem] text-faint">
                  {camera.kind === "running" ? "Scanning…" : "Scanning stops on the first hit."}
                </p>
              </div>
            </div>
          </section>

          <section className="mt-8">
            <h2 className="kicker mb-3">Or paste a token</h2>
            <form onSubmit={onSubmit} className="space-y-4">
              <Field label="QR token" hint="The value encoded in the ticket's QR code">
                {(a) => (
                  <Textarea
                    {...a}
                    value={token}
                    onChange={(e) => setToken(e.target.value)}
                    placeholder="apx_…"
                    autoFocus
                  />
                )}
              </Field>
              <Button
                type="submit"
                size="md"
                className={scan.isPending || !ready ? "pointer-events-none opacity-60" : ""}
              >
                {scan.isPending ? "Checking…" : "Verify"}
              </Button>
            </form>
          </section>
        </div>

        <aside className="lg:col-span-5">
          {/* Capped and scrollable: a sticky column taller than the viewport
              would strand whatever sits at its bottom. */}
            <div className="space-y-6 lg:sticky lg:top-8 lg:max-h-[calc(100vh-4rem)] lg:overflow-y-auto lg:pr-1 [scrollbar-width:thin]">
            <div aria-live="assertive">
              {outcome ? <Result outcome={outcome} /> : (
                <div className="rounded-2xl border border-line bg-ink-2 p-8 text-center">
                  <p className="text-[0.86rem] text-muted">
                    {ready ? "No ticket checked yet." : "Choose the event this gate admits to."}
                  </p>
                </div>
              )}
            </div>

            {log.length > 0 && (
              <section className="rounded-2xl border border-line bg-ink-2 p-6">
                <h2 className="kicker">This session</h2>
                <ul className="mt-4 space-y-2 text-[0.8rem]">
                  {log.map((entry) => (
                    <li key={entry.id} className="flex items-center justify-between gap-3">
                      <span className="flex min-w-0 items-center gap-2">
                        {entry.admitted ? (
                          <Check className="h-3.5 w-3.5 shrink-0 text-accent" />
                        ) : (
                          <X className="h-3.5 w-3.5 shrink-0 text-[#ff6b6b]" />
                        )}
                        <span className="truncate text-muted">{entry.label}</span>
                      </span>
                      <span className="tnum shrink-0 text-[0.72rem] text-faint">
                        {relativeTime(entry.at)}
                      </span>
                    </li>
                  ))}
                </ul>
                <p className="mt-4 text-[0.72rem] text-faint">Cleared when this page reloads.</p>
              </section>
            )}
          </div>
        </aside>
      </div>
    </>
  );
}

function Result({ outcome }: { outcome: Outcome }) {
  if (outcome.kind === "admitted") {
    const t = outcome.ticket;
    return (
      <Card tone="good" word="Admitted">
        <dl className="mt-4 space-y-1.5 text-[0.84rem]">
          <Row label="Seat" value={t.seatLabel} />
          <Row label="Section" value={t.sectionName} />
          <Row label="Tier" value={t.tierName} />
          <Row label="Issued" value={formatInstant(t.issuedAt)} />
        </dl>
      </Card>
    );
  }

  if (outcome.kind === "already") {
    return (
      <Card tone="bad" word="Already scanned">
        <p className="mt-3 text-[0.84rem] text-muted">
          {outcome.usedAt
            ? `First scanned ${relativeTime(outcome.usedAt)}, at ${formatInstant(outcome.usedAt)}.`
            : outcome.message}
        </p>
      </Card>
    );
  }

  if (outcome.kind === "wrong-event") {
    const when = kickoffLabel(outcome.startsAt, outcome.timeZone);
    return (
      <Card tone="bad" word="Wrong event">
        <p className="mt-3 text-[0.84rem] text-muted">
          {outcome.name
            ? `This ticket is for ${outcome.name}${when ? `, ${when}` : ""}${outcome.venue ? `, at ${outcome.venue}` : ""}.`
            : "This ticket is for a different event."}{" "}
          It has not been used and still admits there.
        </p>
      </Card>
    );
  }

  if (outcome.kind === "event-closed") {
    return (
      <Card tone="bad" word="Gates closed">
        <p className="mt-3 text-[0.84rem] text-muted">
          {outcome.status === "cancelled"
            ? "This event has been cancelled, so nobody is admitted."
            : "This event is not published, so its gates are not open."}
        </p>
      </Card>
    );
  }

  if (outcome.kind === "cancelled") {
    return (
      <Card tone="bad" word="Cancelled">
        <p className="mt-3 text-[0.84rem] text-muted">
          This ticket was cancelled and does not admit anyone.
        </p>
      </Card>
    );
  }

  if (outcome.kind === "unknown") {
    return (
      <Card tone="muted" word="Unknown ticket">
        <p className="mt-3 text-[0.84rem] text-muted">
          No ticket matches that code. Check it was scanned in full.
        </p>
      </Card>
    );
  }

  return (
    <div className="rounded-2xl border border-line bg-ink-2 p-6">
      <Notice tone="error">{outcome.message}</Notice>
    </div>
  );
}

function Card({
  tone,
  word,
  children,
}: {
  tone: "good" | "bad" | "muted";
  word: string;
  children: React.ReactNode;
}) {
  return (
    <div
      className={cn(
        "rounded-2xl border p-6",
        tone === "good" && "border-accent/50 bg-accent/[0.06]",
        tone === "bad" && "border-[#ff6b6b]/40 bg-[#ff6b6b]/[0.06]",
        tone === "muted" && "border-line bg-ink-2",
      )}
    >
      <p
        className={cn(
          "flex items-center gap-2 font-display text-xl font-semibold tracking-tight",
          tone === "good" && "text-accent",
          tone === "bad" && "text-[#ff6b6b]",
          tone === "muted" && "text-bone",
        )}
      >
        {/* Icon and word both, so the outcome does not depend on colour alone. */}
        {tone === "good" ? <Check className="h-5 w-5" /> : <X className="h-5 w-5" />}
        {word}
      </p>
      {children}
    </div>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between gap-3">
      <dt className="text-muted">{label}</dt>
      <dd className="text-bone">{value}</dd>
    </div>
  );
}
