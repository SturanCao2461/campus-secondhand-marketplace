import { BrowserRouter, Routes, Route } from 'react-router-dom'
import { AuthProvider } from './auth/AuthProvider'
import { ProtectedRoute } from './auth/ProtectedRoute'
import { Navbar } from './components/Navbar'
import { ToastProvider } from './components/Toast'
import { ErrorBoundary } from './components/ErrorBoundary'
import { HomePage } from './pages/HomePage'
import { MePage } from './pages/MePage'
import { RegisterPage } from './pages/RegisterPage'
import { LoginPage } from './pages/LoginPage'
import { ForgotPasswordPage } from './pages/ForgotPasswordPage'
import { ResetPasswordPage } from './pages/ResetPasswordPage'
import { VerifyEmailPage } from './pages/VerifyEmailPage'
import { CreateListingPage } from './pages/CreateListingPage'
import { MyListingsPage } from './pages/MyListingsPage'
import { ListingDetailPage } from './pages/ListingDetailPage'
import { EditListingPage } from './pages/EditListingPage'
import { BrowsePage } from './pages/BrowsePage'
import { PublicDetailPage } from './pages/PublicDetailPage'
import { ConversationsPage } from './pages/ConversationsPage'
import { ChatPage } from './pages/ChatPage'

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <ToastProvider>
          <ErrorBoundary>
            <Navbar />
            <Routes>
              <Route path="/" element={<HomePage />} />
              <Route path="/browse" element={<BrowsePage />} />
              <Route path="/listings/:id/detail" element={<PublicDetailPage />} />
              <Route path="/register" element={<RegisterPage />} />
              <Route path="/login" element={<LoginPage />} />
              <Route path="/forgot-password" element={<ForgotPasswordPage />} />
              <Route path="/reset-password" element={<ResetPasswordPage />} />
              <Route path="/verify-email" element={<VerifyEmailPage />} />
              <Route
                path="/me"
                element={
                  <ProtectedRoute>
                    <MePage />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/listings/new"
                element={
                  <ProtectedRoute>
                    <CreateListingPage />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/listings/mine"
                element={
                  <ProtectedRoute>
                    <MyListingsPage />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/listings/:id"
                element={
                  <ProtectedRoute>
                    <ListingDetailPage />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/listings/:id/edit"
                element={
                  <ProtectedRoute>
                    <EditListingPage />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/conversations"
                element={
                  <ProtectedRoute>
                    <ConversationsPage />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/conversations/:id"
                element={
                  <ProtectedRoute>
                    <ChatPage />
                  </ProtectedRoute>
                }
              />
            </Routes>
          </ErrorBoundary>
        </ToastProvider>
      </AuthProvider>
    </BrowserRouter>
  )
}
