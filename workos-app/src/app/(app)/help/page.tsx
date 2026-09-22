"use client";

import { useState } from "react";
import { ChevronDown, LifeBuoy, Mail, MessageCircleQuestion, Search } from "lucide-react";
import { AppShell } from "@/components/shell/AppShell";
import { PageHeader } from "@/components/shell/PageHeader";
import { Panel } from "@/components/ui/Panel";
import { Button } from "@/components/ui/Button";

const FAQS = [
  {
    q: "How do I switch between Table, Kanban, Timeline, and Dashboard views?",
    a: "Use the tabs under the board title. Each view stays in sync — changing a task's status in one view updates it everywhere instantly.",
  },
  {
    q: "How do I create a new task?",
    a: "Click \"New Task\" in the toolbar, or \"+ Add task\" / \"+ Add Task\" inside any table group or Kanban column — it opens the task panel pre-filled for that spot.",
  },
  {
    q: "Does my data get saved anywhere?",
    a: "Everything is saved locally in your browser, so your edits survive a reload. Use \"Reset demo data\" in the sidebar to start over from the original sample data.",
  },
  {
    q: "Can I reorder or drag tasks?",
    a: "Yes — on the Kanban view, drag a card between columns to change its status. Cards animate smoothly between columns as they move.",
  },
  {
    q: "What is \"My Work\"?",
    a: "It collects every task assigned to you across all boards in one place, sorted by due date, with quick counts for open, overdue, and completed work.",
  },
];

export default function HelpPage() {
  const [openIndex, setOpenIndex] = useState<number | null>(0);
  const [query, setQuery] = useState("");

  const filtered = FAQS.filter(
    (f) => f.q.toLowerCase().includes(query.toLowerCase()) || f.a.toLowerCase().includes(query.toLowerCase()),
  );

  return (
    <AppShell>
      <main className="w-full pt-14 min-h-screen">
        <div className="px-space-md md:px-space-xl py-space-lg max-w-3xl">
          <PageHeader title="Help & Support" description="Answers to common questions, and how to reach us." />

          <div className="relative mb-space-lg">
            <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-outline" />
            <input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Search help articles..."
              className="w-full pl-10 pr-space-md py-2.5 bg-canvas-bg rounded-lg shadow-sm text-body-sm text-on-surface focus:outline-none focus:ring-2 focus:ring-accent/30"
            />
          </div>

          <Panel padded={false} className="divide-y divide-border-subtle overflow-hidden mb-space-lg">
            {filtered.length === 0 && (
              <div className="p-space-lg text-center text-body-sm text-secondary">
                No help articles match &quot;{query}&quot;.
              </div>
            )}
            {filtered.map((faq) => {
              const index = FAQS.indexOf(faq);
              const isOpen = openIndex === index;
              return (
                <div key={faq.q}>
                  <button
                    onClick={() => setOpenIndex(isOpen ? null : index)}
                    className="w-full flex items-center justify-between gap-space-md p-space-md text-left hover:bg-surface-subtle transition-colors"
                  >
                    <span className="flex items-center gap-space-sm text-body-md text-on-surface">
                      <MessageCircleQuestion size={16} className="text-primary shrink-0" />
                      {faq.q}
                    </span>
                    <ChevronDown
                      size={16}
                      className={`text-outline shrink-0 transition-transform ${isOpen ? "rotate-180" : ""}`}
                    />
                  </button>
                  {isOpen && (
                    <p className="px-space-md pb-space-md pl-11 text-body-sm text-secondary">{faq.a}</p>
                  )}
                </div>
              );
            })}
          </Panel>

          <Panel className="flex items-center justify-between gap-space-md flex-wrap">
            <div className="flex items-center gap-space-md">
              <div className="w-10 h-10 rounded-lg bg-primary/10 text-primary flex items-center justify-center">
                <LifeBuoy size={18} />
              </div>
              <div>
                <h2 className="text-headline-sm text-on-surface">Still need help?</h2>
                <p className="text-body-sm text-secondary">Our team typically replies within a few hours.</p>
              </div>
            </div>
            <Button variant="primary" onClick={() => { window.location.href = "mailto:support@workos.dev"; }}>
              <Mail size={15} />
              Contact support
            </Button>
          </Panel>
        </div>
      </main>
    </AppShell>
  );
}
