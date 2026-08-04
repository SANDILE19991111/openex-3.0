import { FormEvent, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { login, register } from '../api/client'
import { useAuthStore } from '../store/authStore'

export default function LoginPage() {
  const [mode, setMode] = useState<'login' | 'register'>('login')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const authLogin = useAuthStore((s) => s.login)
  const navigate = useNavigate()

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setLoading(true)
    try {
      const fn = mode === 'login' ? login : register
      const res = await fn(username, password)
      authLogin(res.token, res.userId, res.username)
      navigate('/dashboard')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Something went wrong')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="page" style={{ maxWidth: 380 }}>
      <div className="panel">
        <h2>{mode === 'login' ? 'Log in' : 'Register'}</h2>
        <form onSubmit={handleSubmit}>
          <div className="form-row" style={{ flexDirection: 'column' }}>
            <label>
              Username
              <input
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                autoComplete="username"
                required
              />
            </label>
            <label>
              Password
              <input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
                minLength={8}
                required
              />
            </label>
          </div>
          <button type="submit" className="primary" disabled={loading} style={{ width: '100%' }}>
            {loading ? 'Please wait…' : mode === 'login' ? 'Log in' : 'Create account'}
          </button>
          {error && <div className="error">{error}</div>}
        </form>
        <p style={{ marginTop: '1rem', fontSize: '0.85rem' }}>
          {mode === 'login' ? "Don't have an account? " : 'Already registered? '}
          <a
            href="#"
            onClick={(e) => {
              e.preventDefault()
              setMode(mode === 'login' ? 'register' : 'login')
              setError(null)
            }}
          >
            {mode === 'login' ? 'Register' : 'Log in'}
          </a>
        </p>
      </div>
    </div>
  )
}
