import { useEffect, useState } from 'react'
import { fetchDeadLetter, retryDeadJob } from '../api.js'
import { fmtDateTime as fmt } from '../time.js'

const shortId = (id) => (id ? id.slice(0, 8) : '')

export default function DeadLetter() {
  const [jobs, setJobs] = useState([])
  const [busyId, setBusyId] = useState(null)

  const load = () => fetchDeadLetter().then(setJobs).catch(() => {})

  useEffect(() => {
    load()
    const id = setInterval(load, 5000)
    return () => clearInterval(id)
  }, [])

  async function retry(id) {
    setBusyId(id)
    try {
      await retryDeadJob(id)
      setJobs((js) => js.filter((j) => j.id !== id))
    } catch {
      /* ignore */
    } finally {
      setBusyId(null)
    }
  }

  return (
    <div className="space-y-3">
      <div className="flex items-baseline gap-3">
        <h1 className="font-sans font-semibold text-lg text-primary">Dead Letter Queue</h1>
        <span className="text-muted font-mono text-sm">{jobs.length} job(s)</span>
      </div>

      <div className="bg-surface border border-edge overflow-auto">
        <table className="w-full text-sm font-mono border-collapse">
          <thead>
            <tr className="text-muted text-left border-b border-edge text-[11px] uppercase tracking-wider">
              <th className="p-2 font-medium">ID</th>
              <th className="p-2 font-medium">Type</th>
              <th className="p-2 font-medium">Attempts</th>
              <th className="p-2 font-medium">Error</th>
              <th className="p-2 font-medium">Failed At</th>
              <th className="p-2 font-medium"></th>
            </tr>
          </thead>
          <tbody>
            {jobs.map((j) => (
              <tr key={j.id} className="border-b border-edge/50 hover:bg-black/40 align-top">
                <td className="p-2 text-muted" title={j.id}>
                  {shortId(j.id)}
                </td>
                <td className="p-2 font-sans text-primary">{j.type}</td>
                <td className="p-2 text-muted">
                  {j.attempts}/{j.maxAttempts}
                </td>
                <td className="p-2 text-danger max-w-md break-words">{j.errorMessage || '—'}</td>
                <td className="p-2 text-muted">{fmt(j.completedAt || j.updatedAt)}</td>
                <td className="p-2 text-right">
                  <button
                    onClick={() => retry(j.id)}
                    disabled={busyId === j.id}
                    className="px-2 py-1 text-xs font-mono border border-accent text-accent hover:bg-accent hover:text-bg disabled:opacity-50"
                  >
                    {busyId === j.id ? '…' : 'retry'}
                  </button>
                </td>
              </tr>
            ))}
            {jobs.length === 0 && (
              <tr>
                <td colSpan={6} className="p-6 text-center text-muted">
                  no dead jobs — healthy queue
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  )
}
