import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  agentRules: false,
  async rewrites() {
    // Server-only origin keeps browser requests same-origin without backend CORS changes.
    const backendOrigin = (process.env.BACKEND_API_URL || "http://localhost:8080").replace(/\/+$/, "");
    return [{ source: "/api/:path*", destination: `${backendOrigin}/api/:path*` }];
  },
};

export default nextConfig;
