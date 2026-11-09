'use client';

import { useParams } from 'next/navigation';
import { ProjectActualsWorkspace } from '@/components/finance/projects/ProjectActualsWorkspace';

export default function ProjectActualsPage() {
  const params = useParams();
  const projectId =
    typeof params.id === 'string'
      ? params.id
      : Array.isArray(params.id)
      ? params.id[0] ?? ''
      : '';

  if (!projectId) {
    return <div className="p-8 text-center text-xs text-muted-foreground">Missing project identifier.</div>;
  }

  return <ProjectActualsWorkspace key={projectId} projectId={projectId} />;
}
