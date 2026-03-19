import { Routes, Route } from 'react-router-dom'
import Layout from './components/Layout'
import Home from './pages/Home'
import Listings from './pages/Listings'
import ListingDetail from './pages/ListingDetail'
import Login from './pages/Login'
import Register from './pages/Register'
import Favorites from './pages/Favorites'
import Messages from './pages/Messages'
import MyListings from './pages/MyListings'

function App() {
  return (
    <Layout>
      <Routes>
        <Route path="/" element={<Home />} />
        <Route path="/listings" element={<Listings />} />
        <Route path="/listings/:id" element={<ListingDetail />} />
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />
        <Route path="/favorites" element={<Favorites />} />
        <Route path="/messages" element={<Messages />} />
        <Route path="/my-listings" element={<MyListings />} />
      </Routes>
    </Layout>
  )
}

export default App
