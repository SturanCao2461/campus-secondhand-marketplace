import { ReactNode } from 'react'
import Navbar from './Navbar'

interface LayoutProps {
  children: ReactNode
}

export default function Layout({ children }: LayoutProps) {
  return (
    <div className="app-container">
      <Navbar />
      <main className="main-content">
        {children}
      </main>
      <footer className="footer">
        <p>Campus Marketplace — for students, by students. Local pickup only.</p>
      </footer>
    </div>
  )
}
