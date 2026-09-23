"use client";

import { useState } from "react";
import { motion } from "motion/react";
import { FileSpreadsheet, FileText } from "lucide-react";
import { AppShell } from "@/components/shell/AppShell";
import { PageHeader } from "@/components/shell/PageHeader";
import { SpreadsheetBuilder } from "@/components/documents/SpreadsheetBuilder";
import { WordBuilder } from "@/components/documents/WordBuilder";

type Mode = "spreadsheet" | "document";

const TABS: { id: Mode; label: string; icon: typeof FileSpreadsheet }[] = [
  { id: "spreadsheet", label: "Spreadsheet", icon: FileSpreadsheet },
  { id: "document", label: "Document", icon: FileText },
];

export default function DocumentsPage() {
  const [mode, setMode] = useState<Mode>("spreadsheet");

  return (
    <AppShell>
      <main className="w-full pt-14 min-h-screen">
        <div className="px-space-md md:px-space-xl py-space-lg max-w-6xl mx-auto">
          <PageHeader
            title="Documents"
            description="Build a spreadsheet or a Word document from scratch and download it — generated in your browser, no upload required."
          />

          <nav className="flex items-center gap-space-lg mb-space-lg border-b border-border-subtle">
            {TABS.map(({ id, label, icon: Icon }) => {
              const active = mode === id;
              return (
                <button
                  key={id}
                  onClick={() => setMode(id)}
                  className={`relative pb-2 transition-colors flex items-center gap-1.5 text-body-sm ${
                    active ? "text-primary" : "text-on-surface-variant hover:text-on-surface"
                  }`}
                >
                  <Icon size={14} />
                  <span className={active ? "text-headline-sm" : ""}>{label}</span>
                  {active && (
                    <motion.span
                      layoutId="documents-tab-underline"
                      className="absolute left-0 right-0 -bottom-px h-0.5 bg-primary rounded-full"
                      transition={{ duration: 0.3, ease: [0.16, 1, 0.3, 1] }}
                    />
                  )}
                </button>
              );
            })}
          </nav>

          {mode === "spreadsheet" ? <SpreadsheetBuilder /> : <WordBuilder />}
        </div>
      </main>
    </AppShell>
  );
}
