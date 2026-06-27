// Display helpers for the job timestamps returned by the API.
//
// The backend runs in the timezone set by the TZ env (default Asia/Kolkata), so the timestamps it
// serializes (created_at/updated_at/completed_at) are naive wall-clock strings already in that
// zone, e.g. "2026-06-27T22:17:32". The dashboard is viewed in the same zone, so we parse them as
// local time and render directly — no UTC conversion. (The SSE stats `timestamp` is a true UTC
// Instant with a 'Z', and is handled separately where it's consumed.)

/** Local time-of-day, e.g. "10:17:32 PM". */
export function fmtTime(ts) {
  return ts ? new Date(ts).toLocaleTimeString() : '—'
}

/** Local date + time, e.g. "6/27/2026, 10:17:32 PM". */
export function fmtDateTime(ts) {
  return ts ? new Date(ts).toLocaleString() : '—'
}
