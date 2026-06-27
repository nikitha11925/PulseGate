import { useState } from 'react'
import { Routes, Route, NavLink } from 'react-router-dom'
import Dashboard from './pages/Dashboard.jsx'
import DeadLetter from './pages/DeadLetter.jsx'
import SubmitJobModal from './components/SubmitJobModal.jsx'

function NavItem({ to, children }) {
  return (
    <NavLink
      to={to}
      end
      className={({ isActive }) =>
        `px-3 py-1.5 text-sm font-mono border ${
          isActive
            ? 'border-accent text-accent'
            : 'border-edge text-muted hover:text-primary hover:border-primary'
        }`
      }
    >
      {children}
    </NavLink>
  )
}

export default function App() {
  // Bump this to force the dashboard to reload its job list after a submit.
  const [refreshKey, setRefreshKey] = useState(0)
  const [submitOpen, setSubmitOpen] = useState(false)

  return (
    <div className="min-h-screen flex flex-col bg-bg">
      <header className="border-b border-edge px-4 py-3 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <span className="font-mono font-bold text-accent text-lg tracking-tight">
            ▮ PULSEGATE
          </span>
          <span className="text-muted text-xs font-mono hidden sm:inline">
            job processing engine
          </span>
        </div>
        <nav className="flex items-center gap-2">
          <NavItem to="/">DASHBOARD</NavItem>
          <NavItem to="/dead-letter">DEAD LETTER</NavItem>
          <button
            onClick={() => setSubmitOpen(true)}
            className="px-3 py-1.5 text-sm font-mono bg-accent text-bg font-semibold hover:opacity-90"
          >
            + SUBMIT JOB
          </button>
        </nav>
      </header>

      <main className="flex-1 p-4">
        <Routes>
          <Route path="/" element={<Dashboard refreshKey={refreshKey} />} />
          <Route path="/dead-letter" element={<DeadLetter />} />
        </Routes>
      </main>

      {submitOpen && (
        <SubmitJobModal
          onClose={() => setSubmitOpen(false)}
          onSubmitted={() => setRefreshKey((k) => k + 1)}
        />
      )}
    </div>
  )
}
