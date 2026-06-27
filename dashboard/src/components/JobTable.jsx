import StatusBadge from './StatusBadge.jsx'
import { fmtTime as fmt } from '../time.js'

const shortId = (id) => (id ? id.slice(0, 8) : '')

export default function JobTable({ jobs, onCancel }) {
  return (
    <div className="bg-surface border border-edge overflow-auto">
      <table className="w-full text-sm font-mono border-collapse">
        <thead>
          <tr className="text-muted text-left border-b border-edge text-[11px] uppercase tracking-wider">
            <th className="p-2 font-medium">ID</th>
            <th className="p-2 font-medium">Type</th>
            <th className="p-2 font-medium">Status</th>
            <th className="p-2 font-medium">Att.</th>
            <th className="p-2 font-medium">Created</th>
            <th className="p-2 font-medium">Updated</th>
            <th className="p-2 font-medium"></th>
          </tr>
        </thead>
        <tbody>
          {jobs.map((j) => (
            <tr key={j.id} className="border-b border-edge/50 hover:bg-black/40">
              <td className="p-2 text-muted" title={j.id}>
                {shortId(j.id)}
              </td>
              <td className="p-2 font-sans text-primary">{j.type}</td>
              <td className="p-2">
                <StatusBadge status={j.status} />
              </td>
              <td className="p-2 text-muted">
                {j.attempts}/{j.maxAttempts}
              </td>
              <td className="p-2 text-muted">{fmt(j.createdAt)}</td>
              <td className="p-2 text-muted">{fmt(j.updatedAt)}</td>
              <td className="p-2 text-right">
                {(j.status === 'PENDING' || j.status === 'PROCESSING') && (
                  <button
                    onClick={() => onCancel?.(j.id)}
                    className="text-danger hover:underline text-xs"
                  >
                    cancel
                  </button>
                )}
              </td>
            </tr>
          ))}
          {jobs.length === 0 && (
            <tr>
              <td colSpan={7} className="p-6 text-center text-muted">
                no jobs
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  )
}
