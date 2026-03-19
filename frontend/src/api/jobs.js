import axios from 'axios'

const api = axios.create({ baseURL: '/api' })

export const getJobs = (keyword, source, domain, hideSeen) =>
  api.get('/jobs', { params: { keyword, source, domain, hideSeen } }).then(r => r.data)

export const markSeen = (id) =>
  api.patch(`/jobs/${id}/seen`).then(r => r.data)

export const toggleBookmark = (id) =>
  api.patch(`/jobs/${id}/bookmark`).then(r => r.data)

export const triggerIngestion = () =>
  api.post('/jobs/ingest').then(r => r.data)
