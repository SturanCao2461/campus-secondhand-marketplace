import { BrowserRouter, Routes, Route } from 'react-router-dom'

function PlaceholderHome() {
  return (
    <div className="p-8">
      <h1 className="text-3xl font-semibold">Campus Secondhand Marketplace</h1>
      <p className="mt-2 text-slate-600">Frontend skeleton ready. Auth pages coming online task by task.</p>
    </div>
  )
}

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<PlaceholderHome />} />
      </Routes>
    </BrowserRouter>
  )
}
