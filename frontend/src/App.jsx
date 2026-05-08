import React, { useEffect, useState, useCallback, useMemo } from 'react'
import FilterBar from './components/FilterBar'
import JobCard from './components/JobCard'
import ApplicationsTab from './components/ApplicationsTab'
import AddApplicationModal from './components/AddApplicationModal'
import MyDetailsTab from './components/MyDetailsTab'
import { ProfileProvider } from './context/ProfileContext'
import { getJobs, markSeen, toggleBookmark, triggerIngestion, rescoreJob } from './api/jobs'

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
  const [focusedIndex, setFocusedIndex] = useState(-1)
  const [bannerDismissed, setBannerDismissed] = useState(
    () => typeof localStorage !== 'undefined' && localStorage.getItem('feedBannerDismissed') === '1'
  )

  const dismissBanner = () => {
    localStorage.setItem('feedBannerDismissed', '1')
    setBannerDismissed(true)
  }

  const effectiveDomain = feedView === 'fintech' ? 'fintech' : null
  const locationFilter = feedView === 'bangalore' ? 'bangalore' : null

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

  useEffect(() => {
    if (focusedIndex < 0) return
    if (sortedJobs.length === 0) {
      setFocusedIndex(-1)
    } else if (focusedIndex >= sortedJobs.length) {
      setFocusedIndex(sortedJobs.length - 1)
    }
  }, [sortedJobs.length, focusedIndex])

  useEffect(() => {
    if (focusedIndex < 0) return
    const job = sortedJobs[focusedIndex]
    if (!job) return
    const el = document.querySelector(`[data-job-id="${job.id}"]`)
    if (el) el.scrollIntoView({ block: 'nearest', behavior: 'smooth' })
  }, [focusedIndex, sortedJobs])

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
    const updated = await rescoreJob(id)
    setJobs(prev => prev.map(j => j.id === id ? updated : j))
  }

  const handleIngest = async () => {
    await triggerIngestion()
    fetchJobs()
  }

  const switchTab = (tab) => {
    setActiveTab(tab)
    setKeyword('')
    setSource('')
    setFocusedIndex(-1)
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

      const focused = focusedIndex >= 0 && focusedIndex < len ? sortedJobs[focusedIndex] : null

      switch (e.key) {
        case 'j':
          e.preventDefault()
          setFocusedIndex(i => Math.min(i < 0 ? 0 : i + 1, len - 1))
          break
        case 'k':
          e.preventDefault()
          setFocusedIndex(i => Math.max(0, i < 0 ? 0 : i - 1))
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
          setFocusedIndex(-1)
          break
        default:
      }
    }

    window.addEventListener('keydown', handler)
    return () => window.removeEventListener('keydown', handler)
  }, [activeTab, applyJob, sortedJobs, focusedIndex, hideSeen])

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
                onClick={() => setFeedView('bangalore')}>
                Bangalore
              </button>
              <button className={`domain-tab ${feedView === 'fintech' ? 'active' : ''}`}
                onClick={() => setFeedView('fintech')}>
                Fintech
              </button>
            </div>
            <div className="sort-control" role="group" aria-label="Sort jobs">
              <span className="sort-label">Sort</span>
              <button
                className={`sort-pill ${sortMode === 'score' ? 'active' : ''}`}
                onClick={() => setSortMode('score')}>
                Best match
              </button>
              <button
                className={`sort-pill ${sortMode === 'date' ? 'active' : ''}`}
                onClick={() => setSortMode('date')}>
                Newest
              </button>
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
          <main className="job-list">
            {loading && <p className="status">Loading...</p>}
            {error && <p className="status error">{error}</p>}
            {!loading && !error && sortedJobs.length === 0 && (
              <p className="status">No jobs found. Click "Fetch Now" to pull from sources.</p>
            )}
            {sortedJobs.map((job, idx) => (
              <JobCard key={job.id} job={job}
                isFocused={idx === focusedIndex}
                onSeen={handleSeen} onBookmark={handleBookmark}
                onApply={setApplyJob} onRescore={handleRescore} />
            ))}
          </main>
        </>
      )}

      {activeTab === 'applied' && <ApplicationsTab />}
      {activeTab === 'details' && <MyDetailsTab />}

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
