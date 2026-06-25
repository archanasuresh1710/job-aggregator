import React, { useEffect, useState, useMemo } from 'react'
import { getQuestions, addQuestion, updateQuestion, deleteQuestion } from '../api/interviewQuestions'

const EMPTY_FORM = { section: '', question: '', answer: '', source: '' }

export default function InterviewQuestionsTab() {
  const [questions, setQuestions] = useState([])
  const [openSections, setOpenSections] = useState(new Set())
  const [revealedIds, setRevealedIds] = useState(new Set())
  const [showAddForm, setShowAddForm] = useState(null)
  const [form, setForm] = useState(EMPTY_FORM)
  const [editingId, setEditingId] = useState(null)
  const [editForm, setEditForm] = useState({})
  const [search, setSearch] = useState('')

  useEffect(() => { load() }, [])

  const load = async () => {
    const data = await getQuestions()
    setQuestions(data)
  }

  const sectionList = useMemo(() => {
    const counts = questions.reduce((acc, q) => {
      acc[q.section] = (acc[q.section] || 0) + 1
      return acc
    }, {})
    return [...new Set(questions.map(q => q.section))]
      .sort((a, b) => (counts[b] || 0) - (counts[a] || 0))
  }, [questions])

  const grouped = useMemo(() => {
    const q = search.trim().toLowerCase()
    const filtered = q
      ? questions.filter(item =>
          item.question.toLowerCase().includes(q) ||
          (item.answer || '').toLowerCase().includes(q) ||
          (item.source || '').toLowerCase().includes(q)
        )
      : questions
    return sectionList.reduce((acc, s) => {
      acc[s] = filtered.filter(item => item.section === s)
      return acc
    }, {})
  }, [questions, search, sectionList])

  const toggleSection = (s) => setOpenSections(prev => {
    const next = new Set(prev)
    next.has(s) ? next.delete(s) : next.add(s)
    return next
  })

  const toggleReveal = (id) => setRevealedIds(prev => {
    const next = new Set(prev)
    next.has(id) ? next.delete(id) : next.add(id)
    return next
  })

  const handleAdd = async (e) => {
    e.preventDefault()
    if (!form.question.trim() || !form.section.trim()) return
    const saved = await addQuestion(form)
    setQuestions(prev => [...prev, saved])
    setShowAddForm(null)
    setForm(EMPTY_FORM)
  }

  const startEdit = (q) => {
    setEditingId(q.id)
    setEditForm({ section: q.section, question: q.question, answer: q.answer || '', source: q.source || '' })
  }

  const handleEdit = async (e, id) => {
    e.preventDefault()
    const updated = await updateQuestion(id, editForm)
    setQuestions(prev => prev.map(q => q.id === id ? updated : q))
    setEditingId(null)
  }

  const handleDelete = async (id) => {
    if (!confirm('Delete this question?')) return
    await deleteQuestion(id)
    setQuestions(prev => prev.filter(q => q.id !== id))
  }

  const openAddForm = (section) => {
    setShowAddForm(section)
    setForm({ ...EMPTY_FORM, section })
    setOpenSections(prev => new Set([...prev, section]))
  }

  return (
    <div className="iq-tab">
      <div className="iq-header">
        <div>
          <h2 className="iq-title">Interview Questions</h2>
          <p className="iq-subtitle">Section-wise Q&amp;A from past interviews. Click an answer to reveal it.</p>
        </div>
        <input
          className="iq-search"
          placeholder="Search questions..."
          value={search}
          onChange={e => setSearch(e.target.value)}
        />
      </div>

      {sectionList.map(section => {
        const items = grouped[section] || []
        const isOpen = openSections.has(section)
        return (
          <div key={section} className="iq-section">
            <div className="iq-section-header" onClick={() => toggleSection(section)}>
              <div className="iq-section-left">
                <span className="iq-chevron">{isOpen ? '▾' : '▸'}</span>
                <span className="iq-section-name">{section}</span>
                <span className="iq-count">{items.length}</span>
              </div>
              <button className="iq-add-btn" onClick={e => { e.stopPropagation(); openAddForm(section) }}>
                + Add
              </button>
            </div>

            {isOpen && (
              <div className="iq-section-body">
                {showAddForm === section && (
                  <form className="iq-form" onSubmit={handleAdd}>
                    <input
                      className="iq-input"
                      placeholder="Section *"
                      list="section-options"
                      value={form.section}
                      onChange={e => setForm(f => ({ ...f, section: e.target.value }))}
                      required
                    />
                    <datalist id="section-options">
                      {sectionList.map(s => <option key={s} value={s} />)}
                    </datalist>
                    <textarea
                      className="iq-textarea"
                      placeholder="Question *"
                      value={form.question}
                      onChange={e => setForm(f => ({ ...f, question: e.target.value }))}
                      rows={2}
                      required
                    />
                    <textarea
                      className="iq-textarea"
                      placeholder="Answer"
                      value={form.answer}
                      onChange={e => setForm(f => ({ ...f, answer: e.target.value }))}
                      rows={4}
                    />
                    <input
                      className="iq-input"
                      placeholder="Source (e.g. Razorpay Round 1)"
                      value={form.source}
                      onChange={e => setForm(f => ({ ...f, source: e.target.value }))}
                    />
                    <div className="iq-form-actions">
                      <button type="submit" className="btn-action save">Save</button>
                      <button type="button" className="btn-action cancel" onClick={() => setShowAddForm(null)}>Cancel</button>
                    </div>
                  </form>
                )}

                {items.length === 0 && showAddForm !== section && (
                  <p className="iq-empty">No questions yet. Click "+ Add" to add one.</p>
                )}

                {items.map(q => (
                  <div key={q.id} className="iq-card">
                    {editingId === q.id ? (
                      <form className="iq-form" onSubmit={e => handleEdit(e, q.id)}>
                        <input
                          className="iq-input"
                          placeholder="Section *"
                          list="section-options"
                          value={editForm.section}
                          onChange={e => setEditForm(f => ({ ...f, section: e.target.value }))}
                          required
                        />
                        <textarea
                          className="iq-textarea"
                          value={editForm.question}
                          onChange={e => setEditForm(f => ({ ...f, question: e.target.value }))}
                          rows={2}
                          required
                        />
                        <textarea
                          className="iq-textarea"
                          placeholder="Answer"
                          value={editForm.answer}
                          onChange={e => setEditForm(f => ({ ...f, answer: e.target.value }))}
                          rows={4}
                        />
                        <input
                          className="iq-input"
                          placeholder="Source"
                          value={editForm.source}
                          onChange={e => setEditForm(f => ({ ...f, source: e.target.value }))}
                        />
                        <div className="iq-form-actions">
                          <button type="submit" className="btn-action save">Save</button>
                          <button type="button" className="btn-action cancel" onClick={() => setEditingId(null)}>Cancel</button>
                        </div>
                      </form>
                    ) : (
                      <>
                        <div className="iq-card-top">
                          <p className="iq-question">{q.question}</p>
                          <div className="iq-card-actions">
                            <button className="btn-action edit" onClick={() => startEdit(q)}>Edit</button>
                            <button className="btn-action delete" onClick={() => handleDelete(q.id)}>✕</button>
                          </div>
                        </div>
                        {q.source && <span className="iq-source">{q.source}</span>}
                        <button className="iq-reveal-btn" onClick={() => toggleReveal(q.id)}>
                          {revealedIds.has(q.id) ? 'Hide answer ▴' : 'Show answer ▾'}
                        </button>
                        {revealedIds.has(q.id) && (
                          <p className="iq-answer">{q.answer || '—'}</p>
                        )}
                      </>
                    )}
                  </div>
                ))}
              </div>
            )}
          </div>
        )
      })}
    </div>
  )
}
