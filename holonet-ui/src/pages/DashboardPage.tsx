import { FormEvent, useEffect, useState } from 'react'
import { createWallet, deposit, getWallets, Wallet } from '../api/client'
import { useAuthStore } from '../store/authStore'

export default function DashboardPage() {
  const userId = useAuthStore((s) => s.userId)!
  const [wallets, setWallets] = useState<Wallet[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  // New wallet form
  const [newCurrency, setNewCurrency] = useState('USD')

  // Deposit form
  const [depositWalletId, setDepositWalletId] = useState('')
  const [depositAmount, setDepositAmount] = useState('')

  async function refresh() {
    setLoading(true)
    try {
      const w = await getWallets(userId)
      setWallets(w)
      if (w.length > 0 && !depositWalletId) setDepositWalletId(w[0].id)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load wallets')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    refresh()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  async function handleCreateWallet(e: FormEvent) {
    e.preventDefault()
    setError(null)
    try {
      await createWallet(userId, newCurrency.toUpperCase())
      await refresh()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to create wallet')
    }
  }

  async function handleDeposit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    try {
      await deposit(depositWalletId, Number(depositAmount), 'dashboard faucet')
      setDepositAmount('')
      await refresh()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Deposit failed')
    }
  }

  return (
    <div className="page">
      <div className="panel">
        <h2>Wallets</h2>
        {loading ? (
          <p style={{ color: 'var(--muted)' }}>Loading…</p>
        ) : wallets.length === 0 ? (
          <p style={{ color: 'var(--muted)' }}>No wallets yet — create one below.</p>
        ) : (
          wallets.map((w) => (
            <div className="wallet-card" key={w.id}>
              <span>{w.currency}</span>
              <strong>{w.balance.toLocaleString(undefined, { maximumFractionDigits: 8 })}</strong>
            </div>
          ))
        )}
      </div>

      <div className="panel">
        <h2>Create wallet</h2>
        <form onSubmit={handleCreateWallet} className="form-row">
          <label>
            Currency
            <input value={newCurrency} onChange={(e) => setNewCurrency(e.target.value)} placeholder="USD" />
          </label>
          <button type="submit" className="primary" style={{ alignSelf: 'flex-end' }}>
            Create
          </button>
        </form>
      </div>

      <div className="panel">
        <h2>Deposit (faucet)</h2>
        <form onSubmit={handleDeposit} className="form-row">
          <label>
            Wallet
            <select value={depositWalletId} onChange={(e) => setDepositWalletId(e.target.value)}>
              {wallets.map((w) => (
                <option key={w.id} value={w.id}>
                  {w.currency} — {w.id.slice(0, 8)}
                </option>
              ))}
            </select>
          </label>
          <label>
            Amount
            <input
              type="number"
              min="0"
              step="any"
              value={depositAmount}
              onChange={(e) => setDepositAmount(e.target.value)}
              required
            />
          </label>
          <button type="submit" className="primary" style={{ alignSelf: 'flex-end' }} disabled={!depositWalletId}>
            Deposit
          </button>
        </form>
        {error && <div className="error">{error}</div>}
      </div>
    </div>
  )
}
