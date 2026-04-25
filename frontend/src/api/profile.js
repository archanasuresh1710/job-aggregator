import axios from 'axios'

const api = axios.create({ baseURL: '/api' })

export const getProfile = () =>
  api.get('/profile').then(r => r.data)

export const saveProfile = (data) =>
  api.put('/profile', data).then(r => r.data)

export const uploadResume = (file) => {
  const form = new FormData()
  form.append('file', file)
  return api.post('/profile/resume', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000,
  }).then(r => r.data)
}
