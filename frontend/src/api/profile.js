import axios from 'axios'

const api = axios.create({ baseURL: '/api' })

export const getProfile = () =>
  api.get('/profile').then(r => r.data)

export const saveProfile = (data) =>
  api.put('/profile', data).then(r => r.data)
