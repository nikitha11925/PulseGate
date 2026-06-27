import { useState } from 'react'
import { submitJob } from '../api.js'
import { JOB_TYPES } from '../theme.js'

const SAMPLES = {
  EMAIL: '{\n  "to": "user@example.com",\n  "subject": "Welcome"\n}',
  IMAGE_RESIZE: '{\n  "src": "s3://bucket/in.png",\n  "width": 200\n}',
  REPORT: '{\n  "reportId": "weekly-42"\n}',
  // A reachable test endpoint; use a bad URL to demo retries + dead-letter.
  WEBHOOK: '{\n  "url": "https://httpbin.org/post",\n  "body": { "hello": "world" }\n}',
}

export default function SubmitJobModal({ onClose, onSubmitted }) {
  const [type, setType] = useState('EMAIL')
  const [payload, setPayload] = useState(SAMPLES.EMAIL)
  const [priority, setPriority] = useState(5)
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  function changeType(t) {
    setType(t)
    setPayload(SAMPLES[t] || '{}')
  }

  async function handleSubmit(e) {
    e.preventDefault()
    setError(null)
    let parsed
    try {
      parsed = JSON.parse(payload)
    } catch {
      setError('Payload is not valid JSON')
      return
    }
    setBusy(true)
    try {
      await submitJob({ type, payload: parsed, priority: Number(priority) })
      onSubmitted?.()
      onClose()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  const fieldLabel = 'text-muted text-[11px] uppercase tracking-widest font-sans mb-1 block'
  const inputBase =
    'w-full bg-bg border border-edge px-2 py-1.5 text-primary font-mono text-sm focus:outline-none focus:border-accent'

  return (
    <div
      className="fixed inset-0 bg-black/70 flex items-center justify-center z-50 p-4"
      onClick={onClose}
    >
      <div
        className="bg-surface border border-edge w-full max-w-lg p-5"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex justify-between items-center mb-4">
          <h2 className="font-sans font-semibold text-primary">Submit Job</h2>
          <button className="text-muted hover:text-primary" onClick={onClose}>
            ✕
          </button>
        </div>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className={fieldLabel}>Type</label>
              <select
                value={type}
                onChange={(e) => changeType(e.target.value)}
                className={inputBase}
              >
                {JOB_TYPES.map((t) => (
                  <option key={t} value={t}>
                    {t}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className={fieldLabel}>Priority</label>
              <input
                type="number"
                min="1"
                max="9"
                value={priority}
                onChange={(e) => setPriority(e.target.value)}
                className={inputBase}
              />
            </div>
          </div>
          <div>
            <label className={fieldLabel}>Payload (JSON)</label>
            <textarea
              value={payload}
              onChange={(e) => setPayload(e.target.value)}
              rows={8}
              spellCheck={false}
              className={`${inputBase} resize-y`}
            />
          </div>
          {error && <div className="text-danger text-sm font-mono">{error}</div>}
          <div className="flex justify-end gap-2">
            <button
              type="button"
              onClick={onClose}
              className="px-3 py-1.5 text-sm font-mono border border-edge text-muted hover:text-primary"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={busy}
              className="px-3 py-1.5 text-sm font-mono bg-accent text-bg font-semibold disabled:opacity-50"
            >
              {busy ? 'Submitting…' : 'Submit'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
