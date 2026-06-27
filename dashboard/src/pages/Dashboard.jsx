import { useEffect, useState } from 'react'
import MetricsBar from '../components/MetricsBar.jsx'
import QueueDepthChart from '../components/QueueDepthChart.jsx'
import JobTable from '../components/JobTable.jsx'
import { fetchJobs, cancelJob, openStatsStream } from '../api.js'
import { STATUSES, JOB_TYPES } from '../theme.js'

const selectCls =
  'bg-surface border border-edge px-2 py-1 text-xs font-mono text-primary focus:outline-none focus:border-accent'

export default function Dashboard({ refreshKey }) {
  const [stats, setStats] = useState(null)
  const [history, setHistory] = useState([])
  const [jobs, setJobs] = useState([])
  const [statusFilter, setStatusFilter] = useState('')
  const [typeFilter, setTypeFilter] = useState('')
  const [sortOrder, setSortOrder] = useState('newest')

  // Live stats via SSE (no polling). Keep a rolling 30-point window for the chart.
  useEffect(() => {
    const es = openStatsStream((s) => {
      setStats(s)
      setHistory((h) => {
        const point = {
          t: new Date(s.timestamp).toLocaleTimeString(),
          queueDepth: s.queueDepth,
          processing: s.processing,
        }
        return [...h, point].slice(-30)
      })
    })
    return () => es.close()
  }, [])

  // Job list — polled every 3s, and immediately when filters or refreshKey change.
  useEffect(() => {
    let active = true
    const load = () =>
      fetchJobs({ status: statusFilter || undefined, type: typeFilter || undefined, size: 25 })
        .then((j) => active && setJobs(j))
        .catch(() => {})
    load()
    const id = setInterval(load, 3000)
    return () => {
      active = false
      clearInterval(id)
    }
  }, [statusFilter, typeFilter, refreshKey])

  async function handleCancel(id) {
    try {
      await cancelJob(id)
      // Optimistically reflect the cancel; the 3s poll confirms the real terminal state.
      setJobs((js) => js.map((j) => (j.id === id ? { ...j, status: 'CANCELLED' } : j)))
    } catch {
      /* ignore (e.g. the job finished first -> 409) */
    }
  }

  // The API already returns newest-first; this lets the user flip to oldest-first too.
  const sortedJobs = [...jobs].sort((a, b) => {
    const cmp = (b.createdAt || '').localeCompare(a.createdAt || '')
    return sortOrder === 'newest' ? cmp : -cmp
  })

  return (
    <div className="space-y-4">
      <MetricsBar stats={stats} />

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        <div className="lg:col-span-2 space-y-2">
          <div className="flex items-center gap-2">
            <span className="text-muted text-[11px] uppercase tracking-widest font-sans">
              Jobs
            </span>
            <div className="flex-1" />
            <select
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              className={selectCls}
            >
              <option value="">all statuses</option>
              {STATUSES.map((s) => (
                <option key={s} value={s}>
                  {s}
                </option>
              ))}
            </select>
            <select
              value={typeFilter}
              onChange={(e) => setTypeFilter(e.target.value)}
              className={selectCls}
            >
              <option value="">all types</option>
              {JOB_TYPES.map((t) => (
                <option key={t} value={t}>
                  {t}
                </option>
              ))}
            </select>
            <select
              value={sortOrder}
              onChange={(e) => setSortOrder(e.target.value)}
              className={selectCls}
            >
              <option value="newest">newest first</option>
              <option value="oldest">oldest first</option>
            </select>
          </div>
          <JobTable jobs={sortedJobs} onCancel={handleCancel} />
        </div>

        <div className="lg:col-span-1">
          <QueueDepthChart data={history} />
        </div>
      </div>
    </div>
  )
}
