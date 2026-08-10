import { NextRequest, NextResponse } from 'next/server';

const PROTECTED_PATHS = ['/dashboard', '/learning', '/career', '/rag', '/schedule', '/settings', '/workflow', '/evaluation'];

export function middleware(request: NextRequest) {
  const token = request.cookies.get('accessToken')?.value;
  const authHeader = request.headers.get('authorization');
  const hasHeaderToken = authHeader?.startsWith('Bearer ');

  const path = request.nextUrl.pathname;
  const isProtected = PROTECTED_PATHS.some(p => path.startsWith(p));
  const isAuthPath = path === '/login' || path === '/register';
  const hasAuth = !!token || hasHeaderToken;

  if (isAuthPath && hasAuth) {
    return NextResponse.redirect(new URL('/dashboard', request.url));
  }

  if (isProtected && !hasAuth) {
    const loginUrl = new URL('/login', request.url);
    loginUrl.searchParams.set('from', path);
    return NextResponse.redirect(loginUrl);
  }

  return NextResponse.next();
}

export const config = {
  matcher: [
    '/dashboard/:path*', '/learning/:path*', '/career/:path*',
    '/rag/:path*', '/schedule/:path*', '/settings/:path*',
    '/workflow/:path*', '/evaluation/:path*',
    '/login', '/register'
  ],
};
