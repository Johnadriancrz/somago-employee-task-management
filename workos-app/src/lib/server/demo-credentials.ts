/**
 * Every seed account shares this one password — the mock data has no real
 * per-user credentials. It's shown as a hint on the login page. A real
 * backend replaces the check in /api/auth/login with a per-user password
 * hash comparison (or hands off to an OAuth/SSO provider) and this file
 * goes away entirely.
 */
export const DEMO_PASSWORD = "demo1234";
