"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { apiErrorCode, apiErrorMessage, apiFieldErrors } from "@/lib/api";
import { useCreateEvent } from "@/hooks/useAdmin";
import { AdminHeader } from "./admin-shell";
import { EventForm } from "./event-form";
import { Notice, useNotice } from "@/components/ui/notice";
import type { EventUpsert } from "@/lib/types";

export function EventCreate() {
  const router = useRouter();
  const create = useCreateEvent();
  const { notice, show, clear } = useNotice();
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const submit = (body: EventUpsert) => {
    setFieldErrors({});
    clear();
    create.mutate(body, {
      // Straight to the editor: a new event still needs its seating layout,
      // and that is the screen that offers it.
      onSuccess: (event) => router.push(`/admin/events/${event.id}`),
      onError: (err) => {
        if (apiErrorCode(err) === "SLUG_TAKEN") {
          setFieldErrors({ slug: "Another event already uses this slug." });
          show("error", "That slug is taken.");
          return;
        }
        setFieldErrors(apiFieldErrors(err));
        show("error", apiErrorMessage(err, "Could not create the event."));
      },
    });
  };

  return (
    <>
      <AdminHeader
        kicker={
          <Link href="/admin/events" className="ulink">
            Events
          </Link>
        }
        title="New event"
        description="Create the fixture first; its seating layout comes next."
      />

      {notice && (
        <Notice tone={notice.tone} onDismiss={clear} className="mt-6">
          {notice.message}
        </Notice>
      )}

      <div className="mt-8 max-w-3xl">
        <EventForm mode="create" busy={create.isPending} fieldErrors={fieldErrors} onSubmit={submit} />
      </div>
    </>
  );
}
