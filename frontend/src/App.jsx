import React, { useEffect, useState, useCallback, useMemo } from 'react'
import FilterBar from './components/FilterBar'
import ApplicationsTab from './components/ApplicationsTab'
import AddApplicationModal from './components/AddApplicationModal'
import MyDetailsTab from './components/MyDetailsTab'
import InterviewQuestionsTab from './components/InterviewQuestionsTab'
import { ProfileProvider } from './context/ProfileContext'
import { getJobs, markSeen, toggleBookmark, triggerIngestion, rescoreJob } from './api/jobs'

function scoreTier(score) {
  if (score == null) return 'unscored'
  if (score >= 61) return 'good'
  if (score >= 26) return 'stretch'
  return 'pass'
}

function expBits(job) {
  const { experienceFit, yearsRequiredMin, experienceGapYears } = job
  if (!experienceFit || experienceFit === 'match') return { pill: '', cls: '' }
  if (experienceFit === 'unknown') return { pill: 'Exp. not stated', cls: 'exp-unknown' }
  if (experienceFit === 'underqualified') {
    if ((experienceGapYears ?? 0) >= 2) return { pill: `Needs ${yearsRequiredMin}+y`, cls: 'exp-hard-pass' }
    return { pill: 'Stretch +1y', cls: 'exp-stretch' }
  }
  if (experienceFit === 'overqualified') return { pill: 'Overqualified', cls: 'exp-over' }
  return { pill: '', cls: '' }
}

function fmtDate(d) {
  if (!d) return 'Unknown'
  return new Date(d).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' })
}

const sortByScore = (a, b) => {
  const aScore = a.matchScore ?? -Infinity
  const bScore = b.matchScore ?? -Infinity
  if (aScore !== bScore) return bScore - aScore
  return new Date(b.postedDate || 0) - new Date(a.postedDate || 0)
}

const sortByDate = (a, b) =>
  new Date(b.postedDate || 0) - new Date(a.postedDate || 0)

