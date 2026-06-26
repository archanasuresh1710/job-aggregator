import React, { useEffect, useState, useMemo } from 'react'
import Editor from '@monaco-editor/react'
import { getQuestions, addQuestion, updateQuestion, deleteQuestion } from '../api/interviewQuestions'

const EMPTY_FORM = { section: '', question: '', answer: '', source: '' }

function CodeBlock({ text }) {
  const [copied, setCopied] = useState(false)
  const lineCount = (text || '').split('\n').length
  const height = Math.min(Math.max(lineCount * 19 + 16, 80), 500)

  const copy = (e) => {
    e.stopPropagation()
    navigator.clipboard.writeText(text || '')
    setCopied(true)
    setTimeout(() => setCopied(false), 1500)
  }

  return (
    <div className="iq-code-wrap" onClick={e => e.stopPropagation()}>
      <button className="iq-code-copy" onClick={copy}>{copied ? 'Copied!' : 'Copy'}</button>
      <Editor
        height={height}
        defaultLanguage="java"
        value={text || ''}
        theme="vs-dark"
        options={{
          readOnly: true,
          minimap: { enabled: false },
          scrollBeyondLastLine: false,
          lineNumbers: 'on',
          fontSize: 13,
          fontFamily: "ui-monospace, 'SF Mono', Menlo, Consolas, monospace",
          padding: { top: 8, bottom: 8 },
          renderLineHighlight: 'none',
          overviewRulerLanes: 0,
          hideCursorInOverviewRuler: true,
          scrollbar: { vertical: 'hidden', horizontal: 'auto' },
          folding: false,
          lineDecorationsWidth: 0,
          contextmenu: false,
        }}
      />
    </div>
  )
}

