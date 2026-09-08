"use client";

import { useMemo, useState } from "react";
import type { FormEvent } from "react";
import { slugify } from "@/lib/format";
import { EVENT_LABEL, EVENT_STATUSES } from "@/lib/status";
import { useAdminTeams, useSeriesList } from "@/hooks/useAdmin";
import { Field, Input, Select, Textarea } from "@/components/ui/field";
import { Button } from "@/components/ui/button";
import type { Currency, EventDetail, EventStatus, EventUpsert, Sport } from "@/lib/types";

const CURRENCIES: Currency[] = ["USD", "GBP", "INR"];
const SPORTS: Sport[] = ["cricket", "football"];

/**
 * An Instant round-trips through <input type="datetime-local">, which has no zone
 * at all. The field is labelled UTC and converted literally in both directions —
 * reading the browser's offset into it instead would silently move every event by
 * however many hours the operator happens to sit from Greenwich.
 */
function toLocalInput(instant: string | null | undefined): string {
  return instant ? instant.slice(0, 16) : "";
}

function toInstant(local: string): string | null {
  return local ? `${local}:00Z` : null;
}

interface FormState {
  name: string;
  slug: string;
  sport: string;
  seriesId: string;
  homeTeamId: string;
  awayTeamId: string;
  startsAt: string;
  timeZone: string;
  venue: string;
  city: string;
  country: string;
  stage: string;
  status: string;
  image: string;
  blurb: string;
  currency: string;
  salesStartAt: string;
  salesEndAt: string;
}

/**
 * Seeds every field, including the ones this form does not emphasise: PUT is a
 * full replace, so anything left out of the payload is nulled on the event.
 */
function seed(initial?: EventDetail): FormState {
  return {
    name: initial?.name ?? "",
    slug: initial?.slug ?? "",
    sport: initial?.sport ?? "cricket",
    seriesId: initial?.seriesId != null ? String(initial.seriesId) : "",
    homeTeamId: initial?.home?.id != null ? String(initial.home.id) : "",
    awayTeamId: initial?.away?.id != null ? String(initial.away.id) : "",
    startsAt: toLocalInput(initial?.startsAt),
    timeZone: initial?.timeZone ?? "UTC",
    venue: initial?.stadium ?? "",
    city: initial?.city ?? "",
    country: initial?.country ?? "",
    stage: initial?.stage ?? "",
    status: initial?.status ?? "draft",
    image: initial?.image ?? "",
    blurb: initial?.blurb ?? "",
    currency: initial?.currency ?? "USD",
    salesStartAt: toLocalInput(initial?.salesStartAt),
    salesEndAt: toLocalInput(initial?.salesEndAt),
  };
}

