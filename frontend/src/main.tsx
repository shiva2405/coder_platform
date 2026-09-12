import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter, Route, Routes } from 'react-router-dom'
import App from './App.tsx'
import ProblemListPage from './pages/ProblemListPage.tsx'
import ProblemSolvePage from './pages/ProblemSolvePage.tsx'
import './index.css'

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<App />} />
        <Route path="/s/:slug" element={<App />} />
        <Route path="/problems" element={<ProblemListPage />} />
        <Route path="/problems/:slug" element={<ProblemSolvePage />} />
      </Routes>
    </BrowserRouter>
  </React.StrictMode>,
)
