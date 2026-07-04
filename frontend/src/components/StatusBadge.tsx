interface Props {
  status: 'PENDING' | 'COMPLETED' | 'FAILED';
}

export function StatusBadge({ status }: Props) {
  const styles = {
    PENDING:   'bg-yellow-100 text-yellow-800 border border-yellow-200',
    COMPLETED: 'bg-green-100  text-green-800  border border-green-200',
    FAILED:    'bg-red-100    text-red-800    border border-red-200',
  };
  const icons = { PENDING: '⏳', COMPLETED: '✅', FAILED: '❌' };

  return (
    <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium ${styles[status]}`}>
      {icons[status]} {status}
    </span>
  );
}