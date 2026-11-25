# Play Console: Data safety answers (Forensim)

Fill **Play Console > App content > Data safety** with these answers. They match the in-app
Privacy Policy (`LegalScreens.kt`, effective November 24, 2026). If the app's data handling
changes, update this file, the Privacy Policy and the form together.

## Data collection and security

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **Yes** |
| Is all of the user data collected by your app encrypted in transit? | **Yes** (HTTPS to Supabase and the AI tutor) |
| Do you provide a way for users to request that their data is deleted? | **Yes**: in the app (Profile > Settings > Account > Delete account) and on the web |
| Account deletion URL (App content > Data deletion) | `<your site>/legal/delete-account.html` |

"Shared" means sent to a third party *other than* a service provider acting on your behalf.
Supabase (hosting) and Anthropic (AI model for the tutor) are service providers, so nothing is
marked **Shared**. Google/Facebook sign-in send data *to* Forensim; that is not sharing either.

## Data types

Every type below is **Collected: Yes, Shared: No, Processed ephemerally: No** (except where noted).

| Category > Type | Required or optional | Purposes |
|---|---|---|
| Personal info > **Name** | Required | App functionality, Account management |
| Personal info > **Email address** | Required | App functionality, Account management |
| Personal info > **User IDs** (account ID; optional student ID number) | Required (account ID) | App functionality, Account management |
| Personal info > **Other info** (gender) | Optional | App functionality |
| Photos and videos > **Photos** (profile photo) | Optional | App functionality |
| Messages > **Other in-app messages** (class chat) | Optional | App functionality |
| App activity > **App interactions** (lesson progress, quiz and case attempts, daily quiz answers) | Required | App functionality |
| App activity > **Other user-generated content** (quiz answers, typed evidence answers, AI tutor questions) | Optional | App functionality |

AI tutor questions: sent to Anthropic to generate the answer and **not stored** by Forensim
(only a usage record). You may tick **Processed ephemerally** for the AI tutor portion; since the
same type also covers stored quiz answers, leave the type as collected (not ephemeral).

**Not collected:** location, contacts, calendar, financial info, health, audio, files and docs,
web browsing, device or other IDs, app info and performance (no crash/analytics SDKs), ads.

## Other App content answers

| Section | Answer |
|---|---|
| Ads | **No ads** |
| Target audience | **13 and over** (college forensic science students). Do **not** select under-13 ages, so the app stays out of the Families program. |
| Content rating (IARC) | Educational crime-scene simulation: answer **Yes** to depictions of blood / injured or deceased people in a non-graphic, educational context; **No** to gambling, drugs use promotion, sexual content, user-to-user sharing of location. Class chat = **users can interact** (limited to their class, moderated by the professor). |
| Government app / Financial features / Health | **No** |
| Account creation | Users can create an account in the app; provide the deletion URL above. Demo login for reviewers: create a student test account (e.g. `student1@gmail.com`) and enter its password under **App access**. |
| Permissions | `INTERNET`, `ACCESS_NETWORK_STATE`, `VIBRATE`, `POST_NOTIFICATIONS` (daily study reminder), plus WorkManager's normal permissions (`WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`) for the reminder schedule. No sensitive permissions needing a declaration form. When the AR camera feature ships, add `CAMERA` here and re-check the form (camera frames processed only on the device are not "collected"). |
