import React, { useState } from 'react'
import { useProfile } from '../context/ProfileContext'
import { saveProfile, uploadResume } from '../api/profile'

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
  const [uploading, setUploading] = useState(false)
  const [uploadError, setUploadError] = useState(null)
  const [resumeEditing, setResumeEditing] = useState(false)
  const [resumeDraft, setResumeDraft] = useState({})
  const [resumeSaving, setResumeSaving] = useState(false)

  const SENIORITY_OPTIONS = ['Junior', 'Mid-level', 'Senior', 'Lead', 'Staff', 'Principal']

  const startResumeEdit = () => {
    setResumeDraft({
      resumeYearsOfExperience: profile.resumeYearsOfExperience ?? '',
      resumeSeniority: profile.resumeSeniority ?? '',
      resumeStack: profile.resumeStack ?? '',
      resumeSummary: profile.resumeSummary ?? '',
      resumeSkills: profile.resumeSkills ?? '',
    })
    setResumeEditing(true)
  }

  const cancelResumeEdit = () => setResumeEditing(false)

  const saveResumeEdits = async () => {
    setResumeSaving(true)
    try {
      const merged = {
        ...profile,
        ...resumeDraft,
        resumeYearsOfExperience:
          resumeDraft.resumeYearsOfExperience === '' || resumeDraft.resumeYearsOfExperience == null
            ? null
            : Number(resumeDraft.resumeYearsOfExperience),
      }
      const updated = await saveProfile(merged)
      setProfile(updated)
      setResumeEditing(false)
    } finally {
      setResumeSaving(false)
    }
  }

  const handleResumeUpload = async (e) => {
    const file = e.target.files[0]
    if (!file) return
    setUploading(true)
    setUploadError(null)
    try {
      const updated = await uploadResume(file)
      setProfile(updated)
    } catch (err) {
      const msg = err?.response?.data || err?.message || 'Upload failed.'
      setUploadError(typeof msg === 'string' ? msg : 'Upload failed.')
    } finally {
      setUploading(false)
      e.target.value = ''
    }
  }

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

      <div className="resume-section">
        <div className="resume-header">
          <h3>Resume Analysis</h3>
          <div style={{ display: 'flex', gap: 8 }}>
            {profile.resumeFilename && !resumeEditing && (
              <button className="btn-add" onClick={startResumeEdit}>Edit</button>
            )}
            <label className={`btn-upload ${uploading ? 'disabled' : ''}`}>
              {uploading ? 'Analyzing...' : (profile.resumeFilename ? 'Replace Resume' : 'Upload Resume')}
              <input
                type="file"
                accept=".pdf,.docx,.doc"
                onChange={handleResumeUpload}
                disabled={uploading}
                hidden
              />
            </label>
          </div>
        </div>
        <p className="my-details-hint">
          Upload a PDF or DOCX. Claude will extract your skills + experience and use them to score every job in the feed. You can edit the parsed values below if anything looks off.
        </p>

        {uploadError && <p className="form-error">{uploadError}</p>}

        {profile.resumeFilename ? (
          <div className="resume-summary-card">
            <div className="resume-summary-meta">
              <span className="resume-filename">{profile.resumeFilename}</span>
              {profile.resumeUploadedAt && (
                <span className="resume-uploaded-at">
                  uploaded {new Date(profile.resumeUploadedAt).toLocaleDateString()}
                </span>
              )}
            </div>

            {/* Summary */}
            {resumeEditing ? (
              <div className="resume-edit-row">
                <span className="resume-fact-label">Summary</span>
                <textarea
                  className="inline-input detail-input"
                  rows={3}
                  value={resumeDraft.resumeSummary || ''}
                  onChange={e => setResumeDraft(d => ({ ...d, resumeSummary: e.target.value }))}
                />
              </div>
            ) : (
              profile.resumeSummary && <p className="resume-summary-text">{profile.resumeSummary}</p>
            )}

            <div className="resume-fact-row">
              {/* Experience */}
              <div className="resume-fact">
                <span className="resume-fact-label">Experience</span>
                {resumeEditing ? (
                  <input
                    className="inline-input"
                    type="number"
                    min="0"
                    step="1"
                    style={{ width: 80 }}
                    value={resumeDraft.resumeYearsOfExperience ?? ''}
                    onChange={e => setResumeDraft(d => ({ ...d, resumeYearsOfExperience: e.target.value }))}
                  />
                ) : (
                  <span className="resume-fact-value">
                    {profile.resumeYearsOfExperience != null ? `${profile.resumeYearsOfExperience} yrs` : '—'}
                  </span>
                )}
              </div>

              {/* Seniority */}
              <div className="resume-fact">
                <span className="resume-fact-label">Seniority</span>
                {resumeEditing ? (
                  <select
                    className="inline-select"
                    value={resumeDraft.resumeSeniority || ''}
                    onChange={e => setResumeDraft(d => ({ ...d, resumeSeniority: e.target.value }))}
                  >
                    <option value="">—</option>
                    {SENIORITY_OPTIONS.map(s => <option key={s}>{s}</option>)}
                  </select>
                ) : (
                  <span className="resume-fact-value">{profile.resumeSeniority || '—'}</span>
                )}
              </div>

              {/* Stack */}
              <div className="resume-fact" style={{ flex: 1, minWidth: 200 }}>
                <span className="resume-fact-label">Stack</span>
                {resumeEditing ? (
                  <input
                    className="inline-input"
                    value={resumeDraft.resumeStack || ''}
                    onChange={e => setResumeDraft(d => ({ ...d, resumeStack: e.target.value }))}
                  />
                ) : (
                  <span className="resume-fact-value">{profile.resumeStack || '—'}</span>
                )}
              </div>
            </div>

            {/* Skills */}
            <div className="resume-skills">
              <span className="resume-fact-label">Skills</span>
              {resumeEditing ? (
                <input
                  className="inline-input"
                  style={{ width: '100%', marginTop: 4 }}
                  placeholder="comma-separated, e.g. Java, Spring Boot, Kafka"
                  value={resumeDraft.resumeSkills || ''}
                  onChange={e => setResumeDraft(d => ({ ...d, resumeSkills: e.target.value }))}
                />
              ) : (
                <div className="skills-list">
                  {(profile.resumeSkills || '').split(',').filter(Boolean).map(s => (
                    <span key={s} className="skill-tag">{s.trim()}</span>
                  ))}
                </div>
              )}
            </div>

            {resumeEditing && (
              <div className="detail-actions">
                <button className="btn-action save" onClick={saveResumeEdits} disabled={resumeSaving}>
                  {resumeSaving ? 'Saving...' : 'Save'}
                </button>
                <button className="btn-action cancel" onClick={cancelResumeEdit}>Cancel</button>
              </div>
            )}
          </div>
        ) : (
          <p className="status">No resume uploaded yet.</p>
        )}
      </div>
    </div>
  )
}
