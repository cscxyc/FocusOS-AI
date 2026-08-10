import Link from "next/link";
import { Sparkles, Home } from "lucide-react";

export default function NotFound() {
  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50 dark:bg-gray-950 px-4">
      <div className="text-center">
        <div className="flex h-16 w-16 mx-auto mb-6 items-center justify-center rounded-2xl bg-gradient-to-br from-brand-500 to-accent-500 shadow-lg shadow-brand-500/25">
          <Sparkles className="h-8 w-8 text-white" />
        </div>
        <h1 className="text-7xl font-bold bg-gradient-to-r from-brand-600 to-accent-600 bg-clip-text text-transparent">
          404
        </h1>
        <p className="text-xl font-semibold text-gray-900 dark:text-white mt-4">
          页面未找到
        </p>
        <p className="text-sm text-gray-500 dark:text-gray-400 mt-2">
          你访问的页面不存在或已被移动
        </p>
        <Link
          href="/dashboard"
          className="inline-flex items-center gap-2 mt-6 rounded-xl bg-gradient-to-r from-brand-600 to-accent-600 px-6 py-2.5 text-sm font-medium text-white shadow-lg shadow-brand-500/25 hover:shadow-xl hover:shadow-brand-500/30 transition-all"
        >
          <Home className="h-4 w-4" />
          返回 Dashboard
        </Link>
      </div>
    </div>
  );
}
