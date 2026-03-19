import axios from 'axios'

const api = axios.create({ baseURL: '/api' })

export const getApplications = (status, company, sort) =>
  api.get('/applications', { params: { status, company, sort } }).then(r => r.data)

export const getAllApplications = () =>
  api.get('/applications').then(r => r.data)

export const addApplication = (data) =>
  api.post('/applications', data).then(r => r.data)

export const updateStatus = (id, payload) =>
  api.patch(`/applications/${id}/status`, payload).then(r => r.data)

export const deleteApplication = (id) =>
  api.delete(`/applications/${id}`)

export const uploadCsv = (file) => {
  const form = new FormData()
  form.append('file', file)
  return api.post('/applications/upload', form).then(r => r.data)
}
