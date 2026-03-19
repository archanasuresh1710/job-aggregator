import React from 'react'

export default function JobCard({ job, onSeen, onBookmark, onApply }) {
  const postedDate = job.postedDate
    ? new Date(job.postedDate).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' })
    : 'Unknown date'

  return (
    <div className={`job-card ${job.isSeen ? 'seen' : ''}`}>
      <div className="job-card-header">
        <span className={`source-badge source-${job.source}`}>{job.source}</span>
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

      {job.skills && (
        <div className="skills-list">
          {job.skills.split(',').map(skill => (
            <span key={skill} className="skill-tag">{skill.trim()}</span>
          ))}
        </div>
      )}

      {job.description && (
        <p className="job-description">{job.description.slice(0, 200)}...</p>
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
      </div>
    </div>
  )
}