export default function InterviewQuestionsTab() {
  const [questions, setQuestions] = useState([])
  const [activeSection, setActiveSection] = useState(null)
  const [revealedIds, setRevealedIds] = useState(new Set())
  const [showAddForm, setShowAddForm] = useState(false)
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

  const sectionCounts = useMemo(() =>
    questions.reduce((acc, q) => { acc[q.section] = (acc[q.section] || 0) + 1; return acc }, {})
  , [questions])

  // Auto-select first section on load
  useEffect(() => {
    if (!activeSection && sectionList.length > 0) {
      setActiveSection(sectionList[0])
    }
  }, [sectionList])

  const activeQuestions = useMemo(() => {
    if (search.trim()) {
      const q = search.trim().toLowerCase()
      return questions.filter(item =>
        item.question.toLowerCase().includes(q) ||
        (item.answer || '').toLowerCase().includes(q) ||
        (item.source || '').toLowerCase().includes(q)
      )
    }
    if (!activeSection) return []
    return questions.filter(item => item.section === activeSection)
  }, [questions, search, activeSection])

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
    // If new section was created, switch to it
    if (saved.section !== activeSection) setActiveSection(saved.section)
    setShowAddForm(false)
    setForm(EMPTY_FORM)
  }

  const startEdit = (q) => {
    setEditingId(q.id)
    setEditForm({ section: q.section, question: q.question, answer: q.answer || '', source: q.source || '' })
    setRevealedIds(prev => new Set([...prev, q.id]))
  }

  const handleEdit = async (id) => {
    const updated = await updateQuestion(id, editForm)
    setQuestions(prev => prev.map(q => q.id === id ? updated : q))
    setEditingId(null)
  }

  const handleDelete = async (id) => {
    if (!confirm('Delete this question?')) return
    await deleteQuestion(id)
    setQuestions(prev => prev.filter(q => q.id !== id))
  }

  const openAddForm = () => {
    setShowAddForm(true)
    setForm({ ...EMPTY_FORM, section: activeSection || '' })
  }

  const selectSection = (s) => {
    setActiveSection(s)
    setSearch('')
    setShowAddForm(false)
    setEditingId(null)
  }

  const isSearching = search.trim().length > 0

  return (
    <div className="iq-tab">
      <div className="iq-header">
        <div>
          <h2 className="iq-title">Interview Questions</h2>
          <p className="iq-subtitle">Pick a section, then reveal answers to self-quiz.</p>
        </div>
        <input
          className="iq-search"
          placeholder="Search questions..."
          value={search}
          onChange={e => setSearch(e.target.value)}
        />
      </div>

      <div className="iq-progress">
        <div className="iq-progress-stat">
          <span className="iq-progress-num">{questions.length}</span>
          <span className="iq-progress-label">Questions</span>
        </div>
        <div className="iq-progress-stat">
          <span className="iq-progress-num">{sectionList.length}</span>
          <span className="iq-progress-label">Sections</span>
        </div>
      </div>

      {sectionList.length === 0 ? (
        <div className="iq-card" style={{ marginTop: 8 }}>
          <p className="iq-empty" style={{ marginBottom: 12 }}>No questions yet. Add your first one.</p>
          {showAddForm ? (
            <form className="iq-form" onSubmit={handleAdd}>
              <input
                className="iq-input"
                placeholder="Section name *"
                value={form.section}
                onChange={e => setForm(f => ({ ...f, section: e.target.value }))}
                required
              />
              <textarea className="iq-textarea" placeholder="Question *" rows={2}
                value={form.question} onChange={e => setForm(f => ({ ...f, question: e.target.value }))} required />
              <textarea className="iq-textarea" placeholder="Answer" rows={4}
                value={form.answer} onChange={e => setForm(f => ({ ...f, answer: e.target.value }))} />
              <input className="iq-input" placeholder="Source (e.g. Razorpay Round 1)"
                value={form.source} onChange={e => setForm(f => ({ ...f, source: e.target.value }))} />
              <div className="iq-form-actions">
                <button type="submit" className="btn-action save">Save</button>
                <button type="button" className="btn-action cancel" onClick={() => setShowAddForm(false)}>Cancel</button>
              </div>
            </form>
          ) : (
            <button className="iq-add-btn" onClick={() => setShowAddForm(true)}>+ Add first question</button>
          )}
        </div>
      ) : (
        <div className="iq-split">
          <div className="iq-rail">
            {sectionList.map(s => (
              <button
                key={s}
                className={`iq-rail-item ${s === activeSection && !isSearching ? 'active' : ''}`}
                onClick={() => selectSection(s)}
              >
                <span>{s}</span>
                <span className="iq-rail-count">{sectionCounts[s] || 0}</span>
              </button>
            ))}
          </div>

          <div className="iq-content">
            <div className="iq-content-header">
              <span className="iq-content-title">
                {isSearching ? `Results for "${search}"` : (activeSection || '')}
              </span>
              {!isSearching && (
                <button className="iq-add-btn" onClick={openAddForm}>+ Add</button>
              )}
            </div>

            {showAddForm && !isSearching && (
              <div className="iq-card">
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
                  <textarea className="iq-textarea" placeholder="Question *" rows={2}
                    value={form.question} onChange={e => setForm(f => ({ ...f, question: e.target.value }))} required />
                  <textarea className="iq-textarea" placeholder="Answer" rows={4}
                    value={form.answer} onChange={e => setForm(f => ({ ...f, answer: e.target.value }))} />
                  <input className="iq-input" placeholder="Source (e.g. Razorpay Round 1)"
                    value={form.source} onChange={e => setForm(f => ({ ...f, source: e.target.value }))} />
                  <div className="iq-form-actions">
                    <button type="submit" className="btn-action save">Save</button>
                    <button type="button" className="btn-action cancel" onClick={() => setShowAddForm(false)}>Cancel</button>
                  </div>
                </form>
              </div>
            )}

            {activeQuestions.length === 0 && !showAddForm && (
              <p className="iq-empty">
                {isSearching ? 'No questions match your search.' : 'No questions yet. Click "+ Add" to add one.'}
              </p>
            )}

            {activeQuestions.map(q => {
              const isEditing = editingId === q.id
              return (
                <div key={q.id} className={`iq-card ${isEditing ? 'iq-card-editing' : ''}`}
                  onClick={() => !isEditing && toggleReveal(q.id)}
                  style={{ cursor: isEditing ? 'default' : 'pointer' }}>
                  <div className="iq-card-top">
                    <p className="iq-question" style={{ flex: 1 }}>
                      {isSearching && !isEditing && <span className="iq-source" style={{ marginRight: 8 }}>{q.section}</span>}
                      {isEditing ? (
                        <textarea
                          className="iq-inline-edit"
                          value={editForm.question}
                          onChange={e => setEditForm(f => ({ ...f, question: e.target.value }))}
                          onClick={e => e.stopPropagation()}
                          rows={2}
                          autoFocus
                        />
                      ) : q.question}
                      {!isEditing && q.source && <span className="iq-source" style={{ marginLeft: 8 }}>{q.source}</span>}
                      {isEditing && (
                        <input
                          className="iq-inline-edit iq-inline-source"
                          placeholder="Source (e.g. Razorpay Round 1)"
                          value={editForm.source}
                          onChange={e => setEditForm(f => ({ ...f, source: e.target.value }))}
                          onClick={e => e.stopPropagation()}
                        />
                      )}
                    </p>
                    <div className="iq-card-actions">
                      {isEditing ? (
                        <>
                          <button className="btn-action save" onClick={e => { e.stopPropagation(); handleEdit(q.id) }} style={{ padding: '3px 9px', fontSize: 11 }}>Save</button>
                          <button className="btn-action cancel" onClick={e => { e.stopPropagation(); setEditingId(null) }} style={{ padding: '3px 7px', fontSize: 11 }}>✕</button>
                        </>
                      ) : (
                        <>
                          <button className="btn-action edit" onClick={e => { e.stopPropagation(); startEdit(q) }} title="Edit" style={{ padding: '3px 7px', lineHeight: 1 }}>
                            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                              <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
                              <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
                            </svg>
                          </button>
                          <button className="btn-action delete" onClick={e => { e.stopPropagation(); handleDelete(q.id) }} style={{ padding: '3px 7px', fontSize: 11 }}>✕</button>
                        </>
                      )}
                    </div>
                  </div>
                  {revealedIds.has(q.id) && (
                    isEditing
                      ? (q.section === 'DSA'
                          ? <div className="iq-code-wrap" onClick={e => e.stopPropagation()}>
                              <Editor
                                height={200}
                                defaultLanguage="python"
                                value={editForm.answer || ''}
                                theme="vs-dark"
                                onChange={v => setEditForm(f => ({ ...f, answer: v || '' }))}
                                options={{
                                  minimap: { enabled: false },
                                  scrollBeyondLastLine: false,
                                  lineNumbers: 'on',
                                  fontSize: 13,
                                  fontFamily: "ui-monospace, 'SF Mono', Menlo, Consolas, monospace",
                                  padding: { top: 8, bottom: 8 },
                                  folding: false,
                                  scrollbar: { vertical: 'hidden', horizontal: 'auto' },
                                }}
                              />
                            </div>
                          : <textarea
                              className="iq-inline-edit iq-inline-answer"
                              placeholder="Answer"
                              value={editForm.answer}
                              onChange={e => setEditForm(f => ({ ...f, answer: e.target.value }))}
                              onClick={e => e.stopPropagation()}
                              rows={4}
                            />
                        )
                      : (q.section === 'DSA'
                          ? <CodeBlock text={q.answer || ''} />
                          : <p className="iq-answer">{q.answer || '—'}</p>
                        )
                  )}
                </div>
              )
            })}
          </div>
        </div>
      )}
    </div>
  )
}
