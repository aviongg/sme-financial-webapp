"use client";
import { useParams } from 'next/navigation';
import { SampleDetailWorkspace } from '@/components/samples/SampleDetailWorkspace';
export default function SampleDetailPage() {
  const params = useParams(); const id = typeof params.id === 'string' ? params.id : Array.isArray(params.id) ? params.id[0] ?? '' : '';
  return <SampleDetailWorkspace activityId={id} />;
}