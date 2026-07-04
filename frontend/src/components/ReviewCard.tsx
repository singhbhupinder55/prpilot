import { Link } from 'react-router-dom';
import type { Review } from '../types/index.ts';
import { StatusBadge } from './StatusBadge';

interface Props {
  review: Review;
}

export function ReviewCard({ review }: Props) {
  const date = new Date(review.createdAt).toLocaleString();
  const preview = review.reviewBody?.slice(0, 120) ?? 'Processing...';

  return (
    <Link
      to={`/reviews/${review.id}`}
      className="block bg-white border border-gray-200 rounded-lg p-4 hover:border-indigo-400 hover:shadow-sm transition-all"
    >
      <div className="flex items-start justify-between gap-3 mb-2">
        <div>
          <span className="text-sm font-semibold text-gray-900">
            {review.repoFullName}
          </span>
          <span className="ml-2 text-sm text-gray-500">#{review.prNumber}</span>
        </div>
        <StatusBadge status={review.status} />
      </div>

      <p className="text-sm text-gray-600 line-clamp-2 mb-3">{preview}</p>

      <div className="flex items-center gap-3 text-xs text-gray-400">
        <span>{date}</span>
        {review.chunksUsed != null && (
          <span>{review.chunksUsed} context chunks</span>
        )}
        {review.modelUsed && (
          <span className="font-mono">{review.modelUsed}</span>
        )}
      </div>
    </Link>
  );
}