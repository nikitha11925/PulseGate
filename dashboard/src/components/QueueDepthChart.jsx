import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
} from 'recharts'

export default function QueueDepthChart({ data }) {
  return (
    <div className="bg-surface border border-edge p-3 h-full">
      <div className="text-muted text-[11px] uppercase tracking-widest font-sans mb-2">
        Queue Depth · live
      </div>
      <ResponsiveContainer width="100%" height={280}>
        <LineChart data={data} margin={{ top: 5, right: 12, left: -18, bottom: 0 }}>
          <CartesianGrid stroke="#2A2A2A" strokeDasharray="3 3" />
          <XAxis
            dataKey="t"
            stroke="#666666"
            tick={{ fontSize: 10, fontFamily: 'JetBrains Mono', fill: '#666666' }}
            minTickGap={24}
          />
          <YAxis
            stroke="#666666"
            tick={{ fontSize: 10, fontFamily: 'JetBrains Mono', fill: '#666666' }}
            allowDecimals={false}
          />
          <Tooltip
            contentStyle={{
              background: '#1A1A1A',
              border: '1px solid #2A2A2A',
              borderRadius: 0,
              color: '#E8E8E8',
              fontFamily: 'JetBrains Mono',
              fontSize: 12,
            }}
            labelStyle={{ color: '#666666' }}
          />
          <Legend wrapperStyle={{ fontFamily: 'Inter', fontSize: 11, color: '#666666' }} />
          <Line
            type="monotone"
            name="queued"
            dataKey="queueDepth"
            stroke="#00FF88"
            dot={false}
            strokeWidth={2}
            isAnimationActive={false}
          />
          <Line
            type="monotone"
            name="processing"
            dataKey="processing"
            stroke="#FFB800"
            dot={false}
            strokeWidth={2}
            isAnimationActive={false}
          />
        </LineChart>
      </ResponsiveContainer>
    </div>
  )
}
