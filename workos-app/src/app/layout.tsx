import type { Metadata } from "next";
import { Inter } from "next/font/google";
import "./globals.css";
import { AuthProvider } from "@/lib/auth";

const inter = Inter({
  variable: "--font-inter",
  subsets: ["latin"],
  display: "swap",
});

export const metadata: Metadata = {
  title: "WorkOS — Q3 Project Overview",
  description:
    "WorkOS workspace board: table, kanban, timeline, and dashboard views of the same project.",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="en" className={`${inter.variable} h-full antialiased`}>
      <body className="h-full bg-surface-subtle text-on-surface">
        <AuthProvider>{children}</AuthProvider>
      </body>
    </html>
  );
}
