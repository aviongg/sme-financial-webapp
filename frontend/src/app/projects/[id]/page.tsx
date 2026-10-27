"use client";

import { useParams } from 'next/navigation';
import { ProjectDetailWorkspace } from '@/components/projects/ProjectDetailWorkspace';

export default function ProjectDetailPage() {
  const params = useParams();
  const projectId =
    typeof params.id === 'string'
      ? params.id
      : Array.isArray(params.id)
      ? params.id[0] ?? ''
      : '';
  return <ProjectDetailWorkspace key={projectId} projectId={projectId} />;
}
