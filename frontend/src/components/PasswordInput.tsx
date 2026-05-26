import { useState, type InputHTMLAttributes } from 'react'

type Props = Omit<InputHTMLAttributes<HTMLInputElement>, 'type'>

export function PasswordInput(props: Props) {
  const [show, setShow] = useState(false)
  return (
    <div className="relative mt-1">
      <input
        {...props}
        type={show ? 'text' : 'password'}
        className="block w-full rounded-card border border-border-soft bg-card px-3 py-2 pr-14 text-sm shadow-sm focus:border-coral focus:outline-none focus:ring-2 focus:ring-coral/20"
      />
      <button
        type="button"
        onClick={() => setShow(s => !s)}
        className="absolute right-2 top-1/2 -translate-y-1/2 text-xs font-semibold text-muted hover:text-plum transition-colors"
      >
        {show ? 'Hide' : 'Show'}
      </button>
    </div>
  )
}
