# ETest Service | ILM The Contest Center

Responsive student assessment portal starter for GitHub Pages + Supabase.

## Pages
- `index.html`: student sign in and account request
- `student.html`: student portal and Test ID entry
- `test.html`: timed assessment interface
- `about.html`: vision, mission and ILM sponsorship
- `admin-login.html`: administrator sign in
- `admin.html`: test builder, student approval queue and attempt list

## Setup checklist
1. Create a new Supabase project.
2. Open **SQL Editor** and run all of `supabase/schema.sql`.
3. In Supabase Authentication, enable email/password sign-in. Email confirmation is recommended.
4. Sign up your own administrator account from the website.
5. In Supabase SQL Editor, promote your account using the final UPDATE statement in `supabase/schema.sql`, replacing `YOUR_ADMIN_EMAIL` with your account email.
6. Edit `assets/config.js` and add the Supabase Project URL and publishable key. Never use a secret/service_role key in browser code.
7. In GitHub, open Settings → Pages → Deploy from branch → main → /(root).
8. Open https://malakaimal.github.io/etest/.

## Student access: both routes
Students can request accounts from the sign-up page. Every new account starts as **pending**. An administrator approves the student in the dashboard. For existing ILM students, the admin can approve their account after checking the ILM registration details. A later admissions integration can securely sync an approved student roster.

## Test builder question format
Paste a JSON array into the Questions field:
```json
[
  {
    "prompt": "What is 2 + 2?",
    "type": "mcq",
    "options": ["2", "3", "4", "5"],
    "correct_option": 2,
    "marks": 1
  },
  {
    "prompt": "Explain your method.",
    "type": "short",
    "marks": 2
  }
]
```
Supported types in this starter: `mcq`, `short`, `long`. MCQ `correct_option` is zero-based (0 means first option). Answer keys are kept in a separate admin-only table. Math can use Unicode symbols such as × ÷ √ π ² ³ ∑ ≤ ≥.

## Security and readiness
- Application tables use Row Level Security.
- Admin permissions are database-backed, not based on a hidden URL or a JavaScript passkey.
- Only the Supabase publishable key belongs in browser config. Never commit service_role/secret keys.
- This starter does not secretly record camera, microphone or screen. Browser permissions must only be requested for a genuine, clearly explained feature with student consent.
- This is a first functional scaffold, not yet ready for high-stakes examinations. Before real tests, add and test server-side grading, server-enforced deadlines, autosave/recovery, detailed results, Excel export, incident review workflow and RLS tests. Browser-side countdowns and answer submission are not tamper-proof.
