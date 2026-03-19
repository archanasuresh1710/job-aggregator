import React, { useEffect, useState } from 'react'
import { getApplications, getAllApplications, uploadCsv, updateStatus, deleteApplication } from '../api/applications'
import AddApplicationModal from './AddApplicationModal'

const STATUS_STYLES = {
  'Applied':        { bg: '#e8f0fe', color: '#1a56db', border: '#c3d4fb' },
  'Interview Round':{ bg: '#fef3c7', color: '#b45309', border: '#fde68a' },
  'Rejected':       { bg: '#fde8e8', color: '#c81e1e', border: '#fbd5d5' },
  'No Callback':    { bg: '#f3f4f6', color: '#6b7280', border: '#e5e7eb' },
}

const ALL_STATUSES = ['Applied', 'Interview Round', 'Rejected', 'No Callback']
const FILTERS = ['All', ...ALL_STATUSES]

export default function ApplicationsTab() {
  const [applications, setApplications] = useState([])
  const [counts, setCounts] = useState({})
  const [filter, setFilter] = useState('All')
  const [company, setCompany] = useState('')
  const [sort, setSort] = useState('desc')
  const [loading, setLoading] = useState(false)
  const [uploadMsg, setUploadMsg] = useState(null)
  const [showModal, setShowModal] = useState(false)
  const [editingId, setEditingId] = useState(null)
  const [editStatus, setEditStatus] = useState('')
  const [editInterview, setEditInterview] = useState('')
  const [editRemarks, setEditRemarks] = useState('')

  const loadCounts = async () => {
    const all = await getAllApplications()
    setCounts(ALL_STATUSES.reduce((acc, s) => {
      acc[s] = all.filter(a => a.status === s).length
      return acc
    }, {}))
  }

  const load = async () => {
    setLoading(true)
    try {
      const data = await getApplications(
        filter === 'All' ? null : filter,
        company || null,
        sort
      )
      setApplications(data)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { loadCounts() }, [])

  useEffect(() => {
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
    setEditStatus(app.status)
    setEditInterview(app.interview || '')
    setEditRemarks(app.remarks || '')
  }

  const saveEdit = async (id) => {
    const updated = await updateStatus(id, { status: editStatus, interview: editInterview, remarks: editRemarks })
    setApplications(prev => prev.map(a => a.id === id ? updated : a))
    setEditingId(null)
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
      </div>

      {loading && <p className="status">Loading...</p>}
      {!loading && applications.length === 0 && (
        <p className="status">No applications. Add one or import a CSV.</p>
      )}

      {applications.length > 0 && (
        <div className="applications-table-wrap">
          <table className="applications-table">
            <thead>
              <tr>
                <th>#</th>
                <th>Company</th>
                <th>Role</th>
                <th>Date</th>
                <th>Location</th>
                <th>Status</th>
                <th>Interview</th>
                <th>Via</th>
                <th>Remarks</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {applications.map((app, i) => {
                const style = STATUS_STYLES[app.status] || STATUS_STYLES['No Callback']
                const isEditing = editingId === app.id
                return (
                  <tr key={app.id}>
                    <td>{i + 1}</td>
                    <td className="td-company">
                      {app.statusCheckUrl
                        ? <a href={app.statusCheckUrl} target="_blank" rel="noopener noreferrer">{app.company}</a>
                        : app.company}
                    </td>
                    <td>{app.role || '—'}</td>
                    <td style={{ whiteSpace: 'nowrap' }}>{app.appliedDate || '—'}</td>
                    <td>{app.location || '—'}</td>
                    <td>
                      {isEditing ? (
                        <select value={editStatus} onChange={e => setEditStatus(e.target.value)}
                          className="inline-select">
                          {ALL_STATUSES.map(s => <option key={s}>{s}</option>)}
                        </select>
                      ) : (
                        <span className="status-badge" style={{
                          background: style.bg, color: style.color, border: `1px solid ${style.border}`
                        }}>
                          {app.status}
                        </span>
                      )}
                    </td>
                    <td>
                      {isEditing ? (
                        <select value={editInterview} onChange={e => setEditInterview(e.target.value)} className="inline-select">
                          <option value="">—</option>
                          <option>Yes</option>
                          <option>No</option>
                          <option>Coding Assessment</option>
                        </select>
                      ) : (app.interview || '—')}
                    </td>
                    <td>{app.modeOfApplication || '—'}</td>
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

      {showModal && (
        <AddApplicationModal
          onClose={() => setShowModal(false)}
          onSaved={() => load()}
        />
      )}
    </div>
  )
}
