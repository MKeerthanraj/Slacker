# SLA Tracker — Setup Guide (for a first-time coder)

This is a native Android app (Kotlin + Jetpack Compose) that runs fully offline
except for one thing: when you use "Quick Add" to type a task/case in plain
English, it calls the free Groq API to turn it into structured data.

## What's built (v0.1)
- **Tasks board**: swipeable columns — To Do / In Progress / Blocked / Done
- **Support case board**: swipeable columns — New / Triaged / In Review / Resolved / Done,
  each card shows the *next* SLA checkpoint due and turns red if breached
- **SLA engine**: your Sev 1–5 scheme (Initial Triage / Labs Review / Final / RCA),
  fully editable in **Settings** — values are stored in hours
- **Quick Add**: type "Finish X by tomorrow 5pm" or "New Sev 2 case: login broken" —
  Groq extracts the title/deadline/severity, and asks a follow-up question in-chat
  if something's missing (e.g. no severity given)
- **Notifications**: a background check runs every 15 minutes (the shortest Android
  allows) and alerts you when a task or SLA checkpoint is approaching or overdue

## One-time setup

### 1. Open the project
Open Android Studio → **Open** → select the `SlaTracker` folder (this one).
Let it sync Gradle the first time (may take a few minutes, downloads dependencies).

### 2. Add your Groq API key
1. Get a free key at https://console.groq.com/keys
2. In Android Studio's Project view, find `local.properties` in the root folder
   (Android Studio auto-creates this file with your SDK path — it's gitignored,
   so your key never gets committed anywhere)
3. Add this line to it:
   ```
   GROQ_API_KEY=gsk_your_actual_key_here
   ```
4. Sync Gradle again (the elephant/refresh icon, or File → Sync Project with Gradle Files)

### 3. Run it
Plug in your Android phone (enable USB debugging in Developer Options) or use an
emulator, then hit the green ▶ Run button. Pick your device, wait for it to install.

On first launch it'll ask for notification permission — say yes, or you won't get
deadline alerts.

## How the SLA math works
Every severity level (Settings tab) has 4 numbers, all in **hours**:
- Initial Triage, Labs Review, Final — all counted from when you create the case
- RCA — counted from the moment you move the case's status to **Done**

So "immediately" = `0`, "1 day" = `24`, "1 week" = `168`, "3 weeks" = `504`, etc.
Change any of these anytime in Settings — it applies to future calculations
immediately (existing case due-dates recalculate live, since they're computed on
the fly rather than stored).

## Known limitations to know about (fine for v0.1, easy to improve later)
- **Notification dedup**: the 15-min check will re-fire a notification each time
  it's still overdue/approaching, rather than notifying once. Easy fix later:
  track a "last notified" timestamp per checkpoint.
- **Quick Add follow-up questions**: if Groq asks a clarifying question, your next
  message currently starts a fresh parse rather than remembering the original text.
  Fine for short follow-ups ("Sev 2" as the reply), but not full context-carrying yet.
- **No drag-and-drop between columns** — tap "Move to next status" on a card instead.
  This was a deliberate simplification for a first build; can add drag later.
- **Groq free tier rate limits** apply — if Quick Add ever errors out, it's usually
  a rate limit; the app will show the error text so you'll know why.

## Where to go next
Tell me what to build next, e.g.:
- Drag-and-drop between board columns
- Editing/deleting existing tasks and cases (currently just status changes)
- A "My Day" summary screen combining tasks + case SLA breaches
- Per-item custom reminder timing in the UI (the field already exists in the DB)
- Exporting data as backup

Just let me know and I'll add it to this same project.
