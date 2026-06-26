import React, { useState } from 'react'
import { useProfile } from '../context/ProfileContext'
import { saveProfile, uploadResume } from '../api/profile'

const FIELDS = [
  { key: 'name',         label: 'Full Name' },
  { key: 'email',        label: 'Email' },
  { key: 'phone',        label: 'Phone' },
  { key: 'address',      label: 'Address' },
  { key: 'linkedinUrl',  label: 'LinkedIn URL' },
  { key: 'githubUrl',    label: 'Github URL' }
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
  const [roleEditing, setRoleEditing] = useState(false)
  const [roleDraft, setRoleDraft] = useState('')
  const [roleSaving, setRoleSaving] = useState(false)
  const [newLabel, setNewLabel] = useState('')
  const [labelSaving, setLabelSaving] = useState(false)
  const [labelError, setLabelError] = useState(null)

  const resumeLabels = (profile.resumeLabels || '')
    .split('\n')
    .map(s => s.trim())
    .filter(Boolean)

  const persistLabels = async (labels) => {
    setLabelSaving(true)
    setLabelError(null)
    try {
      const updated = await saveProfile({ ...profile, resumeLabels: labels.join('\n') })
      setProfile(updated)
      return true
    } catch (err) {
      const msg = err?.response?.data?.message || err?.response?.statusText || err?.message || 'Failed to save.'
      setLabelError(typeof msg === 'string' ? msg : 'Failed to save.')
      return false
    } finally {
      setLabelSaving(false)
    }
  }

  const addLabel = async () => {
    const trimmed = newLabel.trim()
    if (!trimmed) return
    if (resumeLabels.includes(trimmed)) {
      setLabelError(`"${trimmed}" already exists.`)
      return
    }
    const ok = await persistLabels([trimmed, ...resumeLabels])
    if (ok) setNewLabel('')
  }

  const removeLabel = async (label) => {
    await persistLabels(resumeLabels.filter(l => l !== label))
  }

  const startRoleEdit = () => {
    setRoleDraft(profile.roleDescription || '')
    setRoleEditing(true)
  }

  const cancelRoleEdit = () => setRoleEditing(false)

  const saveRoleDescription = async () => {
    setRoleSaving(true)
    try {
      const updated = await saveProfile({ ...profile, roleDescription: roleDraft })
      setProfile(updated)
      setRoleEditing(false)
    } finally {
      setRoleSaving(false)
    }
  }

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

  const initials = (profile.name || '')
    .split(/\s+/).filter(Boolean).slice(0, 2)
    .map(w => w[0].toUpperCase()).join('') || '?'

  const headline = [profile.resumeSeniority, profile.resumeStack].filter(Boolean).join(' · ')

  return (
    <>
      <div className="profile-hero">
        <div className="profile-avatar">{initials}</div>
        <div className="profile-hero-main">
          <div className="profile-hero-name">{profile.name || 'Your Name'}</div>
          {headline && <div className="profile-hero-headline">{headline}</div>}
          <div className="profile-hero-facts">
            {profile.resumeYearsOfExperience != null && (
              <span className="profile-fact-chip"><strong>{profile.resumeYearsOfExperience} yrs</strong> experience</span>
            )}
            {profile.resumeSeniority && (
              <span className="profile-fact-chip"><strong>{profile.resumeSeniority}</strong></span>
            )}
            {profile.address && (
              <span className="profile-fact-chip">{profile.address}</span>
            )}
            {profile.email && (
              <span className="profile-fact-chip">{profile.email}</span>
            )}
            {resumeLabels.length > 0 && (
              <span className="profile-fact-chip">
                {resumeLabels.length} resume version{resumeLabels.length !== 1 ? 's' : ''}
              </span>
            )}
          </div>
        </div>
        <div className="profile-hero-actions">
          {!editing && (
            <button className="btn-add" onClick={startEdit}>Edit profile</button>
          )}
        </div>
      </div>

    <div className="my-details-split">
      <section className="details-pane">
        <div className="my-details-header">
          <h2>Contact</h2>
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

        <div className="role-description-block">
          <div className="role-description-header">
            <span className="detail-label">Role Description</span>
            {!roleEditing && (
              <div className="role-description-actions">
                <button
                  className={`btn-copy ${copied === 'roleDescription' ? 'copied' : ''}`}
                  onClick={() => handleCopy('roleDescription', profile.roleDescription)}
                  disabled={!profile.roleDescription}
                  title="Copy role description"
                >
                  {copied === 'roleDescription' ? 'Copied!' : 'Copy'}
                </button>
                <button className="btn-add" onClick={startRoleEdit}>
                  {profile.roleDescription ? 'Edit' : 'Add'}
                </button>
              </div>
            )}
          </div>

          {roleEditing ? (
            <>
              <textarea
                className="inline-input role-description-textarea"
                rows={8}
                placeholder="Paste a paragraph describing your current role / responsibilities — you'll be able to copy it from here while applying."
                value={roleDraft}
                onChange={e => setRoleDraft(e.target.value)}
              />
              <div className="detail-actions">
                <button className="btn-action save" onClick={saveRoleDescription} disabled={roleSaving}>
                  {roleSaving ? 'Saving...' : 'Save'}
                </button>
                <button className="btn-action cancel" onClick={cancelRoleEdit}>Cancel</button>
              </div>
            </>
          ) : (
            profile.roleDescription
              ? <p className="role-description-text">{profile.roleDescription}</p>
              : <p className="detail-empty">No role description saved yet.</p>
          )}
        </div>
      </section>

      <section className="details-pane">
        <div className="my-details-header">
          <h2>Resume Analysis</h2>
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

        <div className="resume-labels-block">
          <div className="my-details-header">
            <h3 className="resume-labels-title">Resume Versions</h3>
          </div>
          <p className="my-details-hint">
            Track which resume version you sent per application. Add a label here (e.g. <em>v3-fintech</em>,
            <em> platform-heavy</em>), then pick it from the dropdown when logging an application.
          </p>

          <div className="resume-labels-add">
            <input
              className="inline-input"
              value={newLabel}
              onChange={e => { setNewLabel(e.target.value); setLabelError(null) }}
              onKeyDown={e => { if (e.key === 'Enter') { e.preventDefault(); addLabel() } }}
              placeholder="Add a resume version label..."
              disabled={labelSaving}
            />
            <button
              type="button"
              className="btn-add"
              onClick={addLabel}
              disabled={labelSaving || !newLabel.trim()}
            >
              {labelSaving ? 'Saving...' : 'Add'}
            </button>
          </div>

          {labelError && <p className="form-error">{labelError}</p>}

          {resumeLabels.length === 0 ? (
            <p className="detail-empty">No resume versions saved yet.</p>
          ) : (
            <div className="resume-labels-list">
              {resumeLabels.map(label => (
                <span key={label} className="resume-label-chip">
                  {label}
                  <button
                    type="button"
                    className="resume-label-remove"
                    onClick={() => removeLabel(label)}
                    disabled={labelSaving}
                    title={`Remove "${label}"`}
                    aria-label={`Remove ${label}`}
                  >
                    ×
                  </button>
                </span>
              ))}
            </div>
          )}
        </div>
      </section>
    </div>
    </>
  )
}
