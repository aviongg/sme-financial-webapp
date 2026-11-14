// Process/HTTP liveness only. Backend and provider acceptance are separate gates.
export const dynamic = "force-dynamic";

export function GET() {
  return Response.json({ status: "UP" }, { headers: { "Cache-Control": "no-store" } });
}