export default function App() {
  const [activeTab, setActiveTab] = useState('feed')
  const [jobs, setJobs] = useState([])
  const [keyword, setKeyword] = useState('')
  const [source, setSource] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)
  const [hideSeen, setHideSeen] = useState(true)
  const [feedView, setFeedView] = useState('bangalore')
  const [applyJob, setApplyJob] = useState(null)
  const [sortMode, setSortMode] = useState('score')
  const [selectedJobId, setSelectedJobId] = useState(null)
  const [rescoringId, setRescoringId] = useState(null)
  const [bannerDismissed, setBannerDismissed] = useState(
    () => typeof localStorage !== 'undefined' && localStorage.getItem('feedBannerDismissed') === '1'
  )

  const dismissBanner = () => {
    localStorage.setItem('feedBannerDismissed', '1')
    setBannerDismissed(true)
  }

  const effectiveDomain = feedView === 'fintech' ? 'fintech' : null
  const locationFilter =
    feedView === 'bangalore' ? 'bangalore' :
    feedView === 'kochi' ? 'kochi' : null

  const fetchJobs = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const data = await getJobs(keyword || null, source || null, effectiveDomain, hideSeen, locationFilter)
      setJobs(data)
    } catch {
      setError('Failed to load jobs. Is the backend running?')
    } finally {
      setLoading(false)
    }
  }, [keyword, source, effectiveDomain, hideSeen, locationFilter])

  useEffect(() => {
    if (activeTab !== 'feed') return
    const delay = setTimeout(fetchJobs, 400)
    return () => clearTimeout(delay)
  }, [fetchJobs, activeTab])

  const sortedJobs = useMemo(() => {
    const cmp = sortMode === 'date' ? sortByDate : sortByScore
    return [...jobs].sort(cmp)
  }, [jobs, sortMode])

  // Auto-select first job; re-select when current selection is removed
  useEffect(() => {
    if (sortedJobs.length === 0) { setSelectedJobId(null); return }
    if (selectedJobId && sortedJobs.find(j => j.id === selectedJobId)) return
    setSelectedJobId(sortedJobs[0].id)
  }, [sortedJobs])

  // Scroll selected row into view inside feed-list-pane
  useEffect(() => {
    if (!selectedJobId) return
    const el = document.querySelector(`[data-job-id="${selectedJobId}"]`)
    if (el) el.scrollIntoView({ block: 'nearest', behavior: 'smooth' })
  }, [selectedJobId])

  const handleSeen = async (id) => {
    await markSeen(id)
    if (hideSeen) {
      setJobs(prev => prev.filter(j => j.id !== id))
    } else {
      setJobs(prev => prev.map(j => j.id === id ? { ...j, isSeen: true } : j))
    }
  }

  const handleBookmark = async (id) => {
    const updated = await toggleBookmark(id)
    setJobs(prev => prev.map(j => j.id === id ? updated : j))
  }

  const handleRescore = async (id) => {
    setRescoringId(id)
    try {
      const updated = await rescoreJob(id)
      setJobs(prev => prev.map(j => j.id === id ? updated : j))
    } finally {
      setRescoringId(null)
    }
  }

  const handleIngest = async () => {
    await triggerIngestion()
    fetchJobs()
  }

  const switchTab = (tab) => {
    setActiveTab(tab)
    setKeyword('')
    setSource('')
    setSelectedJobId(null)
  }

  useEffect(() => {
    if (activeTab !== 'feed') return
    if (applyJob) return

    const handler = (e) => {
      const t = e.target
      if (!t) return
      if (t.tagName === 'INPUT' || t.tagName === 'TEXTAREA' || t.tagName === 'SELECT' || t.isContentEditable) return
      if (e.metaKey || e.ctrlKey || e.altKey) return

      const len = sortedJobs.length
      if (len === 0) return

      const idx = sortedJobs.findIndex(j => j.id === selectedJobId)
      const curIdx = idx < 0 ? 0 : idx
      const focused = sortedJobs[curIdx]

      switch (e.key) {
        case 'j':
          e.preventDefault()
          setSelectedJobId(sortedJobs[Math.min(curIdx + 1, len - 1)].id)
          break
        case 'k':
          e.preventDefault()
          setSelectedJobId(sortedJobs[Math.max(0, curIdx - 1)].id)
          break
        case 's':
          if (!focused || focused.isSeen) return
          e.preventDefault()
          handleSeen(focused.id)
          break
        case 'b':
          if (!focused) return
          e.preventDefault()
          handleBookmark(focused.id)
          break
        case 'a':
          if (!focused) return
          e.preventDefault()
          setApplyJob(focused)
          break
        case 'Escape':
          setSelectedJobId(null)
          break
        default:
      }
    }

    window.addEventListener('keydown', handler)
    return () => window.removeEventListener('keydown', handler)
  }, [activeTab, applyJob, sortedJobs, selectedJobId, hideSeen])

  const selectedJob = selectedJobId
    ? sortedJobs.find(j => j.id === selectedJobId) ?? null
    : (sortedJobs[0] ?? null)

  return (
    <ProfileProvider>
    <div className="app">
      <header className="app-header">
        <h1>Job Aggregator</h1>
        <div className="tabs">
          <button className={`tab ${activeTab === 'feed' ? 'active' : ''}`}
            onClick={() => switchTab('feed')}>
            Job Feed {activeTab === 'feed' && <span className="tab-count">{jobs.length}</span>}
          </button>
          <button className={`tab ${activeTab === 'applied' ? 'active' : ''}`}
            onClick={() => setActiveTab('applied')}>
            Applied Jobs
          </button>
          <button className={`tab ${activeTab === 'details' ? 'active' : ''}`}
            onClick={() => setActiveTab('details')}>
            My Details
          </button>
          <button className={`tab ${activeTab === 'interview' ? 'active' : ''}`}
            onClick={() => setActiveTab('interview')}>
            Interview Q&amp;A
          </button>
        </div>
      </header>

      {activeTab === 'feed' && (
        <>
          <FilterBar
            keyword={keyword}
            source={source}
            onKeywordChange={setKeyword}
            onSourceChange={setSource}
            onIngest={handleIngest}
            hideSeen={hideSeen}
            onToggleSeen={() => setHideSeen(h => !h)}
          />
          <div className="feed-controls">
            <div className="domain-tabs">
              <button className={`domain-tab ${feedView === 'bangalore' ? 'active' : ''}`}
                onClick={() => setFeedView('bangalore')}>Bangalore</button>
              <button className={`domain-tab ${feedView === 'kochi' ? 'active' : ''}`}
                onClick={() => setFeedView('kochi')}>Kochi</button>
              <button className={`domain-tab ${feedView === 'fintech' ? 'active' : ''}`}
                onClick={() => setFeedView('fintech')}>Fintech</button>
            </div>
            <div className="sort-control" role="group" aria-label="Sort jobs">
              <span className="sort-label">Sort</span>
              <button className={`sort-pill ${sortMode === 'score' ? 'active' : ''}`}
                onClick={() => setSortMode('score')}>Best match</button>
              <button className={`sort-pill ${sortMode === 'date' ? 'active' : ''}`}
                onClick={() => setSortMode('date')}>Newest</button>
            </div>
          </div>
          {!bannerDismissed && (
            <div className="info-banner" role="note">
              <span>
                Each job is scored 0–100 against the resume on your{' '}
                <button className="banner-link" onClick={() => switchTab('details')}>My Details</button>{' '}
                tab — green = strong match, amber = stretch, red = poor fit.
              </span>
              <button className="banner-close" onClick={dismissBanner} aria-label="Dismiss">×</button>
            </div>
          )}
          <p className="kbd-hint">
            <kbd>j</kbd>/<kbd>k</kbd> navigate · <kbd>s</kbd> seen · <kbd>b</kbd> bookmark · <kbd>a</kbd> apply
          </p>

          {loading && <p className="status">Loading...</p>}
          {error && <p className="status error">{error}</p>}
          {!loading && !error && sortedJobs.length === 0 && (
            <p className="status">No jobs found. Click "Fetch Now" to pull from sources.</p>
          )}

          {sortedJobs.length > 0 && (
            <div className="feed-split">
              <div className="feed-list-pane">
                <div className="feed-count">{sortedJobs.length} matches</div>
                {sortedJobs.map(job => {
                  const tier = scoreTier(job.matchScore)
                  const exp = expBits(job)
                  return (
                    <div
                      key={job.id}
                      data-job-id={job.id}
                      className={`job-row tier-${tier} ${job.isSeen ? 'seen' : ''} ${job.id === selectedJobId ? 'selected' : ''}`}
                      onClick={() => setSelectedJobId(job.id)}
                    >
                      <div className="job-row-top">
                        <div>
                          <div className="job-row-title">{job.title}</div>
                          <div className="job-row-company">{job.company}</div>
                        </div>
                        <span className={`score-chip score-${tier}`}>
                          {job.matchScore == null ? '—' : `${job.matchScore}%`}
                        </span>
                      </div>
                      <div className="job-row-meta">
                        <span>{job.location}</span>
                        <span>{fmtDate(job.postedDate)}</span>
                      </div>
                      <div className="job-row-foot">
                        <span className={`source-badge source-${job.source}`}>{job.source}</span>
                        {job.isBookmarked && <span className="bookmark-indicator">★ Saved</span>}
                        {exp.pill && <span className={`exp-pill ${exp.cls}`}>{exp.pill}</span>}
                      </div>
                    </div>
                  )
                })}
              </div>

              <div className="feed-detail-pane">
                {selectedJob ? (
                  <JobDetail
                    job={selectedJob}
                    onSeen={handleSeen}
                    onBookmark={handleBookmark}
                    onApply={setApplyJob}
                    onRescore={handleRescore}
                    rescoringId={rescoringId}
                  />
                ) : (
                  <div className="feed-empty-detail">Select a job to see details</div>
                )}
              </div>
            </div>
          )}
        </>
      )}

      {activeTab === 'applied' && <ApplicationsTab />}
      {activeTab === 'details' && <MyDetailsTab />}
      {activeTab === 'interview' && <InterviewQuestionsTab />}

      {applyJob && (
        <AddApplicationModal
          onClose={() => setApplyJob(null)}
          onSaved={() => {
            const jobId = applyJob.id
            markSeen(jobId)
            setJobs(prev => prev.filter(j => j.id !== jobId))
            setApplyJob(null)
          }}
          initialData={{
            company: applyJob.company || '',
            role: applyJob.title || '',
            location: applyJob.location || '',
            modeOfApplication: applyJob.source === 'linkedin' ? 'LinkedIn'
                             : applyJob.source === 'naukri'   ? 'Naukri'
                             : 'Other',
            appliedDate: new Date().toISOString().split('T')[0],
            status: 'Awaiting',
          }}
        />
      )}
    </div>
    </ProfileProvider>
  )
}

