import { STATUS_COLORS } from '../theme.js'

export default function StatusBadge({ status }) {
  const color = STATUS_COLORS[status] || '#666666'
  return (
    <span
      className="font-mono text-xs px-2 py-0.5 border inline-block"
      style={{ color, borderColor: color }}
    >
      {status}
    </span>
  )
}
