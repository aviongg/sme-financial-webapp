import DemoOnboardingPage from "@/components/onboarding/DemoOnboardingPage";
import { LiveOnboardingPage } from "@/components/onboarding/LiveOnboardingPage";
import { isDemoMode } from "@/lib/api/config";

export default function OnboardingPage() {
  return isDemoMode ? <DemoOnboardingPage /> : <LiveOnboardingPage />;
}
