import React, { useState } from 'react'
import { useProfile } from '../context/ProfileContext'
import { saveProfile } from '../api/profile'

const FIELDS = [
  { key: 'name',         label: 'Full Name' },
  { key: 'email',        label: 'Email' },
  { key: 'phone',        label: 'Phone' },
  { key: 'address',      label: 'Address' },
  { key: 'linkedinUrl',  label: 'LinkedIn URL' },
  { key: 'portfolioUrl', label: 'Portfolio URL' },
  { key: 'resumeUrl',    label: 'Resume URL' },
]

export default function MyDetailsTab() {
  const { profile, setProfile } = useProfile()
  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState({})
  const [copied, setCopied] = useState(null)
  const [saving, setSaving] = useState(false)

  const handleCopy = (key, value) => {
    if (!value) return
    navigator.clipboard.writeText(value)
    setCopied(key)
    setTimeout(() => setCopied(null), 1500)
  }

  const startEdit = () => {
    setDraft({ ...profile })
    setEditing(true)
  }

  const cancelEdit = () => setEditing(false)

  const handleSave = async () => {
    setSaving(true)
    try {
      const updated = await saveProfile(draft)
      setProfile(updated)
      setEditing(false)
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="my-details-tab">
      <div className="my-details-header">
        <h2>My Details</h2>
        {!editing && (
          <button className="btn-add" onClick={startEdit}>Edit</button>
        )}
      </div>
      <p className="my-details-hint">Quick-copy your details while filling job applications.</p>

      <div className="details-grid">
        {FIELDS.map(({ key, label }) => (
          <div key={key} className="detail-row">
            <span className="detail-label">{label}</span>
            {editing ? (
              <input
                className="inline-input detail-input"
                value={draft[key] || ''}
                onChange={e => setDraft(d => ({ ...d, [key]: e.target.value }))}
                placeholder={`Enter ${label.toLowerCase()}...`}
              />
            ) : (
              <span className="detail-value">{profile[key] || <span className="detail-empty">—</span>}</span>
            )}
            {!editing && (
              <button
                className={`btn-copy ${copied === key ? 'copied' : ''}`}
                onClick={() => handleCopy(key, profile[key])}
                disabled={!profile[key]}
                title={`Copy ${label}`}
              >
                {copied === key ? 'Copied!' : 'Copy'}
              </button>
            )}
          </div>
        ))}
      </div>

      {editing && (
        <div className="detail-actions">
          <button className="btn-action save" onClick={handleSave} disabled={saving}>
            {saving ? 'Saving...' : 'Save'}
          </button>
          <button className="btn-action cancel" onClick={cancelEdit}>Cancel</button>
        </div>
      )}
    </div>
  )
}