export function EventForm({
  mode,
  initial,
  busy,
  fieldErrors,
  onSubmit,
}: {
  mode: "create" | "edit";
  initial?: EventDetail;
  busy?: boolean;
  fieldErrors?: Record<string, string>;
  onSubmit: (body: EventUpsert) => void;
}) {
  const [form, setForm] = useState<FormState>(() => seed(initial));
  // Once someone edits the slug themselves, the name stops driving it.
  const [slugTouched, setSlugTouched] = useState(mode === "edit");
  const [localErrors, setLocalErrors] = useState<Record<string, string>>({});

  const teams = useAdminTeams(form.sport);
  const series = useSeriesList();

  const errors = useMemo(
    () => ({ ...localErrors, ...(fieldErrors ?? {}) }),
    [localErrors, fieldErrors],
  );

  const set = <K extends keyof FormState>(key: K, value: FormState[K]) =>
    setForm((f) => ({ ...f, [key]: value }));

  const teamOptions = (teams.data ?? []).map((t) => ({ value: String(t.id), label: t.name }));

  const submit = (e: FormEvent) => {
    e.preventDefault();

    const next: Record<string, string> = {};
    if (!form.name.trim()) next.name = "Give the event a name.";
    if (!form.slug.trim()) next.slug = "The slug is the event's URL.";
    if (!form.startsAt) next.startsAt = "When does it start?";
    if (!form.venue.trim()) next.venue = "Where is it played?";
    if (form.homeTeamId && form.homeTeamId === form.awayTeamId) {
      next.awayTeamId = "A team cannot play itself.";
    }
    setLocalErrors(next);
    if (Object.keys(next).length) return;

    onSubmit({
      name: form.name.trim(),
      slug: form.slug.trim(),
      sport: form.sport as Sport,
      seriesId: form.seriesId ? Number(form.seriesId) : null,
      homeTeamId: form.homeTeamId ? Number(form.homeTeamId) : null,
      awayTeamId: form.awayTeamId ? Number(form.awayTeamId) : null,
      startsAt: toInstant(form.startsAt)!,
      timeZone: form.timeZone.trim() || "UTC",
      venue: form.venue.trim(),
      city: form.city.trim() || null,
      country: form.country.trim() || null,
      stage: form.stage.trim() || null,
      status: (form.status || "draft") as EventStatus,
      image: form.image.trim() || null,
      blurb: form.blurb.trim() || null,
      currency: form.currency as Currency,
      salesStartAt: toInstant(form.salesStartAt),
      salesEndAt: toInstant(form.salesEndAt),
    });
  };

  return (
    <form onSubmit={submit} className="space-y-10">
      <Group title="Identity">
        <Field label="Name" required error={errors.name} className="sm:col-span-2">
          {(a) => (
            <Input
              {...a}
              value={form.name}
              onChange={(e) => {
                set("name", e.target.value);
                if (!slugTouched) set("slug", slugify(e.target.value));
              }}
              placeholder="Australia v England"
            />
          )}
        </Field>

        <Field
          label="Slug"
          required
          error={errors.slug}
          hint="Appears in the public URL: /events/<slug>"
          className="sm:col-span-2"
        >
          {(a) => (
            <Input
              {...a}
              value={form.slug}
              onChange={(e) => {
                setSlugTouched(true);
                set("slug", e.target.value);
              }}
              placeholder="australia-england-super-8"
            />
          )}
        </Field>

        <Field label="Sport" error={errors.sport}>
          {(a) => (
            <Select
              {...a}
              value={form.sport}
              onChange={(e) => {
                set("sport", e.target.value);
                // Team lists are per sport, so held ids would no longer be valid.
                set("homeTeamId", "");
                set("awayTeamId", "");
              }}
              options={SPORTS.map((s) => ({ value: s, label: s === "cricket" ? "Cricket" : "Football" }))}
            />
          )}
        </Field>

        <Field label="Series" error={errors.seriesId} hint="Optional">
          {(a) => (
            <Select
              {...a}
              value={form.seriesId}
              onChange={(e) => set("seriesId", e.target.value)}
              placeholder="No series"
              options={(series.data ?? []).map((s) => ({ value: String(s.id), label: s.name }))}
            />
          )}
        </Field>

        <Field label="Home team" error={errors.homeTeamId} hint="Optional">
          {(a) => (
            <Select
              {...a}
              value={form.homeTeamId}
              onChange={(e) => set("homeTeamId", e.target.value)}
              placeholder="None"
              options={teamOptions}
            />
          )}
        </Field>

        <Field label="Away team" error={errors.awayTeamId} hint="Optional">
          {(a) => (
            <Select
              {...a}
              value={form.awayTeamId}
              onChange={(e) => set("awayTeamId", e.target.value)}
              placeholder="None"
              options={teamOptions}
            />
          )}
        </Field>
      </Group>

      <Group title="Schedule">
        <Field label="Starts at (UTC)" required error={errors.startsAt}>
          {(a) => (
            <Input
              {...a}
              type="datetime-local"
              value={form.startsAt}
              onChange={(e) => set("startsAt", e.target.value)}
            />
          )}
        </Field>

        <Field
          label="Display time zone"
          error={errors.timeZone}
          hint="How the date and time are shown to buyers, e.g. Asia/Colombo"
        >
          {(a) => (
            <Input
              {...a}
              value={form.timeZone}
              onChange={(e) => set("timeZone", e.target.value)}
              placeholder="UTC"
            />
          )}
        </Field>

        <Field label="Sales open (UTC)" error={errors.salesStartAt} hint="Optional">
          {(a) => (
            <Input
              {...a}
              type="datetime-local"
              value={form.salesStartAt}
              onChange={(e) => set("salesStartAt", e.target.value)}
            />
          )}
        </Field>

        <Field label="Sales close (UTC)" error={errors.salesEndAt} hint="Optional">
          {(a) => (
            <Input
              {...a}
              type="datetime-local"
              value={form.salesEndAt}
              onChange={(e) => set("salesEndAt", e.target.value)}
            />
          )}
        </Field>
      </Group>

      <Group title="Venue">
        <Field label="Stadium" required error={errors.venue} className="sm:col-span-2">
          {(a) => (
            <Input
              {...a}
              value={form.venue}
              onChange={(e) => set("venue", e.target.value)}
              placeholder="Eden Gardens"
            />
          )}
        </Field>
        <Field label="City" error={errors.city}>
          {(a) => <Input {...a} value={form.city} onChange={(e) => set("city", e.target.value)} />}
        </Field>
        <Field label="Country" error={errors.country}>
          {(a) => (
            <Input {...a} value={form.country} onChange={(e) => set("country", e.target.value)} />
          )}
        </Field>
      </Group>

      <Group title="Presentation">
        <Field label="Stage" error={errors.stage} hint="e.g. Super 8, Matchweek 12">
          {(a) => <Input {...a} value={form.stage} onChange={(e) => set("stage", e.target.value)} />}
        </Field>
        <Field label="Status" error={errors.status}>
          {(a) => (
            <Select
              {...a}
              value={form.status}
              onChange={(e) => set("status", e.target.value)}
              options={EVENT_STATUSES.map((s) => ({ value: s, label: EVENT_LABEL[s] }))}
            />
          )}
        </Field>
        <Field label="Hero image URL" error={errors.image} className="sm:col-span-2">
          {(a) => (
            <Input
              {...a}
              value={form.image}
              onChange={(e) => set("image", e.target.value)}
              placeholder="https://…"
            />
          )}
        </Field>
        <Field label="Blurb" error={errors.blurb} className="sm:col-span-2">
          {(a) => (
            <Textarea {...a} value={form.blurb} onChange={(e) => set("blurb", e.target.value)} />
          )}
        </Field>
      </Group>

      <Group title="Commerce">
        <Field label="Currency" error={errors.currency} hint="Tier prices are quoted in this">
          {(a) => (
            <Select
              {...a}
              value={form.currency}
              onChange={(e) => set("currency", e.target.value)}
              options={CURRENCIES.map((c) => ({ value: c, label: c }))}
            />
          )}
        </Field>
      </Group>

      <div className="flex items-center gap-3 border-t border-line pt-6">
        <Button type="submit" size="md" className={busy ? "pointer-events-none opacity-60" : ""}>
          {busy ? "Saving…" : mode === "create" ? "Create event" : "Save changes"}
        </Button>
        {mode === "edit" && (
          <p className="text-[0.75rem] text-faint">Saving replaces every field shown above.</p>
        )}
      </div>
    </form>
  );
}

function Group({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section>
      <h2 className="kicker mb-4">{title}</h2>
      <div className="grid gap-4 sm:grid-cols-2">{children}</div>
    </section>
  );
}
