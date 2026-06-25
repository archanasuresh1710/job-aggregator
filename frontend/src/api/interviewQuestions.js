import axios from 'axios'

const api = axios.create({ baseURL: '/api' })

export const getQuestions = () =>
  api.get('/interview-questions').then(r => r.data)

export const addQuestion = (data) =>
  api.post('/interview-questions', data).then(r => r.data)

export const updateQuestion = (id, data) =>
  api.put(`/interview-questions/${id}`, data).then(r => r.data)

export const deleteQuestion = (id) =>
  api.delete(`/interview-questions/${id}`)

export const renameSection = (from, to) =>
  api.patch('/interview-questions/rename-section', { from, to })
