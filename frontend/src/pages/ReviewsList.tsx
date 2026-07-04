import { useReviews } from '../hooks/useReviews.ts';
import { ReviewCard } from '../components/ReviewCard.tsx';

export function ReviewsList() {
  const { reviews, loading, error, refetch } = useReviews();

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Code Reviews</h1>
          <p className="text-sm text-gray-500 mt-1">
            Auto-refreshes every 30 seconds
          </p>
        </div>
        <button
          onClick={refetch}
          className="px-3 py-1.5 text-sm bg-indigo-600 text-white rounded-md hover:bg-indigo-700 transition-colors"
        >
          Refresh
        </button>
      </div>

      {loading && (
        <div className="text-center py-16 text-gray-400">
          Loading reviews...
        </div>
      )}

      {error && (
        <div className="bg-red-50 border border-red-200 rounded-lg p-4 text-red-700 text-sm">
          {error}
        </div>
      )}

      {!loading && !error && reviews.length === 0 && (
        <div className="text-center py-16">
          <p className="text-gray-400 text-lg mb-2">No reviews yet</p>
          <p className="text-gray-400 text-sm">
            Open a pull request on a connected GitHub repo to trigger a review.
          </p>
        </div>
      )}

      <div className="space-y-3">
        {reviews.map(review => (
          <ReviewCard key={review.id} review={review} />
        ))}
      </div>
    </div>
  );
}