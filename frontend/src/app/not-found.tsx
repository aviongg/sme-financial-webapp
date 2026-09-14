import { AppShell } from "@/components/layout/AppShell";
import { Container } from "@/components/ui/Container";
import { EmptyState } from "@/components/ui/EmptyState";
import { Compass } from "lucide-react";

export default function NotFound() {
  return (
    <AppShell title="Page Not Found">
      <Container width="reading" className="py-12">
        <EmptyState
          icon={<Compass className="w-6 h-6" />}
          title="Page Not Found"
          description="The destination you requested does not exist or has been moved. You can return safely to the financial dashboard."
          actionLabel="Return to Dashboard"
          actionHref="/"
        />
      </Container>
    </AppShell>
  );
}
