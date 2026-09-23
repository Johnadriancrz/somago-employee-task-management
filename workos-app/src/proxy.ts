import { NextResponse, type NextRequest } from "next/server";
import { SESSION_COOKIE } from "@/lib/server/session-cookie";
import { ADMIN_SESSION_COOKIE } from "@/lib/server/admin-session-cookie";

const PUBLIC_PAGES = ["/login"];

/**
 * Only checks that the session cookie is present, for redirect UX (no flash
 * of protected content, no dead API calls from a page that will bounce).
 * The route handlers themselves (via requireSessionPersonId) are what
 * actually validate the cookie against the session store — this is a fast,
 * cheap gate in front of that, not the source of truth.
 */
export function proxy(request: NextRequest) {
  const { pathname } = request.nextUrl;

  // The admin area is a fully separate auth system (its own cookie, its own
  // session store) — handled first, in its own branch, so it never falls
  // through to the regular workspace-user checks below.
  if (pathname.startsWith("/api/admin/auth")) {
    return NextResponse.next();
  }

  if (pathname.startsWith("/api/admin/")) {
    if (!request.cookies.has(ADMIN_SESSION_COOKIE)) {
      return NextResponse.json({ error: "Not authenticated" }, { status: 401 });
    }
    return NextResponse.next();
  }

  const isAdminArea = pathname === "/admin" || pathname.startsWith("/admin/");
  if (isAdminArea) {
    const hasAdminSession = request.cookies.has(ADMIN_SESSION_COOKIE);
    const isAdminLoginPage = pathname === "/admin/login";

    if (!hasAdminSession && !isAdminLoginPage) {
      const url = request.nextUrl.clone();
      url.pathname = "/admin/login";
      url.searchParams.set("next", pathname);
      return NextResponse.redirect(url);
    }
    if (hasAdminSession && isAdminLoginPage) {
      const url = request.nextUrl.clone();
      url.pathname = "/admin";
      url.search = "";
      return NextResponse.redirect(url);
    }
    return NextResponse.next();
  }

  const hasSession = request.cookies.has(SESSION_COOKIE);

  if (pathname.startsWith("/api/auth")) {
    return NextResponse.next();
  }

  if (pathname.startsWith("/api/")) {
    if (!hasSession) {
      return NextResponse.json({ error: "Not authenticated" }, { status: 401 });
    }
    return NextResponse.next();
  }

  const isPublicPage = PUBLIC_PAGES.includes(pathname);

  if (!hasSession && !isPublicPage) {
    const url = request.nextUrl.clone();
    url.pathname = "/login";
    url.searchParams.set("next", pathname);
    return NextResponse.redirect(url);
  }

  if (hasSession && isPublicPage) {
    const url = request.nextUrl.clone();
    url.pathname = "/";
    url.search = "";
    return NextResponse.redirect(url);
  }

  return NextResponse.next();
}

export const config = {
  matcher: ["/((?!_next/static|_next/image|favicon.ico|.*\\.(?:png|jpg|jpeg|gif|svg|webp|ico)$).*)"],
};
