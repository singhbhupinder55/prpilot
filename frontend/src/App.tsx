import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { ReviewsList } from './pages/ReviewsList.tsx';
import { ReviewDetail } from './pages/ReviewDetail.tsx';

function Layout({ children }: { children: React.ReactNode }) {
  return (
    <div className="min-h-screen bg-gray-50">
      {/* Nav */}
      <nav className="bg-white border-b border-gray-200 px-6 py-3 flex items-center gap-3">
        <span className="text-xl">🤖</span>
        <span className="font-bold text-gray-900 text-lg">PRPilot</span>
        <span className="text-gray-400 text-sm ml-1">AI Code Review</span>
      </nav>

      {/* Content */}
      <main className="max-w-4xl mx-auto px-6 py-8">
        {children}
      </main>
    </div>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <Layout>
        <Routes>
          <Route path="/" element={<ReviewsList />} />
          <Route path="/reviews/:id" element={<ReviewDetail />} />
        </Routes>
      </Layout>
    </BrowserRouter>
  );
}