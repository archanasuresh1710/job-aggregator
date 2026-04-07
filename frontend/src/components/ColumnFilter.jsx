import React, { useState, useEffect, useRef } from 'react'

export default function ColumnFilter({ label, options, selected, onChange }) {
  const [open, setOpen] = useState(false)
  const ref = useRef(null)

  // Close on outside click
  useEffect(() => {
    const handler = (e) => { if (ref.current && !ref.current.contains(e.target)) setOpen(false) }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [])

  const toggle = (value) => {
    if (selected.includes(value)) {
      onChange(selected.filter(v => v !== value))
    } else {
      onChange([...selected, value])
    }
  }

  const selectAll = () => onChange([])
  const isFiltered = selected.length > 0

  return (
    <div className="col-filter" ref={ref}>
      <button
        className={`col-filter-btn ${isFiltered ? 'active' : ''}`}
        onClick={() => setOpen(o => !o)}
        title={isFiltered ? `Filtered: ${selected.join(', ')}` : `Filter ${label}`}
      >
        {label} {isFiltered ? '▼' : '⌄'}
      </button>

      {open && (
        <div className="col-filter-dropdown">
          <div className="col-filter-header">
            <span className="col-filter-title">Filter {label}</span>
            {isFiltered && (
              <button className="col-filter-clear" onClick={selectAll}>Clear</button>
            )}
          </div>
          <div className="col-filter-options">
            {options.map(opt => (
              <label key={opt} className="col-filter-option">
                <input
                  type="checkbox"
                  checked={selected.includes(opt)}
                  onChange={() => toggle(opt)}
                />
                <span>{opt || '—'}</span>
              </label>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}
