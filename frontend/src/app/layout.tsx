import type { Metadata } from "next";
import "./globals.css";
import { ClientProviders } from "./ClientProviders";

export const metadata: Metadata = {
  title: "FocusOS AI - AI 驱动的专注系统",
  description: "FocusOS AI 是一款 AI 驱动的个人专注与成长管理系统，集成学习、职业、知识库和日程管理功能。",
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="zh-CN" suppressHydrationWarning>
      <body className="font-sans antialiased">
        <ClientProviders>{children}</ClientProviders>
      </body>
    </html>
  );
}
