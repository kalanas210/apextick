/**
 * Whether a pay request that failed has settled its attempt, so the next submit is a new payment
 * and needs a new Idempotency-Key.
 *
 * Any answer the API gives settles it: a decline, an order that closed, a refused request. Two
 * kinds of failure do not. No answer at all (a dropped connection, a timeout) and a server error
 * may both have charged already, so the resend has to carry the same key and get the first result
 * back instead of charging again. And PAYMENT_IN_PROGRESS means that attempt is still running.
 */
export function settlesAttempt(status: number | undefined, code: string | undefined): boolean {
  if (status === undefined || status >= 500) {
    return false;
  }
  return code !== "PAYMENT_IN_PROGRESS";
}
