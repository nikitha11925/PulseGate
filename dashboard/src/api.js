// Thin fetch wrappers around the PulseGate REST API. All requests go through the Vite proxy
// (/api -> backend), so there are no hardcoded hosts here.

async function asJson(res) {
  if (!res.ok) {
    const text = await res.text().catch(() => '')
    throw new Error(`${res.status} ${res.statusText} ${text}`.trim())
  }
  return res.json()
}

export function submitJob(body) {
  return fetch('/api/jobs', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  }).then(asJson)
}

export function fetchJobs({ status, type, page = 0, size = 25 } = {}) {
  const params = new URLSearchParams()
  if (status) params.set('status', status)
  if (type) params.set('type', type)
  params.set('page', String(page))
  params.set('size', String(size))
  return fetch(`/api/jobs?${params.toString()}`).then(asJson)
}

export function fetchStats() {
  return fetch('/api/stats').then(asJson)
}

export function fetchDeadLetter() {
  return fetch('/api/dead-letter').then(asJson)
}

export function retryDeadJob(id) {
  return fetch(`/api/dead-letter/${id}/retry`, { method: 'POST' }).then(asJson)
}

export function cancelJob(id) {
  return fetch(`/api/jobs/${id}`, { method: 'DELETE' }).then(asJson)
}

/**
 * Subscribe to the SSE stats stream. The backend names the event "stats"
 * (ServerSentEvent.event("stats")), so we listen for that explicitly.
 * Returns the EventSource so the caller can close() it on unmount.
 */
export function openStatsStream(onStats, onError) {
  const es = new EventSource('/api/stats/stream')
  const handle = (e) => {
    try {
      onStats(JSON.parse(e.data))
    } catch {
      /* ignore malformed frame */
    }
  }
  es.addEventListener('stats', handle)
  es.onmessage = handle // fallback for unnamed events
  es.onerror = (e) => {
    if (onError) onError(e)
  }
  return es
}
