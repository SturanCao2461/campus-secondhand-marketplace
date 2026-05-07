import { BrowserRouter, Routes, Route } from 'react-router-dom'
import { AuthProvider } from './auth/AuthProvider'
import { Navbar } from './components/Navbar'

function PlaceholderHome() {
  return (
    <div className="mx-auto max-w-5xl px-4 py-8">
      <h1 className="text-3xl font-semibold">Campus Secondhand Marketplace</h1>
      <p className="mt-2 text-slate-600">
        Auth pages will appear here as they come online.
      </p>
    </div>
  )
}

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Navbar />
        <Routes>
          <Route path="/" element={<PlaceholderHome />} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  )
}
