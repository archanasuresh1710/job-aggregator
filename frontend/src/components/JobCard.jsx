import React, { useState } from 'react'
import { useProfile } from '../context/ProfileContext'

function ScoreBadge({ score }) {
  if (score == null) {
    return <span className="score-badge score-unscored" title="Not yet scored against your resume">Not scored</span>
  }
  const cls = score >= 61 ? 'score-good' : score >= 26 ? 'score-stretch' : 'score-pass'
  return <span className={`score-badge ${cls}`} title="Match score (0-100)">{score}% match</span>
}

function ExperiencePill({ job, userYears }) {
  const { experienceFit, yearsRequiredMin, experienceGapYears } = job
  if (!experienceFit) return null

  // 'match' is implied by a green score badge — don't double up.
  if (experienceFit === 'match') return null

  if (experienceFit === 'unknown') {
    return <span className="exp-pill exp-unknown" title="Job description didn't state required years">Exp. not stated</span>
  }

  if (experienceFit === 'underqualified') {
    if (experienceGapYears >= 2) {
      return (
        <span className="exp-pill exp-hard-pass" title="More than 1 year short — score capped at 25">
          Needs {yearsRequiredMin}+y · You: {userYears ?? '?'}y
        </span>
      )
    }
    return <span className="exp-pill exp-stretch" title="1 year short — score capped at 60">Stretch +1y</span>
  }

  if (experienceFit === 'overqualified') {
    return <span className="exp-pill exp-over" title="You have more experience than the role asks for">Overqualified</span>
  }

  return null
}

export default function JobCard({ job, onSeen, onBookmark, onApply, onRescore }) {
  const { profile } = useProfile()
  const userYears = profile?.resumeYearsOfExperience
  const [rescoring, setRescoring] = useState(false)

  const handleRescoreClick = async () => {
    if (rescoring) return
    setRescoring(true)
    try {
      await onRescore(job.id)
    } catch (err) {
      console.error('Rescore failed:', err)
    } finally {
      setRescoring(false)
    }
  }

  const postedDate = job.postedDate
    ? new Date(job.postedDate).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' })
    : 'Unknown date'

  const isScored = job.matchScore != null
  const matchedSkills = (job.matchedSkills || '').split(',').map(s => s.trim()).filter(Boolean)
  const missingSkills = (job.missingSkills || '').split(',').map(s => s.trim()).filter(Boolean)
  const fallbackSkills = (job.skills || '').split(',').map(s => s.trim()).filter(Boolean)

  return (
    <div className={`job-card ${job.isSeen ? 'seen' : ''}`}>
      <div className="job-card-header">
        <div className="job-card-header-left">
          <ScoreBadge score={job.matchScore} />
          <ExperiencePill job={job} userYears={userYears} />
          <span className={`source-badge source-${job.source}`}>{job.source}</span>
        </div>
        {job.isBookmarked && <span className="bookmark-indicator">Bookmarked</span>}
      </div>

      <h3 className="job-title">
        <a href={job.url} target="_blank" rel="noopener noreferrer">
          {job.title}
        </a>
      </h3>

      <div className="job-meta">
        <span>{job.company}</span>
        <span>{job.location}</span>
        <span>{postedDate}</span>
      </div>

      {isScored && job.matchRationale && (
        <p className="match-rationale" title="Why this score">{job.matchRationale}</p>
      )}

      {isScored ? (
        <div className="match-skills">
          {matchedSkills.length > 0 && (
            <div className="skills-list">
              {matchedSkills.map(s => (
                <span key={`m-${s}`} className="skill-tag skill-matched">{s}</span>
              ))}
            </div>
          )}
          {missingSkills.length > 0 && (
            <div className="skills-list">
              {missingSkills.map(s => (
                <span key={`x-${s}`} className="skill-tag skill-missing" title="In the JD but not on your resume">{s}</span>
              ))}
            </div>
          )}
        </div>
      ) : (
        fallbackSkills.length > 0 && (
          <div className="skills-list">
            {fallbackSkills.map(s => (
              <span key={s} className="skill-tag">{s}</span>
            ))}
          </div>
        )
      )}

      <div className="job-actions">
        <button onClick={() => onSeen(job.id)} disabled={job.isSeen} className="btn-seen">
          {job.isSeen ? 'Seen' : 'Mark Seen'}
        </button>
        <button onClick={() => onBookmark(job.id)} className="btn-bookmark">
          {job.isBookmarked ? 'Unbookmark' : 'Bookmark'}
        </button>
        <button onClick={() => onApply(job)} className="btn-apply">
          Mark as Applied
        </button>
        {onRescore && (
          <button
            onClick={handleRescoreClick}
            disabled={rescoring}
            className="btn-rescore"
            title="Re-run match scoring against your current resume"
          >
            {rescoring ? 'Scoring...' : (job.matchScore == null ? 'Score' : 'Rescore')}
          </button>
        )}
      </div>
    </div>
  )
}
