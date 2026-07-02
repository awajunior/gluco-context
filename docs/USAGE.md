# Using Gluco Context

## Who this is for

Gluco Context is designed for experienced AAPS users who already use Nightscout and want to reason about bolus timing with the help of an AI assistant (ChatGPT, Claude, or similar).

It is not a beginner tool. It assumes you understand your own diabetes management, your AAPS settings, and how to interpret glycemic data.

---

## The core workflow

```
Describe your meal
       ↓
Tap "Prepare for AI"
       ↓
App syncs Nightscout, builds a structured snapshot, copies it and opens your AI
       ↓
Paste into your AI chat — review the analysis
       ↓
Decide: No bolus now / Copy final dose and open AAPS
       ↓
Log your decision (optional: set a reassessment reminder)
       ↓
If reassessment set: receive reminder → open app → repeat
```

---

## Step by step

### 1. First-time setup

Go to **Connections** and configure:

- **Nightscout URL** — your Nightscout instance URL
- **Nightscout token** — your read access token, if required by your Nightscout configuration (optional for public instances)
- **AI Destination** — the URL or app you use for AI (e.g. ChatGPT, Claude)
- **AAPS package** — your AAPS app package name (e.g. `info.nightscout.androidaps`)

Your Nightscout URL and token are stored locally on your device and never leave it.

Confirm that your active profile was imported correctly before proceeding.

### 2. Describe your meal

Go to the **Meal** tab and describe what you are about to eat or have just eaten — food items, estimated carbs, and any relevant notes (e.g. high fat, delayed absorption expected).

### 3. Tap "Prepare for AI"

From the **Home** tab, tap **Prepare for AI**.

The app will:
- Sync your Nightscout data
- Build a structured snapshot with your current BG, trend, IOB, active ISF and IC, sensor age, site age, meal description, and recent context
- Copy the snapshot to your clipboard
- Open your configured AI destination

### 4. Paste into your AI and review the analysis

Paste the snapshot into your AI chat. The AI will reason about the current context and provide an analysis.

**Example snapshot output (real session):**

> Snapshot 13:32 — Context Analysis | ~44 min post-meal
>
> BG: 99 mg/dL | Trend: +0.7 mg/dL/min (stable) | Sequence: 96→97→98→98→99
> Prediction 30 min: 103 mg/dL | IOB: 1.282 U | Last SMB: 132 min ago
> Active ISF (11:00): 75 mg/dL/U | IC: 1:9
> Sensor: Day 10.4 ⚠️ | Site: 72.4 h ⚠️
> Meal: salmon + rice/beans/meat/salad, ~70–85g CHO, ~44 min ago

**Example AI analysis for the snapshot above:**

> IOB 1.28U × ISF 75 = ~96 mg/dL potential coverage. Rice/beans/meat/salad absorption still active — estimated peak window 13:20–14:30 (60–90 min post-meal). No bolus now. If BG rises above 150–160 mg/dL with sustained trend → reassess. If BG drops below 85 mg/dL with negative trend → consider preventive rescue carbs.
>
> Sensor Day 10.4 + site 72.4h trigger Combined Risk Rule — reduced confidence for the next 2h. Site change recommended today.
>
> Next snapshot: 30–45 min (expected peak window for rice/beans).
>
> Confidence level: Reduced. Final decision belongs to the user.

### 5. Log your decision

Back in the app, log your decision:

- **No bolus now** — logs the decision and schedules a reassessment reminder if one was set
- **Copy final dose and open AAPS** — copies the dose to clipboard and opens AAPS for manual entry

Logging the decision is what activates the reassessment reminder.

### 6. Reassessment (optional)

If you set a reassessment reminder (15, 30, 45, 60, 90 or 120 min), you will receive a notification when the time comes.

Open the app, tap **Prepare for AI** again, and repeat the process with updated data.

---

## The AI protocol

The first time you use Gluco Context with an AI, load the full protocol:

1. Go to **Home** → tap **Copy full protocol to AI**
2. Paste it into a dedicated chat or project in your AI
3. The AI will confirm: *"Protocol loaded: Gluco Context v2.x. Ready to receive: app snapshot, meal photo or AAPS screenshot."*

After that, you only need to paste the snapshot for each meal — the protocol stays loaded in the same chat or project.

**Personal Pattern (optional):** If you have recurring patterns (e.g. delayed spike with pizza, prolonged absorption with high fat/protein), go to **Home** → tap **Copy Personal Pattern to AI** and paste it once into the same chat. The AI will factor it into future analyses.

---

## What the app does not do

- Does not calculate boluses
- Does not send commands to AAPS
- Does not make decisions — it organizes context for you to reason with AI
- Does not replace your clinical judgment or your healthcare team

The final decision is always yours.

---

## Tips for experienced users

- Use a **dedicated chat or project** in your AI for Gluco Context — this keeps the protocol and personal pattern loaded without re-pasting every time.
- The snapshot always includes the **active ISF and IC for the current time slot** — no need to look them up manually.
- **Sensor age and site age** are included in every snapshot. The AI will flag Combined Risk Rule if both exceed safe thresholds simultaneously.
- If your AI asks for clarification, answer in the same chat — the protocol context is preserved.
- After a few sessions, you will notice the AI reasoning becomes more consistent as it learns your personal patterns.
