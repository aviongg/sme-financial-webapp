"use client";

import { useParams } from 'next/navigation';
import { CommercialDocumentWorkspace } from '@/components/commercial/CommercialDocumentWorkspace';

export default function BillDetailPage() {
  const params = useParams();
  const id = typeof params.id === 'string' ? params.id : Array.isArray(params.id) ? params.id[0] ?? '' : '';
  return <CommercialDocumentWorkspace key={id} kind="BILL" documentId={id} />;
}