function JobDetail({ job, onSeen, onBookmark, onApply, onRescore, rescoringId }) {
  const tier = scoreTier(job.matchScore)
  const exp = expBits(job)
  const isScored = job.matchScore != null
  const matchedSkills = (job.matchedSkills || '').split(',').map(s => s.trim()).filter(Boolean)
  const allMissing = (job.missingSkills || '').split(',').map(s => s.trim()).filter(Boolean)
  const criticalMissing = (job.criticalMissingSkills || '').split(',').map(s => s.trim()).filter(Boolean)
  const criticalSet = new Set(criticalMissing.map(s => s.toLowerCase()))
  const niceToHaveMissing = allMissing.filter(s => !criticalSet.has(s.toLowerCase()))
  const fallbackSkills = (job.skills || '').split(',').map(s => s.trim()).filter(Boolean)
  const description = job.description || job.jobDescription || ''

  return (
    <div className="job-detail">
      <div className="job-detail-header">
        <div>
          <h2 className="job-detail-title">
            <a href={job.url} target="_blank" rel="noopener noreferrer">{job.title}</a>
          </h2>
          <div className="job-detail-sub">
            <span className={`source-badge source-${job.source}`}>{job.source}</span>
            <span>{job.company}</span>
            <span>{job.location}</span>
            <span>{fmtDate(job.postedDate)}</span>
            {exp.pill && <span className={`exp-pill ${exp.cls}`}>{exp.pill}</span>}
          </div>
        </div>
        {isScored ? (
          <span className={`score-badge score-lg score-${tier}`}>
            <span className="score-value">{job.matchScore}%</span>
            <span className="score-caption">resume match</span>
          </span>
        ) : (
          <span className="score-badge score-lg score-unscored">Not scored</span>
        )}
      </div>

      <div className="job-detail-cta">
        <button className="btn-apply-lg" onClick={() => onApply(job)}>Mark as Applied</button>
        <button className="btn-bookmark" onClick={() => onBookmark(job.id)}>
          {job.isBookmarked ? 'Unbookmark' : 'Bookmark'}
        </button>
        <button className="btn-seen" onClick={() => onSeen(job.id)} disabled={job.isSeen}>
          {job.isSeen ? 'Seen' : 'Mark Seen'}
        </button>
        <button
          className="btn-rescore"
          onClick={() => onRescore(job.id)}
          disabled={rescoringId === job.id}
        >
          {rescoringId === job.id ? 'Scoring...' : (isScored ? 'Rescore' : 'Score')}
        </button>
      </div>

      {isScored && job.matchRationale && (
        <div className="job-detail-section">
          <div className="job-detail-section-label">Why this score</div>
          <p className="match-rationale">{job.matchRationale}</p>
        </div>
      )}

      <div className="job-detail-section">
        <div className="job-detail-section-label">Skills</div>
        <div className="match-skills">
          {isScored ? (
            <>
              {matchedSkills.length > 0 && (
                <div className="skills-list">
                  <span className="skills-label">On your resume:</span>
                  {matchedSkills.map(s => <span key={s} className="skill-tag skill-matched">{s}</span>)}
                </div>
              )}
              {criticalMissing.length > 0 && (
                <div className="skills-list">
                  <span className="skills-label skills-label-critical">Required, missing:</span>
                  {criticalMissing.map(s => <span key={s} className="skill-tag skill-critical">{s}</span>)}
                </div>
              )}
              {niceToHaveMissing.length > 0 && (
                <div className="skills-list">
                  <span className="skills-label">Nice-to-have, missing:</span>
                  {niceToHaveMissing.map(s => <span key={s} className="skill-tag skill-missing">{s}</span>)}
                </div>
              )}
              {matchedSkills.length === 0 && criticalMissing.length === 0 && niceToHaveMissing.length === 0 && (
                <p style={{ color: '#a8a095', fontSize: 13 }}>No skill breakdown available.</p>
              )}
            </>
          ) : (
            fallbackSkills.length > 0 ? (
              <div className="skills-list">
                {fallbackSkills.map(s => <span key={s} className="skill-tag">{s}</span>)}
              </div>
            ) : (
              <p style={{ color: '#a8a095', fontSize: 13 }}>Score this job to see skill breakdown.</p>
            )
          )}
        </div>
      </div>

      {description && (
        <div className="job-detail-section">
          <div className="job-detail-section-label">Description</div>
          <p className="job-detail-desc">{description}</p>
        </div>
      )}

      <div className="job-detail-section">
        <div className="detail-meta-grid">
          <div className="detail-meta-cell">
            <span className="detail-meta-key">Source</span>
            <span className="detail-meta-val">{job.source}</span>
          </div>
          <div className="detail-meta-cell">
            <span className="detail-meta-key">Posted</span>
            <span className="detail-meta-val">{fmtDate(job.postedDate)}</span>
          </div>
          {isScored && (
            <div className="detail-meta-cell">
              <span className="detail-meta-key">Match</span>
              <span className="detail-meta-val">{job.matchScore}%</span>
            </div>
          )}
          {job.yearsRequiredMin != null && (
            <div className="detail-meta-cell">
              <span className="detail-meta-key">Experience req.</span>
              <span className="detail-meta-val">{job.yearsRequiredMin}+ yrs</span>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
