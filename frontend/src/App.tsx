import { BrowserRouter, Routes, Route } from 'react-router-dom'
import { AuthProvider } from './auth/AuthProvider'
import { ProtectedRoute } from './auth/ProtectedRoute'
import { Navbar } from './components/Navbar'
import { HomePage } from './pages/HomePage'
import { MePage } from './pages/MePage'
import { RegisterPage } from './pages/RegisterPage'
import { LoginPage } from './pages/LoginPage'
import { ForgotPasswordPage } from './pages/ForgotPasswordPage'
import { ResetPasswordPage } from './pages/ResetPasswordPage'
import { CreateListingPage } from './pages/CreateListingPage'
import { MyListingsPage } from './pages/MyListingsPage'
import { ListingDetailPage } from './pages/ListingDetailPage'
import { EditListingPage } from './pages/EditListingPage'

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Navbar />
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/forgot-password" element={<ForgotPasswordPage />} />
          <Route path="/reset-password" element={<ResetPasswordPage />} />
          <Route path="/me" element={<ProtectedRoute><MePage /></ProtectedRoute>} />
          <Route path="/listings/new" element={<ProtectedRoute><CreateListingPage /></ProtectedRoute>} />
          <Route path="/listings/mine" element={<ProtectedRoute><MyListingsPage /></ProtectedRoute>} />
          <Route path="/listings/:id" element={<ProtectedRoute><ListingDetailPage /></ProtectedRoute>} />
          <Route path="/listings/:id/edit" element={<ProtectedRoute><EditListingPage /></ProtectedRoute>} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  )
}
