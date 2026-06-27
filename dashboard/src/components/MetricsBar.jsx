function Metric({ label, value, color }) {
  return (
    <div className="bg-surface border border-edge px-4 py-3 flex flex-col">
      <span className="text-muted text-[10px] uppercase tracking-widest font-sans">{label}</span>
      <span className="font-mono text-2xl leading-tight" style={{ color }}>
        {value}
      </span>
    </div>
  )
}

export default function MetricsBar({ stats }) {
  const s = stats || {}
  const v = (x) => (x === undefined || x === null ? '—' : x)
  return (
    <div className="grid grid-cols-2 sm:grid-cols-4 lg:grid-cols-7 gap-2">
      <Metric label="Queue Depth" value={v(s.queueDepth)} color="#E8E8E8" />
      <Metric label="Processing" value={v(s.processing)} color="#FFB800" />
      <Metric label="Active Workers" value={v(s.activeWorkers)} color="#00FF88" />
      <Metric label="Done" value={v(s.done)} color="#00FF88" />
      <Metric label="Failed" value={v(s.failed)} color="#FFB800" />
      <Metric label="Dead" value={v(s.dead)} color="#FF4444" />
      <Metric label="Processed" value={v(s.processedTotal)} color="#E8E8E8" />
    </div>
  )
}
