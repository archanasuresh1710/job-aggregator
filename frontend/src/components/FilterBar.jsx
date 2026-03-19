import React from 'react'

export default function FilterBar({ keyword, source, onKeywordChange, onSourceChange, onIngest, hideSeen, onToggleSeen }) {
  return (
    <div className="filter-bar">
      <input
        type="text"
        placeholder="Search by title or company..."
        value={keyword}
        onChange={e => onKeywordChange(e.target.value)}
        className="filter-input"
      />
      <select value={source} onChange={e => onSourceChange(e.target.value)} className="filter-select">
        <option value="">All Sources</option>
        <option value="linkedin">LinkedIn</option>
        <option value="adzuna">Adzuna</option>
      </select>
      <button onClick={onToggleSeen} className={`btn-toggle-seen ${!hideSeen ? 'active' : ''}`}>
        {hideSeen ? 'Show Seen' : 'Hide Seen'}
      </button>
      <button onClick={onIngest} className="btn-ingest">
        Fetch Now
      </button>
    </div>
  )
}
