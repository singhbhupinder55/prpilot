export interface Review {
  id: string;
  repoFullName: string;
  prNumber: number;
  headSha: string;
  deliveryId: string;
  status: 'PENDING' | 'COMPLETED' | 'FAILED';
  reviewBody: string | null;
  modelUsed: string | null;
  chunksUsed: number | null;
  createdAt: string;
  completedAt: string | null;
}