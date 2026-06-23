import React, { useEffect, useState, useMemo } from 'react'
import { getApplications, getAllApplications, uploadCsv, updateStatus, deleteApplication, bulkUpdateStatus } from '../api/applications'
import AddApplicationModal from './AddApplicationModal'
import ColumnFilter from './ColumnFilter'
import { useProfile } from '../context/ProfileContext'

const STATUS_STYLES = {
  'Awaiting':           { bg: '#e8f0fe', color: '#1a56db', border: '#c3d4fb' },
  'Interview Round':    { bg: '#fef3c7', color: '#b45309', border: '#fde68a' },
  'Coding Assessment':  { bg: '#f3e8ff', color: '#7c3aed', border: '#a78bfa' },
  'Rejected':           { bg: '#fde8e8', color: '#c81e1e', border: '#fbd5d5' },
  'No Callback':        { bg: '#f3f4f6', color: '#6b7280', border: '#e5e7eb' },
}

const ALL_STATUSES = ['Awaiting', 'Interview Round', 'Coding Assessment', 'Rejected', 'No Callback']
const FILTERS = ['All', ...ALL_STATUSES]

export default function ApplicationsTab() {
  const { profile } = useProfile()
  const resumeLabels = useMemo(() =>
    (profile?.resumeLabels || '').split('\n').map(s => s.trim()).filter(Boolean)
  , [profile?.resumeLabels])

  const [applications, setApplications] = useState([])
  const [counts, setCounts] = useState({})
  const [filter, setFilter] = useState('All')
  const [company, setCompany] = useState('')
  const [sort, setSort] = useState('desc')
  const [loading, setLoading] = useState(false)
  const [uploadMsg, setUploadMsg] = useState(null)
  const [showModal, setShowModal] = useState(false)
  const [interviewFilter, setInterviewFilter] = useState([])
  const [locationFilter, setLocationFilter] = useState([])
  const [statusLinksOnly, setStatusLinksOnly] = useState(false)
  const [page, setPage] = useState(1)
  const PAGE_SIZE = 20
  const [selectedIds, setSelectedIds] = useState(new Set())
  const [editingId, setEditingId] = useState(null)
  const [editCompany, setEditCompany] = useState('')
  const [editStatus, setEditStatus] = useState('')
  const [editDate, setEditDate] = useState('')
  const [editInterview, setEditInterview] = useState('')
  const [editRemarks, setEditRemarks] = useState('')
  const [editResumeLabel, setEditResumeLabel] = useState('')
  const [editStatusUrl, setEditStatusUrl] = useState('')
  const loadCounts = async () => {
    const all = await getAllApplications()
    setCounts(ALL_STATUSES.reduce((acc, s) => {
      acc[s] = all.filter(a => a.status === s).length
      return acc
    }, {}))
  }

  const interviewOptions = useMemo(() =>
    [...new Set(applications.map(a => a.interview || '').filter(Boolean))].sort()
  , [applications])

  const locationOptions = useMemo(() =>
    [...new Set(applications.map(a => (a.location || '').trim()).filter(Boolean))].sort()
  , [applications])

  const visibleApplications = useMemo(() => applications.filter(a => {
    if (statusLinksOnly && !a.statusCheckUrl) return false
    if (interviewFilter.length > 0 && !interviewFilter.includes(a.interview || '')) return false
    if (locationFilter.length > 0 && !locationFilter.includes((a.location || '').trim())) return false
    return true
  }), [applications, interviewFilter, locationFilter, statusLinksOnly])

  const totalPages = Math.ceil(visibleApplications.length / PAGE_SIZE)

  const paginatedApplications = useMemo(() =>
    visibleApplications.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE)
  , [visibleApplications, page])

  useEffect(() => { setPage(1); setSelectedIds(new Set()) }, [filter, company, sort, interviewFilter, locationFilter, statusLinksOnly])

  const load = async () => {
    setLoading(true)
    try {
      const statusParam = filter === 'All' ? null : filter
      const data = await getApplications(statusParam, company || null, sort)
      setApplications(data)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { loadCounts() }, [])

  useEffect(() => {
    setInterviewFilter([])
    setLocationFilter([])
    const delay = setTimeout(load, 300)
    return () => clearTimeout(delay)
  }, [filter, company, sort])

  const handleUpload = async (e) => {
    const file = e.target.files[0]
    if (!file) return
    try {
      const msg = await uploadCsv(file)
      setUploadMsg(msg)
      load(); loadCounts()
    } catch {
      setUploadMsg('Upload failed.')
    }
    e.target.value = ''
  }

  const startEdit = (app) => {
    setEditingId(app.id)
    setEditCompany(app.company || '')
    setEditStatus(app.status)
    setEditDate(app.appliedDate || '')
    setEditInterview(app.interview || '')
    setEditRemarks(app.remarks || '')
    setEditResumeLabel(app.resumeLabel || '')
    setEditStatusUrl(app.statusCheckUrl || '')
  }

  const saveEdit = async (id) => {
    const updated = await updateStatus(id, {
      company: editCompany, status: editStatus, appliedDate: editDate,
      interview: editInterview, remarks: editRemarks, resumeLabel: editResumeLabel,
      statusCheckUrl: editStatusUrl,
    })
    setApplications(prev => prev.map(a => a.id === id ? updated : a))
    setEditingId(null)
    loadCounts()
  }

  const toggleSelect = (id) => {
    setSelectedIds(prev => {
      const next = new Set(prev)
      next.has(id) ? next.delete(id) : next.add(id)
      return next
    })
  }

  const toggleSelectAll = () => {
    const pageIds = paginatedApplications.map(a => a.id)
    const allSelected = pageIds.every(id => selectedIds.has(id))
    setSelectedIds(prev => {
      const next = new Set(prev)
      allSelected ? pageIds.forEach(id => next.delete(id)) : pageIds.forEach(id => next.add(id))
      return next
    })
  }

  const handleBulkNoCallback = async () => {
    const ids = [...selectedIds]
    const updated = await bulkUpdateStatus(ids, 'No Callback')
    setApplications(prev => prev.map(a => {
      const u = updated.find(u => u.id === a.id)
      return u || a
    }))
    setSelectedIds(new Set())
    loadCounts()
  }

  const handleDelete = async (id) => {
    if (!confirm('Delete this application?')) return
    await deleteApplication(id)
    setApplications(prev => prev.filter(a => a.id !== id))
    loadCounts()
  }

  return (
    <div className="applications-tab">
      <div className="applications-header">
        <div className="stat-cards">
          <div className="stat-card stat-card-total"
            onClick={() => setFilter('All')} role="button">
            <span className="stat-count">{Object.values(counts).reduce((a, b) => a + b, 0) || '—'}</span>
            <span className="stat-label">Total Applied</span>
          </div>
          {ALL_STATUSES.map(s => (
            <div key={s} className="stat-card" style={{ borderColor: STATUS_STYLES[s]?.border }}
              onClick={() => setFilter(s)} role="button">
              <span className="stat-count" style={{ color: STATUS_STYLES[s]?.color }}>{counts[s] ?? '—'}</span>
              <span className="stat-label">{s}</span>
            </div>
          ))}
        </div>

        <div className="applications-actions">
          <button className="btn-add" onClick={() => setShowModal(true)}>+ Add Application</button>
          <label className="btn-upload">
            Import CSV
            <input type="file" accept=".csv" onChange={handleUpload} hidden />
          </label>
        </div>
      </div>

      {uploadMsg && <p className="upload-msg">{uploadMsg}</p>}

      <div className="app-controls">
        <input
          className="filter-input"
          placeholder="Search by company..."
          value={company}
          onChange={e => setCompany(e.target.value)}
        />
        <button
          className="sort-btn"
          onClick={() => setSort(s => s === 'desc' ? 'asc' : 'desc')}
        >
          Date {sort === 'desc' ? '↓ Newest' : '↑ Oldest'}
        </button>
      </div>

      <div className="filter-bar" style={{ marginBottom: 16 }}>
        {FILTERS.map(s => (
          <button key={s} onClick={() => setFilter(s)}
            className={`filter-pill ${filter === s ? 'active' : ''}`}>
            {s}
          </button>
        ))}
        <button
          className={`filter-pill ${statusLinksOnly ? 'active' : ''}`}
          onClick={() => setStatusLinksOnly(v => !v)}
          title="Show only applications with a status check link"
        >
          Has Status Link
        </button>
      </div>

      {loading && <p className="status">Loading...</p>}
      {!loading && visibleApplications.length === 0 && (
        <p className="status">No applications. Add one or import a CSV.</p>
      )}

      {selectedIds.size > 0 && (
        <div className="bulk-action-bar">
          <span>{selectedIds.size} selected</span>
          <button className="btn-bulk-no-callback" onClick={handleBulkNoCallback}>
            Mark as No Callback
          </button>
          <button className="btn-bulk-clear" onClick={() => setSelectedIds(new Set())}>Clear</button>
        </div>
      )}

      {visibleApplications.length > 0 && (
        <div className="applications-table-wrap">
          <table className="applications-table">
            <thead>
              <tr>
                <th className="th-check">
                  <input type="checkbox"
                    checked={paginatedApplications.length > 0 && paginatedApplications.every(a => selectedIds.has(a.id))}
                    onChange={toggleSelectAll}
                  />
                </th>
                <th>#</th>
                <th>Company</th>
                <th>Role</th>
                <th>Date</th>
                <th>
                  <ColumnFilter
                    label="Location"
                    options={locationOptions}
                    selected={locationFilter}
                    onChange={setLocationFilter}
                  />
                </th>
                <th>Status</th>
                {filter !== 'Coding Assessment' && filter !== 'Interview Round' && <th>
                  <ColumnFilter
                    label="Interview"
                    options={interviewOptions}
                    selected={interviewFilter}
                    onChange={setInterviewFilter}
                  />
                </th>}
                <th>Via</th>
                <th>Resume</th>
                <th>Remarks</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {paginatedApplications.map((app, i) => {
                const style = STATUS_STYLES[app.status] || STATUS_STYLES['No Callback']
                const isEditing = editingId === app.id
                return (
                  <tr key={app.id} className={selectedIds.has(app.id) ? 'row-selected' : ''}>
                    <td className="td-check">
                      <input type="checkbox"
                        checked={selectedIds.has(app.id)}
                        onChange={() => toggleSelect(app.id)}
                      />
                    </td>
                    <td>{(page - 1) * PAGE_SIZE + i + 1}</td>
                    <td className="td-company">
                      {isEditing ? (
                        <div className="td-company-edit">
                          <input value={editCompany} onChange={e => setEditCompany(e.target.value)}
                            className="inline-input" placeholder="company..." />
                          <input value={editStatusUrl} onChange={e => setEditStatusUrl(e.target.value)}
                            className="inline-input url-input" placeholder="Status URL..." />
                        </div>
                      ) : app.statusCheckUrl
                        ? <a href={app.statusCheckUrl} target="_blank" rel="noopener noreferrer">{app.company}</a>
                        : app.company}
                    </td>
                    <td>{app.role || '—'}</td>
                    <td style={{ whiteSpace: 'nowrap' }}>
                      {isEditing ? (
                        <input type="date" value={editDate} onChange={e => setEditDate(e.target.value)}
                          className="inline-input" />
                      ) : (app.appliedDate || '—')}
                    </td>
                    <td>{app.location || '—'}</td>
                    <td>
                      {isEditing ? (
                        <select value={editStatus} onChange={e => setEditStatus(e.target.value)}
                          className="inline-select">
                          {ALL_STATUSES.map(s => <option key={s} value={s}>{s}</option>)}
                        </select>
                      ) : (
                        <span className="status-badge" style={{
                          background: style.bg, color: style.color, border: `1px solid ${style.border}`
                        }}>
                          {app.status}
                        </span>
                      )}
                    </td>
                    {filter !== 'Coding Assessment' && filter !== 'Interview Round' && <td>
                      {isEditing ? (
                        <select value={editInterview} onChange={e => setEditInterview(e.target.value)} className="inline-select">
                          <option value="">—</option>
                          <option>Yes</option>
                          <option>No</option>
                          <option>Coding Assessment</option>
                        </select>
                      ) : (app.interview || '—')}
                    </td>}
                    <td>{app.modeOfApplication || '—'}</td>
                    <td>
                      {isEditing ? (
                        resumeLabels.length > 0 ? (
                          <select value={editResumeLabel} onChange={e => setEditResumeLabel(e.target.value)} className="inline-select">
                            <option value="">—</option>
                            {/* keep an existing label even if it's since been removed from Profile */}
                            {editResumeLabel && !resumeLabels.includes(editResumeLabel) && (
                              <option value={editResumeLabel}>{editResumeLabel}</option>
                            )}
                            {resumeLabels.map(l => <option key={l} value={l}>{l}</option>)}
                          </select>
                        ) : (
                          <input value={editResumeLabel} onChange={e => setEditResumeLabel(e.target.value)}
                            className="inline-input" placeholder="label..." />
                        )
                      ) : (app.resumeLabel || '—')}
                    </td>
                    <td className="td-remarks">
                      {isEditing ? (
                        <input value={editRemarks} onChange={e => setEditRemarks(e.target.value)}
                          className="inline-input" placeholder="remarks..." />
                      ) : (
                        app.remarks || '—'
                      )}
                    </td>
                    <td className="td-actions">
                      {isEditing ? (
                        <>
                          <button className="btn-action save" onClick={() => saveEdit(app.id)}>Save</button>
                          <button className="btn-action cancel" onClick={() => setEditingId(null)}>Cancel</button>
                        </>
                      ) : (
                        <>
                          <button className="btn-action edit" onClick={() => startEdit(app)}>Edit</button>
                          <button className="btn-action delete" onClick={() => handleDelete(app.id)}>✕</button>
                        </>
                      )}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}

      {totalPages > 1 && (
        <div className="pagination">
          <button className="page-btn" onClick={() => setPage(p => p - 1)} disabled={page === 1}>← Prev</button>
          <span className="page-info">
            Page {page} of {totalPages} &nbsp;·&nbsp; {visibleApplications.length} jobs
          </span>
          <button className="page-btn" onClick={() => setPage(p => p + 1)} disabled={page === totalPages}>Next →</button>
        </div>
      )}

      {showModal && (
        <AddApplicationModal
          onClose={() => setShowModal(false)}
          onSaved={() => load()}
        />
      )}
    </div>
  )
}
