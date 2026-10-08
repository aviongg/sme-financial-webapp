import { Container } from "@/components/ui/Container";
import { Skeleton } from "@/components/ui/Skeleton";

export default function Loading() {
  return (
    <div className="min-h-screen bg-[var(--color-surface-canvas)] py-8">
      <Container width="dashboard" className="space-y-6">
        <div className="flex items-center justify-between">
          <Skeleton variant="heading" className="w-48 h-8" />
          <Skeleton variant="rectangular" className="w-32 h-10" />
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          <Skeleton variant="rectangular" className="h-44 md:col-span-2" />
          <Skeleton variant="rectangular" className="h-44" />
        </div>

        <Skeleton variant="rectangular" className="h-64 w-full" />
      </Container>
    </div>
  );
}
