import { FormEvent, useState } from 'react'
import { sendChatMessage } from '../api/client'
import { useAuthStore } from '../store/authStore'

interface ChatLine {
  from: 'user' | 'droid'
  text: string
}

export default function ChatWidget() {
  const { token, userId } = useAuthStore()
  const [open, setOpen] = useState(false)
  const [input, setInput] = useState('')
  const [lines, setLines] = useState<ChatLine[]>([
    { from: 'droid', text: 'Astromech online. Ask me about your wallet balances.' }
  ])
  const [sending, setSending] = useState(false)

  if (!token || !userId) return null // only show once logged in - the tool needs a real JWT

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    const message = input.trim()
    if (!message || sending) return

    setLines((prev) => [...prev, { from: 'user', text: message }])
    setInput('')
    setSending(true)
    try {
      const { reply } = await sendChatMessage(message, userId!)
      setLines((prev) => [...prev, { from: 'droid', text: reply }])
    } catch (err) {
      setLines((prev) => [
        ...prev,
        {
          from: 'droid',
          text:
            err instanceof Error
              ? `Error: ${err.message}`
              : 'Something went wrong reaching the assistant.'
        }
      ])
    } finally {
      setSending(false)
    }
  }

  return (
    <div style={{ position: 'fixed', bottom: '1.5rem', right: '1.5rem', zIndex: 50 }}>
      {open && (
        <div
          className="panel"
          style={{
            width: 320,
            height: 420,
            display: 'flex',
            flexDirection: 'column',
            marginBottom: '0.75rem',
            padding: 0,
            overflow: 'hidden'
          }}
        >
          <div
            style={{
              padding: '0.75rem 1rem',
              borderBottom: '1px solid var(--border)',
              fontWeight: 600
            }}
          >
            Astromech
          </div>
          <div style={{ flex: 1, overflowY: 'auto', padding: '0.75rem 1rem' }}>
            {lines.map((line, i) => (
              <div
                key={i}
                style={{
                  marginBottom: '0.6rem',
                  textAlign: line.from === 'user' ? 'right' : 'left'
                }}
              >
                <span
                  style={{
                    display: 'inline-block',
                    padding: '0.4rem 0.65rem',
                    borderRadius: 8,
                    fontSize: '0.85rem',
                    maxWidth: '85%',
                    background: line.from === 'user' ? 'var(--accent)' : 'var(--bg)',
                    color: line.from === 'user' ? 'white' : 'var(--text)',
                    border: line.from === 'user' ? 'none' : '1px solid var(--border)'
                  }}
                >
                  {line.text}
                </span>
              </div>
            ))}
            {sending && <p style={{ color: 'var(--muted)', fontSize: '0.85rem' }}>Thinking…</p>}
          </div>
          <form
            onSubmit={handleSubmit}
            style={{ display: 'flex', gap: '0.5rem', padding: '0.75rem', borderTop: '1px solid var(--border)' }}
          >
            <input
              value={input}
              onChange={(e) => setInput(e.target.value)}
              placeholder="Ask about your balance…"
              style={{ flex: 1 }}
              disabled={sending}
            />
            <button type="submit" className="primary" disabled={sending}>
              Send
            </button>
          </form>
        </div>
      )}
      <button
        className="primary"
        onClick={() => setOpen((o) => !o)}
        style={{ borderRadius: '999px', width: 56, height: 56, fontSize: '1.4rem' }}
      >
        {open ? '×' : '🤖'}
      </button>
    </div>
  )
}
