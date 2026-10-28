"use client";

import { useParams } from 'next/navigation';
import { EstimateWorkspace } from '@/components/estimates/EstimateWorkspace';

export default function EstimateDetailPage() {
  const params = useParams();
  const id = typeof params.id === 'string' ? params.id : Array.isArray(params.id) ? params.id[0] ?? '' : '';
  return <EstimateWorkspace key={id} estimateId={id}/>;
}
