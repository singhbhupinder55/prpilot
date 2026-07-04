import { useParams, Link } from 'react-router-dom';
import { useReview } from '../hooks/useReviews.ts';
import { StatusBadge } from '../components/StatusBadge.tsx';

export function ReviewDetail() {
  const { id } = useParams<{ id: string }>();
  const { review, loading, error } = useReview(id!);

  if (loading) return (
    <div className="text-center py-16 text-gray-400">Loading review...</div>
  );

  if (error || !review) return (
    <div className="text-center py-16">
      <p className="text-red-500 mb-4">{error ?? 'Review not found'}</p>
      <Link to="/" className="text-indigo-600 hover:underline text-sm">← Back to reviews</Link>
    </div>
  );

  const createdAt = new Date(review.createdAt).toLocaleString();
  const completedAt = review.completedAt
    ? new Date(review.completedAt).toLocaleString()
    : null;

  return (
    <div>
      <Link to="/" className="text-indigo-600 hover:underline text-sm mb-6 inline-block">
        ← Back to reviews
      </Link>

      {/* Header */}
      <div className="bg-white border border-gray-200 rounded-lg p-5 mb-4">
        <div className="flex items-start justify-between gap-3 mb-3">
          <div>
            <h1 className="text-xl font-bold text-gray-900">
              {review.repoFullName} <span className="text-gray-400">#{review.prNumber}</span>
            </h1>
            <p className="text-xs text-gray-400 font-mono mt-1">{review.headSha.slice(0, 8)}</p>
          </div>
          <StatusBadge status={review.status} />
        </div>

        <div className="flex flex-wrap gap-4 text-xs text-gray-500">
          <span>Created: {createdAt}</span>
          {completedAt && <span>Completed: {completedAt}</span>}
          {review.chunksUsed != null && <span>{review.chunksUsed} context chunks</span>}
          {review.modelUsed && <span className="font-mono">{review.modelUsed}</span>}
        </div>
      </div>

      {/* Review body */}
      {review.reviewBody ? (
        <div className="bg-white border border-gray-200 rounded-lg p-5">
          <h2 className="text-sm font-semibold text-gray-700 mb-4 flex items-center gap-2">
            🤖 PRPilot AI Review
          </h2>
          <div className="prose prose-sm max-w-none">
            <pre className="whitespace-pre-wrap font-sans text-sm text-gray-800 leading-relaxed">
              {review.reviewBody}
            </pre>
          </div>
        </div>
      ) : (
        <div className="bg-gray-50 border border-gray-200 rounded-lg p-8 text-center text-gray-400">
          {review.status === 'PENDING'
            ? 'Review is being generated...'
            : 'No review content available.'}
        </div>
      )}
    </div>
  );
}