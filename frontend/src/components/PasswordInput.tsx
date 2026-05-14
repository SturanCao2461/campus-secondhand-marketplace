import { useState, type InputHTMLAttributes } from 'react'

type Props = Omit<InputHTMLAttributes<HTMLInputElement>, 'type'>

export function PasswordInput(props: Props) {
  const [show, setShow] = useState(false)
  return (
    <div className="relative mt-1">
      <input
        {...props}
        type={show ? 'text' : 'password'}
        className="block w-full rounded-md border border-slate-300 px-3 py-2 pr-14 text-sm shadow-sm focus:border-slate-900 focus:outline-none"
      />
      <button
        type="button"
        onClick={() => setShow(s => !s)}
        className="absolute right-2 top-1/2 -translate-y-1/2 text-xs font-medium text-slate-600 hover:text-slate-900"
      >
        {show ? 'Hide' : 'Show'}
      </button>
    </div>
  )
}
