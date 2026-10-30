import { defineConfig } from '@qmuse/vite-config';

// @qmuse/vite-config already includes the following — do NOT add or register
// them again, or the app may break because of duplicate plugins:
//   - TanStack Router
//   - React
//   - Tailwind CSS
//   - TypeScript path aliases
//
// Add only genuinely additional configuration through defineConfig({ ... }).
// Preview and dev both need the auth proxy; SPA fallback HTML is not an API response.
const apiTarget = process.env.XIAOPIAO_API_PROXY_TARGET ?? "http://127.0.0.1:8787";
const apiOrigin = process.env.XIAOPIAO_API_ORIGIN;
const localHttpCookies = process.env.XIAOPIAO_PREVIEW_HTTP_COOKIES === "1";
const authProxy: import("vite").ProxyOptions = {
  target: apiTarget,
  changeOrigin: false,
  configure(proxy) {
    if (apiOrigin) proxy.on("proxyReq", request => { request.setHeader("Origin", apiOrigin); });
    // Only the explicitly enabled loopback QA gateway removes Secure; production stays Secure.
    if (localHttpCookies) proxy.on("proxyRes", response => {
      const cookies = response.headers["set-cookie"];
      if (cookies) response.headers["set-cookie"] = cookies.map(cookie => cookie.replace(/;\s*Secure\b/gi, ""));
    });
  },
};
export default defineConfig({
  server: { proxy: { "/v1": authProxy } },
  preview: { host: "127.0.0.1", proxy: { "/v1": authProxy } },
});
