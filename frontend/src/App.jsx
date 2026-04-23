import React, { useEffect, useState, useCallback } from 'react'
import FilterBar from './components/FilterBar'
import JobCard from './components/JobCard'
import ApplicationsTab from './components/ApplicationsTab'
import AddApplicationModal from './components/AddApplicationModal'
import MyDetailsTab from './components/MyDetailsTab'
import { ProfileProvider } from './context/ProfileContext'
import { getJobs, markSeen, toggleBookmark, triggerIngestion } from './api/jobs'

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

  const country = activeTab === 'global' ? 'GLOBAL' : 'IN'
  const effectiveDomain = activeTab === 'global' ? 'fintech'
                        : (activeTab === 'feed' && feedView === 'fintech') ? 'fintech'
                        : null
  const locationFilter = activeTab === 'feed' && feedView === 'bangalore' ? 'bangalore' : null
  const sponsorship = activeTab === 'global'

  const fetchJobs = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const data = await getJobs(keyword || null, source || null, effectiveDomain, hideSeen, country, sponsorship, locationFilter)
      setJobs(data)
    } catch {
      setError('Failed to load jobs. Is the backend running?')
    } finally {
      setLoading(false)
    }
  }, [keyword, source, effectiveDomain, hideSeen, country, sponsorship, locationFilter])

  useEffect(() => {
    if (activeTab === 'applied') return
    const delay = setTimeout(fetchJobs, 400)
    return () => clearTimeout(delay)
  }, [fetchJobs, activeTab])

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

  const handleIngest = async () => {
    await triggerIngestion()
    fetchJobs()
  }

  const switchTab = (tab) => {
    setActiveTab(tab)
    setKeyword('')
    setSource('')
  }

  const jobListSection = (badge, emptyMsg) => (
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
      {badge && <div className="global-badge">{badge}</div>}
      <main className="job-list">
        {loading && <p className="status">Loading...</p>}
        {error && <p className="status error">{error}</p>}
        {!loading && !error && jobs.length === 0 && <p className="status">{emptyMsg}</p>}
        {jobs.map(job => (
          <JobCard
            key={job.id}
            job={job}
            onSeen={handleSeen}
            onBookmark={handleBookmark}
            onApply={setApplyJob}
          />
        ))}
      </main>
    </>
  )

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
          <button className={`tab ${activeTab === 'global' ? 'active' : ''}`}
            onClick={() => switchTab('global')}>
            🌐 Global {activeTab === 'global' && <span className="tab-count">{jobs.length}</span>}
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
          <main className="job-list">
            {loading && <p className="status">Loading...</p>}
            {error && <p className="status error">{error}</p>}
            {!loading && !error && jobs.length === 0 && (
              <p className="status">No jobs found. Click "Fetch Now" to pull from sources.</p>
            )}
            {jobs.map(job => (
              <JobCard key={job.id} job={job}
                onSeen={handleSeen} onBookmark={handleBookmark} onApply={setApplyJob} />
            ))}
          </main>
        </>
      )}

      {activeTab === 'global' && jobListSection(
        '🇬🇧 UK Fintech — Visa Sponsorship Only',
        'No matching jobs yet. Click "Fetch Now" to pull from sources.'
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
            status: 'Applied',
          }}
        />
      )}
    </div>
    </ProfileProvider>
  )
}
