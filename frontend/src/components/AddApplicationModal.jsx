import React, { useState } from 'react'
import { addApplication } from '../api/applications'
import { useProfile } from '../context/ProfileContext'

const STATUSES = ['Awaiting', 'Interview Round', 'Rejected', 'No Callback']
const MODES = ['LinkedIn', 'Naukri', 'Foundit', 'Wellfound', 'Referral', 'Career Page', 'Other']


const todayLocal = () => new Date().toLocaleDateString('en-CA')

const empty = {
  company: '', role: '', appliedDate: '', location: '',
  status: 'Awaiting', interview: '', remarks: '', modeOfApplication: 'LinkedIn',
  statusCheckUrl: '', resumeLabel: ''
}

export default function AddApplicationModal({ onClose, onSaved, initialData = {} }) {
  const { profile } = useProfile()
  const resumeLabels = (profile?.resumeLabels || '')
    .split('\n')
    .map(s => s.trim())
    .filter(Boolean)

  const [form, setForm] = useState({ ...empty, appliedDate: todayLocal(), ...initialData })
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState(null)

  const set = (field, value) => setForm(f => ({ ...f, [field]: value }))

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (!form.company.trim()) { setError('Company is required.'); return }
    setSaving(true)
    try {
      await addApplication(form)
      onSaved()
      onClose()
    } catch {
      setError('Failed to save. Try again.')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal" onClick={e => e.stopPropagation()}>
        <div className="modal-header">
          <h2>Add Application</h2>
          <button className="modal-close" onClick={onClose}>✕</button>
        </div>

        <form onSubmit={handleSubmit} className="modal-form">
          <div className="form-row">
            <label>Company *</label>
            <input value={form.company} onChange={e => set('company', e.target.value)}
              placeholder="e.g. PhonePe" />
          </div>
          <div className="form-row">
            <label>Role</label>
            <input value={form.role} onChange={e => set('role', e.target.value)}
              placeholder="e.g. Senior Software Engineer" />
          </div>
          <div className="form-row two-col">
            <div>
              <label>Date Applied</label>
              <input type="date" value={form.appliedDate} onChange={e => set('appliedDate', e.target.value)} />
            </div>
            <div>
              <label>Location</label>
              <input value={form.location} onChange={e => set('location', e.target.value)}
                placeholder="e.g. Bangalore" />
            </div>
          </div>
          <div className="form-row two-col">
            <div>
              <label>Status</label>
              <select value={form.status} onChange={e => set('status', e.target.value)}>
                {STATUSES.map(s => <option key={s}>{s}</option>)}
              </select>
            </div>
            <div>
              <label>Mode of Application</label>
              <select value={form.modeOfApplication} onChange={e => set('modeOfApplication', e.target.value)}>
                {MODES.map(m => <option key={m}>{m}</option>)}
              </select>
            </div>
          </div>
          <div className="form-row">
            <label>Interview</label>
            <select value={form.interview || ''} onChange={e => set('interview', e.target.value)}>
              <option value="">—</option>
              <option>Yes</option>
              <option>No</option>
              <option>Coding Assessment</option>
            </select>
          </div>
          <div className="form-row">
            <label>Application Status Check URL <span style={{color:'#9ca3af', fontWeight:400}}>(optional)</span></label>
            <input value={form.statusCheckUrl} onChange={e => set('statusCheckUrl', e.target.value)}
              placeholder="e.g. company portal link to track your application" />
          </div>
          <div className="form-row">
            <label>Resume Used <span style={{color:'#9ca3af', fontWeight:400}}>(optional)</span></label>
            {resumeLabels.length > 0 ? (
              <select value={form.resumeLabel || ''} onChange={e => set('resumeLabel', e.target.value)}>
                <option value="">—</option>
                {resumeLabels.map(l => <option key={l} value={l}>{l}</option>)}
              </select>
            ) : (
              <span style={{ fontSize: 13, color: '#6b7280' }}>
                Add resume version labels in My Details to track which one you sent.
              </span>
            )}
          </div>
          <div className="form-row">
            <label>Remarks</label>
            <textarea value={form.remarks} onChange={e => set('remarks', e.target.value)}
              placeholder="Any notes..." rows={2} />
          </div>

          {error && <p className="form-error">{error}</p>}

          <div className="modal-actions">
            <button type="button" className="btn-cancel" onClick={onClose}>Cancel</button>
            <button type="submit" className="btn-save" disabled={saving}>
              {saving ? 'Saving...' : 'Save'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
