# Play Console: Data safety answers (Forensim)

Fill **Play Console > App content > Data safety** with these answers. They match the in-app
Privacy Policy (`LegalScreens.kt`, effective November 26, 2026). If the app's data handling
changes, update this file, the Privacy Policy and the form together.

## Data collection and security

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **Yes** |
| Is all of the user data collected by your app encrypted in transit? | **Yes** (HTTPS to Supabase and OpenRouter) |
| Do you provide a way for users to request that their data is deleted? | **Yes**: in the app (Profile > Settings > Account > Delete account) and on the web |
| Account deletion URL (App content > Data deletion) | `https://layfate.github.io/Forensim/legal/delete-account.html` |

### What "shared" means here

Supabase (hosting) is a service provider, so data stored there is **not shared**. Google/Facebook
sign-in send data *to* Forensim; that is not sharing either.

The AI features use **free models through OpenRouter**. Free-model providers may keep requests and
use them to improve their models, so they are not acting only on Forensim's behalf. To be safe,
the text sent to the AI is declared as **Shared** (purpose: App functionality). Only that text is
sent: no name, email or account ID.

## Data types

| Category > Type | Collected | Shared | Ephemeral | Required? | Purposes |
|---|---|---|---|---|---|
| Personal info > **Name** | Yes | No | No | Required | App functionality, Account management |
| Personal info > **Email address** | Yes | No | No | Required | App functionality, Account management |
| Personal info > **User IDs** (account ID; optional student ID number) | Yes | No | No | Required (account ID) | App functionality, Account management |
| Personal info > **Other info** (gender) | Yes | No | No | Optional | App functionality |
| Photos and videos > **Photos** (profile photo; class cover photo for professors) | Yes | No | No | Optional | App functionality |
| Messages > **Other in-app messages** (class chat) | Yes | No | No | Optional | App functionality |
| Files and docs (PDF text a professor sends to the AI quiz generator) | Yes | **Yes** | **Yes** | Optional | App functionality |
| App activity > **App interactions** (lesson progress, quiz and case attempts, daily quiz answers) | Yes | No | No | Required | App functionality |
| App activity > **Other user-generated content** (quiz answers, typed evidence answers, AI tutor questions, content reports) | Yes | **Yes** | No | Optional | App functionality |

Notes:
- AI tutor questions go to the AI and are **not stored** by Forensim (only a usage record),
  unless the user reports the answer. Stored quiz answers and reports are in the same type, so it is
  not marked ephemeral as a whole.
- The PDF file is not uploaded for quiz generation: its text is extracted on the device and sent
  once to the AI, so "Files and docs" is ephemeral.
- **Camera** images (AR evidence viewer) are processed only on the device and never leave it, so
  they are **not collected** and not listed.

**Not collected:** location, contacts, calendar, financial info, health, audio, web browsing,
device or other IDs, app info and performance (no crash/analytics SDKs), ads.

## Other App content answers

| Section | Answer |
|---|---|
| Ads | **No ads** |
| Target audience | **13 and over** (college forensic science students). Do **not** select under-13 ages, so the app stays out of the Families program. |
| Content rating (IARC) | Educational crime-scene simulation: answer **Yes** to depictions of blood / injured or deceased people in a non-graphic, educational context; **No** to gambling, drugs use promotion, sexual content, user-to-user sharing of location. **Users can interact** (class chat, limited to their class; report, block and professor moderation). |
| AI-generated content | **Yes**: AI tutor answers and AI quiz drafts. Users can report offensive or wrong output in the app (Report button under each answer; "Report a problem with the AI draft"). |
| Government app / Financial features / Health | **No** |
| Account creation | Users can create an account in the app; provide the deletion URL above. **App access**: give reviewers a student test account and a professor test account (chat, quiz publishing and the AI quiz generator are professor features). |
| Permissions | `INTERNET`, `ACCESS_NETWORK_STATE`, `VIBRATE`, `POST_NOTIFICATIONS` (daily study reminder), `CAMERA` (AR evidence viewer, asked only when AR is opened, images stay on the device), plus WorkManager's `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED` and `FOREGROUND_SERVICE`. None needs a Play declaration form. |

## User-generated content checklist (Play UGC policy)

- Terms define objectionable content and zero tolerance: Terms section 5.
- Users accept the Terms before posting: consent line on sign-in / create account.
- In-app reporting: long-press a chat message > Report message / Report person.
- In-app blocking: long-press > Block (hides that person's messages on the device; Manage to unblock).
- Moderation: professors delete messages in their class chat; administrators (Stats > Reported
  content) and professors (Profile > Reported content) review reports, remove messages and close reports.
