"use client";

import { Suspense } from 'react';
import { useSearchParams } from 'next/navigation';
import { RelationshipsView } from '@/components/product/OperationsViews';

const KINDS = ['DOCUMENT', 'TRANSACTION', 'PROJECT', 'ITEM', 'SAMPLE'];
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function Relationships() {
  const params = useSearchParams();
  const kind = params.get('kind') ?? '';
  const id = params.get('id') ?? '';
  if (!KINDS.includes(kind) || !UUID.test(id)) return <RelationshipsView kind="DOCUMENT" id="00000000-0000-0000-0000-000000000000"/>;
  return <RelationshipsView kind={kind} id={id}/>;
}

export default function RelationshipsPage() {
  return <Suspense fallback={null}><Relationships/></Suspense>;
}
