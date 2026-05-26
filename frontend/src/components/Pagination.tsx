interface Props {
  page: number
  totalPages: number
  onPageChange: (page: number) => void
}

export function Pagination({ page, totalPages, onPageChange }: Props) {
  if (totalPages <= 1) return null

  const window = getPageWindow(page, totalPages)
  const btnBase =
    'rounded-full border border-border-soft bg-card text-sm font-medium text-plum hover:bg-surface disabled:opacity-30 transition-colors'

  return (
    <nav className="flex flex-wrap justify-center items-center gap-2 mt-8" aria-label="Pagination">
      <button
        onClick={() => onPageChange(page - 1)}
        disabled={page === 0}
        className={`${btnBase} px-4 py-1.5`}
      >
        Previous
      </button>

      {window.map((slot, i) =>
        slot === 'ellipsis' ? (
          <span key={`e${i}`} className="text-muted text-sm px-1 select-none" aria-hidden>
            …
          </span>
        ) : slot === page ? (
          <button
            key={slot}
            aria-current="page"
            disabled
            className="rounded-full bg-plum text-surface text-sm font-bold px-3 py-1.5 min-w-[36px] shadow-button"
          >
            {slot + 1}
          </button>
        ) : (
          <button
            key={slot}
            onClick={() => onPageChange(slot)}
            className={`${btnBase} px-3 py-1.5 min-w-[36px]`}
            aria-label={`Go to page ${slot + 1}`}
          >
            {slot + 1}
          </button>
        )
      )}

      <button
        onClick={() => onPageChange(page + 1)}
        disabled={page >= totalPages - 1}
        className={`${btnBase} px-4 py-1.5`}
      >
        Next
      </button>
    </nav>
  )
}

function getPageWindow(current: number, total: number): (number | 'ellipsis')[] {
  if (total <= 7) {
    return Array.from({ length: total }, (_, i) => i)
  }

  const last = total - 1
  const result: (number | 'ellipsis')[] = [0]
  const start = Math.max(1, current - 1)
  const end = Math.min(last - 1, current + 1)

  if (start > 1) result.push('ellipsis')
  for (let i = start; i <= end; i++) result.push(i)
  if (end < last - 1) result.push('ellipsis')

  result.push(last)
  return result
}
