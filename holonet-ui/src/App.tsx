import { Navigate, NavLink, Route, Routes } from 'react-router-dom'
import { useAuthStore } from './store/authStore'
import LoginPage from './pages/LoginPage'
import DashboardPage from './pages/DashboardPage'
import TradingPage from './pages/TradingPage'
import MarketsPage from './pages/MarketsPage'
import ChatWidget from './components/ChatWidget'

function RequireAuth({ children }: { children: JSX.Element }) {
  const token = useAuthStore((s) => s.token)
  if (!token) return <Navigate to="/login" replace />
  return children
}

export default function App() {
  const { username, logout } = useAuthStore()

  return (
    <>
      <nav className="navbar">
        <span className="brand">OpenEx 3.0 · Holonet Terminal</span>
        <div className="navbar-links">
          <NavLink to="/markets">Markets</NavLink>
          <NavLink to="/dashboard">Dashboard</NavLink>
          <NavLink to="/trading/BTC-USD">Trading</NavLink>
          {username ? (
            <>
              <span style={{ color: 'var(--muted)' }}>{username}</span>
              <a href="#" onClick={(e) => { e.preventDefault(); logout() }}>Log out</a>
            </>
          ) : (
            <NavLink to="/login">Login</NavLink>
          )}
        </div>
      </nav>

      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/markets" element={<MarketsPage />} />
        <Route
          path="/dashboard"
          element={
            <RequireAuth>
              <DashboardPage />
            </RequireAuth>
          }
        />
        <Route
          path="/trading/:pair"
          element={
            <RequireAuth>
              <TradingPage />
            </RequireAuth>
          }
        />
        <Route path="*" element={<Navigate to="/markets" replace />} />
      </Routes>

      {/* Floating chat widget - renders on every page, but only shows itself once logged in */}
      <ChatWidget />
    </>
  )
}
