import { useState, useEffect, useCallback } from 'react';
import axios from 'axios';
import type { Review } from '../types/index';

const API_BASE = import.meta.env.VITE_API_URL || '';

export function useReviews() {
  const [reviews, setReviews] = useState<Review[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchReviews = useCallback(async () => {
    try {
      const res = await axios.get<Review[]>(`${API_BASE}/api/reviews`);
      setReviews(res.data);
      setError(null);
    } catch {
      setError('Failed to load reviews. Is review-service running?');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchReviews();
    const interval = setInterval(fetchReviews, 30_000);
    return () => clearInterval(interval);
  }, [fetchReviews]);

  return { reviews, loading, error, refetch: fetchReviews };
}

export function useReview(id: string) {
  const [review, setReview] = useState<Review | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    axios.get<Review>(`${API_BASE}/api/reviews/${id}`)
      .then(res => { setReview(res.data); setError(null); })
      .catch(() => setError('Review not found'))
      .finally(() => setLoading(false));
  }, [id]);

  return { review, loading, error };
}