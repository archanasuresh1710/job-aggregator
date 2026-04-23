import React, { createContext, useContext, useEffect, useState } from 'react'
import { getProfile } from '../api/profile'

const ProfileContext = createContext(null)

export function ProfileProvider({ children }) {
  const [profile, setProfile] = useState({})

  useEffect(() => {
    getProfile().then(setProfile).catch(() => {})
  }, [])

  return (
    <ProfileContext.Provider value={{ profile, setProfile }}>
      {children}
    </ProfileContext.Provider>
  )
}

export const useProfile = () => useContext(ProfileContext)
