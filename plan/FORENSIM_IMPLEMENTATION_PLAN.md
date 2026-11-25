# FORENSIM — Implementation Plan

> **Product:** Forensim — AI-Assisted Forensic Learning, 3D Investigation & Augmented Reality  
> **Platform:** Android first; published on Google Play  
> **Primary stack:** Kotlin, Jetpack Compose, Material 3, Supabase, Filament/SceneView, CameraX, OpenCV, optional ARCore  
> **3D content format:** glTF 2.0 / `.glb`  
> **Roles:** Student, Professor, Administrator  
> **Status:** Proposed technical blueprint; milestones and APIs require prototype validation

---

## 1. Product vision

Build an accessible, mobile-first forensic education platform for criminology students that combines:

1. **Learning Management System (LMS):** course modules, lessons, resources, quizzes, assignments, enrollment, grading and analytics.
2. **Playable 3D forensic simulation:** first-person crime-scene investigation with an on-screen joystick, touch or optional gyroscope camera, tools, evidence handling, guided procedures and assessments.
3. **Hybrid Augmented Reality:** full ARCore experiences when available; marker-based camera AR and a 3D viewer fallback on devices without ARCore.
4. **AI learning assistant:** grounded lesson explanations, study guidance and performance reviews without providing live graded-assessment answers.
5. **Role-specific experiences:** student, professor and administrator functionality within the Android app; a separate web dashboard may be added later if needed.

**Non-negotiable product requirements**

- No Unity dependency. Use Android Studio, Kotlin and a native 3D renderer.
- 3D scenes use reusable `.glb` assets, assembled by a deterministic/procedural scene generator.
- The 3D simulation is an actual playable game, **not** merely a model viewer.
- Gyroscope camera controls must be **optional**, configurable and disabled when unsupported.
- The LMS, quizzes, results and most learning activities must work on phones without ARCore.
- Students on non-ARCore phones must have substantially equivalent **learning objectives**, although physical-world tracking capabilities will differ.
- Graded scores, permissions, confidential questions and AI assessment restrictions must be enforced on the server.
- Lessons and collection procedures must be reviewed by qualified forensic/criminology instructors.

### 1.1 Primary users

| Role | Core purpose | Main screens |
|---|---|---|
| Student | Learn, practice, complete assessments and review performance | Dashboard, Modules, Quizzes, 3D Lab, AR Scanner, AI Tutor, Grades, Profile |
| Professor | Teach, assess and monitor assigned students | Classrooms, Content Editor, Question Bank, Assignments, Submissions, Analytics, Simulation Scenarios |
| Administrator | Oversee users, courses, security and platform health | Users/Roles, Course Catalog, Approvals, Reports, Audit Logs, System Settings |

---

## 2. Recommended architecture

```text
Android app (Kotlin + Jetpack Compose + Material 3)
├─ Authentication and role-based navigation
├─ LMS / Quiz / Gradebook / AI Tutor
├─ 3D Simulation (Filament/SceneView + custom gameplay)
│  ├─ GLB asset loader + scene generation
│  ├─ First-person character controller
│  ├─ Joystick, touch look, optional gyro
│  ├─ Collision and interaction/raycast systems
│  └─ Evidence procedures and simulation events
├─ Hybrid AR
│  ├─ ARCore world tracking (optional)
│  ├─ CameraX + OpenCV marker pose estimation
│  └─ Interactive GLB 3D viewer (universal fallback)
└─ Room database + DataStore + WorkManager
                  │ HTTPS + authenticated Supabase SDK
                  ▼
Supabase
├─ Auth (identity)
├─ PostgreSQL (LMS, attempts, results, cases)
├─ Row Level Security (authorization)
├─ Storage (lesson media, allowed GLBs and documents)
├─ Edge Functions (trusted assessment, AI, privileged actions)
└─ Realtime (optional, limited to needed collaboration/notifications)
                  │ trusted server-to-server only
                  ▼
AI provider (model API, optional embedding/reranking service)
```

### 2.1 Technology decisions

| Area | Preferred choice | Reason / boundary |
|---|---|---|
| App language | **Kotlin** | Native Android APIs, mature tooling |
| UI | **Jetpack Compose + Material 3** | Adaptive layouts, accessible reusable components |
| Architecture | **Clean Architecture + MVVM / UDF** | Separates UI, domain rules and data |
| Dependency injection | **Hilt** | Explicit testable dependencies |
| Async state | **Coroutines + Flow** | Sensor, network and database streams |
| 3D rendering | **Google Filament**, initially through **SceneView** if prototype APIs fit | Loads/renders glTF models; **not** a complete gameplay engine |
| GLB content | **Blender → glTF 2.0 (`.glb`)** | Reusable asset and animation pipeline |
| Scene generation | **Kotlin procedural scene assembler** | Seeded templates and constrained object placement |
| Collision | **Custom lightweight collision and character controller**, with optional native physics library if needed | Must be proven in a playable prototype |
| ARCore tracking | **ARCore SDK** | Markerless world tracking on supported phones |
| Non-ARCore tracking | **CameraX + OpenCV ArUco/AprilTag detector + pose estimation** | Marker-anchored AR; requires known marker size and camera calibration |
| 3D-only fallback | **Filament/SceneView** | Camera-independent inspect/rotate/zoom mode |
| Motion controls | **Android SensorManager**, rotation vector / gyroscope where supported | Calibrated optional motion look, with touch fallback |
| Local storage | **Room + DataStore** | Offline lessons, progress queue and settings |
| Background sync | **WorkManager** | Retry pending uploads when connectivity returns |
| Backend | **Supabase Auth + PostgreSQL + Storage + Edge Functions** | Cloud data and trusted server operations |
| AI | **Provider API invoked only from Edge Functions** | Keep keys out of the APK; apply policy and retrieval limits |
| Build and release | **Gradle, Android App Bundle (`.aab`), Play Console** | Google Play publication |

**Rust:** Do **not** add Rust in version 1 without measured need. It can later be added for a clearly isolated performance-critical computation, if profiling demonstrates a benefit.

**Technical risk:** Full first-person gameplay built on Filament is a substantial custom-engine task. Before committing to all feature work, validate frame time, collisions, interaction, touch controls and scene composition in a real-device vertical slice. If these cannot meet quality goals, evaluate a Kotlin-friendly game framework such as libGDX for the simulation layer while keeping the rest of the native Android app and Unity-free constraint.

---

## 3. Feature scope by role

### 3.1 Student

- Sign up/sign in, profile, class enrollment via approved code or invitation.
- Home dashboard: enrolled courses, pending work, streak/progress (optional), scores.
- Seven forensic modules with structured chapters, media, glossary and references.
- Practice quizzes with explanations; graded quizzes with time limits, attempt rules and results.
- 3D case selection: tutorial, practice, graded assessment.
- First-person navigation, inspect, photograph, tag, collect and package evidence.
- AR learning: scan a marker or use ARCore, interact with the same `GLB` evidence objects.
- AI tutor: module Q&A, approved study cards, topic review, performance interpretation.
- Accessible learning alternatives and offline content where licensed/allowed.

### 3.2 Professor

- Manage **assigned** classrooms, enrollments and class announcements.
- Create/edit/publish modules, lessons, media and practice materials.
- Manage question bank, grading rubrics, availability windows and attempt limits.
- Assign predefined simulation scenarios and AR activities.
- Review scored attempts, weak concepts, evidence event timelines and class trends.
- Configure release dates and post-submission review permissions.
- Use AI to **draft** lesson summaries and practice materials; professor approves before publishing.
- Export class reports (CSV initially; PDF later).

### 3.3 Administrator

- Invite and approve professors; manage role changes through trusted server actions.
- Manage institutional catalog, user status, courses and class ownership.
- Review system analytics, storage consumption, moderation/incident reports.
- View audit logs for privileged operations, grading changes and role assignments.
- Manage AI usage limits, provider configuration via server secrets and system notices.
- Handle account deletion and data-retention requests consistent with applicable policies.
- Never rely on a role name saved only on the client for authorization.

---

## 4. LMS and forensic curriculum

The initial **seven-module catalog** is configurable; align naming and scope with the institution's approved criminology curriculum before release.

| # | Initial module | Example learning activity | 3D/AR companion |
|---|---|---|---|
| 1 | Forensic Photography | Scene documentation, scale and framing | Photograph evidence from suitable positions |
| 2 | Personal Identification Techniques | Fingerprint patterns and comparison basics | Inspect annotated ridge patterns |
| 3 | Forensic Chemistry & Toxicology | Laboratory safety and interpretation | Explore virtual tools and labeled samples |
| 4 | Questioned Document Examination | Alterations, handwriting and document features | Zoom and compare document evidence |
| 5 | Forensic Polygraph Procedures and Limitations | Instrument principles and interpretation limits | Examine virtual equipment and readouts |
| 6 | Forensic Ballistics | Cartridge cases, firearms identification concepts, trajectories | Inspect models and scene context |
| 7 | Crime Scene Investigation | Scene security, recording, recovery, chain of custody | Complete a multi-step investigation |

**Curriculum caution:** A polygraph does not reliably establish whether someone is lying; teaching content must accurately cover scientific limitations. The simulator must distinguish educational demonstrations from valid real-world forensic conclusions.

**LMS content hierarchy:** Course → Module → Lesson → Activity → Quiz/Assignment → Attempt → Feedback. Support prerequisites, versioned lessons, due dates, instructor notes and citations to approved source material.

### 4.1 Assessment types

- **Practice:** immediate concept feedback, optional AI coaching, no official grade.
- **Graded quiz:** server-generated attempt; no AI tutoring while the assessment is active; configurable review after completion.
- **Simulation practice:** hints allowed; procedure mistakes explained at the point of action.
- **Simulation assessment:** events scored by a server-side rubric; hints restricted; feedback released according to instructor policy.
- **AR activity:** assess identification, observation or conceptual understanding rather than the phone's tracking quality.

---

## 5. Fully playable 3D investigation system

### 5.1 Core gameplay loop

1. Receive a case briefing and educational objectives.
2. Enter the 3D scene in first-person mode.
3. Secure/assess the scene according to the scenario's approved protocol.
4. Survey the environment without disturbing evidence.
5. Photograph and document evidence location and condition.
6. Choose appropriate tools and safe collection procedures.
7. Package, label and record chain-of-custody information.
8. Submit a case report and review procedural accuracy.

### 5.2 Input and camera controls

| Control | Behavior |
|---|---|
| Left floating joystick | Forward/back/strafe movement with dead zone |
| Right-side drag | Yaw/pitch camera look |
| Gyroscope toggle | Optional motion-assisted camera rotation |
| Sensitivity slider | Camera look speed and gyro gain |
| Recenter button | Reset gyro reference orientation |
| Interact button | Examine, pick up or operate the focused object |
| Tool wheel / toolbar | Select camera, evidence bag, gloves, measuring device, etc. |
| Inventory | View collected evidence and metadata |
| Pause | Objectives, accessibility, controls and tutorial settings |

**Gyroscope implementation:** Detect supported Android sensors; use a rotation-vector-based orientation estimate or calibrated sensor fusion where appropriate. Avoid integrating raw angular velocity indefinitely (drift). Clamp pitch, smooth orientation, handle display rotation and suspend sensors outside gameplay. Without a suitable motion sensor, expose touch-only camera settings.

### 5.3 Gameplay systems and responsibilities

- **SceneLoader:** load `.glb` environments, meshes, materials, animations and textures.
- **SceneGenerator:** build scenes from validated case templates and deterministic seeds.
- **PlayerController:** move player, camera height, velocity, navigation and collision.
- **CollisionWorld:** static colliders and interaction blockers; never assume visual mesh equals usable collision geometry.
- **InteractionSystem:** center-screen reticle or tap raycast; show contextual action and distance checks.
- **ToolSystem:** usage rules, state transitions and allowed interactions.
- **EvidenceSystem:** evidence attributes, current state, collection requirements, contamination flags.
- **ProcedureEvaluator:** deterministic events matched against educator-approved procedural rubrics.
- **CaseJournal:** notes, photos, tags, timestamps and collected evidence history.
- **TutorialDirector:** guided steps, hints, voice/text instructions and accessibility cues.
- **SimulationRecorder:** versioned event log submitted to trusted grading endpoint.

### 5.4 Evidence state machine

```text
UNSEEN → DISCOVERED → DOCUMENTED → COLLECTED → PACKAGED → SUBMITTED
                 └────→ CONTAMINATED / INVALID_ACTION (when rubric applies)
```

Example: fingerprints may require scene documentation and suitable collection technique; a cartridge case requires its own prescribed procedure. Each procedure is **scenario-specific**, not a universal hard-coded checklist. Instructors should author or approve correct action ordering and explanations.

### 5.5 Procedural scene generation using `.glb`

`.glb` files contain 3D asset data. They do **not** generate a complete crime scene automatically. Use a **seeded procedural scene assembler** to construct different playable scenarios from reusable assets and authored case rules.

```text
Assets (.glb) + Scene template (JSON) + Seed + Spawn rules
        ↓
Validated object placement and evidence assignment
        ↓
Playable 3D environment + objectives + scoring rubric
```

Example scenario definition (illustrative):

```json
{
  "scenario_id": "residential_case_001",
  "version": 1,
  "seed": 4281,
  "environment_asset": "environments/room_a.glb",
  "spawn_points": ["desk", "door", "floor_north"],
  "evidence": [
    {
      "id": "ev_01",
      "asset": "evidence/cartridge_case.glb",
      "spawn_point": "floor_north",
      "procedure_id": "cartridge_document_and_package_v1"
    }
  ],
  "mission_id": "scene_documentation_intro"
}
```

Production generator constraints:

- Ensure player navigation paths remain open and evidence is reachable.
- Use explicit collision proxies and object bounding volumes.
- Prevent objects from spawning inside furniture, walls or each other.
- Keep evidence/object IDs, seeds and rubric versions stable for reproducibility.
- Validate case educational objectives before making scenarios available to students.
- AI may suggest **draft practice** scenarios, but approved templates and scoring remain deterministic.
- Do **not** ship secret graded-answer logic in the APK or publicly downloadable GLBs/scene metadata.

### 5.6 Asset performance strategy

- Export `.glb` from Blender using consistent units, pivots, naming and material rules.
- Use mesh and texture compression when supported by the chosen runtime.
- Use LOD strategies where practical; batch/instance repeated props where possible.
- Bake static lighting when appropriate; minimize expensive shadows and transparency.
- Load scenes asynchronously; release unused GPU resources.
- Separate lightweight collision shapes from detailed visual meshes.
- Test low- and mid-range Android hardware; include configurable graphics presets.
- Keep large case assets downloadable/cached with checksum and version metadata.

---

## 6. Hybrid AR design: one curriculum, three tracking modes

### 6.1 Experience modes

| Capability | Full ARCore | Camera marker-based AR (no ARCore) | Standalone 3D viewer |
|---|---|---|---|
| Live camera | Yes | Yes | Not needed |
| Render and annotate `.glb` evidence | Yes | Yes | Yes |
| Tap, inspect, rotate/zoom evidence | Yes | Yes | Yes |
| Lesson objectives and questions | Same | Same | Same |
| Tracking anchor | World tracking / detected surfaces | Printed ArUco/AprilTag with known size | Virtual 3D camera |
| Markerless room-scale positioning | Supported on capable devices | **Not equivalent** | No |
| Depth/occlusion | Depends on device | Limited/custom | Not applicable |
| Tracking when marker is out of view | ARCore dependent | Limited; expect loss/reacquisition | Stable virtual camera |

**Product promise:** Similar *interaction and learning outcomes*, **not** guaranteed identical physical-world tracking on non-ARCore devices.

### 6.2 Implementation: ARCore path

1. Detect ARCore availability at runtime; treat unavailability, missing services and session failures gracefully.
2. Request installation/support only on eligible devices.
3. Create a tracked camera/surface session and place approved 3D evidence.
4. Use shared model controls and evidence overlays.
5. Provide a one-tap switch to the marker/3D fallback if tracking is lost or unavailable.

### 6.3 Implementation: custom AR fallback

1. Use **CameraX** to stream camera frames.
2. Detect known ArUco/AprilTag markers using an Android-compatible OpenCV build/detector.
3. Estimate 6-DoF marker pose from corners, known physical marker dimensions and camera intrinsics.
4. Convert marker pose into the Filament camera/object coordinate system; validate handedness and axis mapping.
5. Smooth noisy estimates; optionally use device orientation to reduce visible rotational jitter.
6. Render `.glb` models anchored to markers with shared UI, lesson content and hotspots.
7. On marker loss, show tracking status and require reacquisition; do not falsely promise stable room-scale positioning.
8. If camera permission is denied or no usable tracking exists, open the same content in the 3D viewer.

**QR code distinction:** A QR scanner is appropriate for opening an evidence lesson, but **continuous 3D marker tracking** needs detectable marker geometry/pose estimation, not just QR decoding.

### 6.4 Device compatibility and Play distribution

- Publish the main app as **AR Optional** so missing ARCore does not automatically exclude otherwise compatible phones.
- Mark AR camera feature as non-required and configure ARCore metadata according to the SDK's AR-Optional requirements.
- Ask for camera permission contextually; all non-camera learning functions remain available on denial.
- Keep core 3D investigation gameplay independent of ARCore and optional camera/gyro sensors.
- Test devices with/without ARCore, with/without gyroscope, weak cameras and reduced GPU capacity.

---

## 7. AI learning assistant and academic integrity

### 7.1 Allowed AI experiences

- Explain concepts in the student's enrolled modules with lesson references.
- Summarize professor-approved materials and generate **ungraded** practice questions.
- Explain **completed and released** quiz feedback according to instructor settings.
- Analyze the student's **authorized** scores, completion, missed concepts and simulation mistakes.
- Suggest personalized review plans based on progress, with clear distinction between measured results and AI recommendations.
- Assist professors in creating **draft** materials, never auto-publish or silently change grades.

### 7.2 Explicit anti-cheating contract

**During any active graded quiz or simulation assessment:**

- Disable direct student AI tutor access in the assessment UI.
- Enforce the lock in **Edge Functions**, not solely in Compose UI.
- Block calls requesting unreleased question/answer data, including paraphrased or indirect requests.
- Withhold answer keys and rubric secrets from students and model retrieval context.
- Grade attempts on trusted server endpoints; do not accept a client-submitted final score as authoritative.
- Allow feedback only after submission and only to the extent released by the professor.
- Audit restricted attempts and apply rate limits; handle false positives without revealing keys.

This prevents misuse **inside Forensim's own AI service**, not all possible cheating through external apps or devices.

### 7.3 Trusted AI request pipeline

```text
Student message
  → Authenticated Edge Function
  → Verify user, enrollment and current assessment state
  → Check AI access policy (study / graded assessment / professor draft)
  → Retrieve ONLY released, role-authorized educational content
  → Add grounded lesson excerpts and permitted performance aggregates
  → Call configured AI provider with protected API credentials
  → Filter and format response; rate limit; log safe usage metadata
  → Return answer with lesson references and uncertainty as needed
```

- Prefer retrieval-augmented generation over sending the entire database to a model.
- Consider `pgvector` for a reviewed, per-course knowledge index when data volume justifies it.
- Never expose `service_role` credentials or AI provider secrets in the Android application.
- Avoid sending unnecessary personal details, raw student submissions or instructor-only content to providers.
- Publish a clear AI usage/privacy disclosure; enable server-side quotas, abuse controls and provider failure handling.
- Evaluate prompt injection in uploaded documents and student prompts; enforce retrieval and permission limits **outside** the model.

### 7.4 Performance assistant examples

- “Which forensic module should I review next?” → ranking based on published outcomes and missed topics.
- “Why is my chain-of-custody score low?” → approved feedback from released simulation events.
- “Explain evidence contamination.” → answer grounded in course materials.
- “Give me answers to the quiz I am taking.” → blocked during the active graded attempt.

---

## 8. Supabase data model (initial blueprint)

Use UUID primary keys, timestamp columns, indexes for enrollment and attempts, and explicit ownership. Names are illustrative and can be revised during schema design.

| Table / area | Important fields / responsibilities |
|---|---|
| `profiles` | user id (Auth FK), name, institution metadata, preferences |
| `user_roles` | user id, role, scope; role assignment via trusted admin action |
| `courses` | title, owner professor, state, institution |
| `classes` | course id, professor id, schedule, join rules |
| `enrollments` | class id, student id, status |
| `modules` | course id, order, prerequisites, publish state |
| `lessons` | module id, content version, citation/reference metadata |
| `lesson_assets` | lesson id, storage path, asset type, visibility |
| `assignments` | class id, activity reference, availability, due date, review policy |
| `question_bank` | owner, question text, objectives, difficulty, visibility |
| `question_options` | question id, response options; protect key data separately |
| `private_answer_keys` | correct answers, scoring keys, feedback policy; **server-only** |
| `quiz_attempts` | assignment id, student id, status, start/end, server score |
| `quiz_responses` | attempt id, question id, chosen response, saved time |
| `simulation_scenarios` | template version, asset manifest, approved procedure references |
| `simulation_attempts` | student, scenario, seed, state, server score, started/completed |
| `simulation_events` | attempt id, event sequence, action, evidence ref, timestamp |
| `evidence_definitions` | evidence type, learning objectives, allowed procedure reference |
| `student_progress` | lesson/quiz/simulation completion aggregates |
| `ai_conversations` | user, course context, policy mode, timestamps |
| `ai_usage` | request counts, cost budgets, safety/quality telemetry |
| `audit_logs` | privileged action, actor, object, result, timestamp |

### 8.1 Storage buckets

- `course-media`: published course images, audio and approved PDFs.
- `simulation-assets`: approved `.glb`, scene bundles, textures and manifests; private/signed delivery if restricted.
- `submission-attachments`: student-created permitted media, access restricted by enrollment and ownership.
- `private-admin`: sensitive reports and privileged exports; strictly role-gated.

Avoid storing grades or answer keys in publicly readable object metadata or filenames.

### 8.2 Authorization rules

- Enable RLS on all application tables exposed through Supabase APIs; default deny unless an explicit policy allows access.
- Students can read **published** lessons for classes they belong to, and only their own attempt/results data.
- Professors can read/write content, grades and enrollments **for classes they are assigned to** (subject to institution policy).
- Admin privileges are scoped and audited; privileged changes run through trusted, authenticated server functions.
- Question answer keys and active assessment rubrics are isolated in a server-only boundary; never grant students direct select access.
- Validate ownership/role separately in every privileged Edge Function.
- Scope object Storage policies to authorized courses/classes/users and distinguish released from draft materials.
- Sanitize document uploads, limit upload sizes/MIME types and use signed access where appropriate.

### 8.3 Edge Functions (proposed)

| Function | Purpose |
|---|---|
| `create-quiz-attempt` | Enforce availability, eligibility, time window and attempt count |
| `submit-quiz-attempt` | Validate responses, close attempt atomically, grade server-side |
| `start-simulation` | Authorize scenario and issue versioned attempt/seed |
| `submit-simulation-events` | Validate event sequence and compute rubric score |
| `ai-tutor` | Enforce policy and perform grounded, role-authorized AI request |
| `professor-publish-content` | Validate professor scope, content and publish action |
| `admin-manage-role` | Role changes with explicit approval and audit trail |
| `generate-progress-report` | Authorized summaries for students/professors |

---

## 9. Offline-first and synchronization

- Cache authorized lessons, manifests and selected assets locally for study and practice.
- Store UI preferences and control settings in DataStore; structured content/progress in Room.
- Record **practice** simulation events offline and sync idempotently when online.
- Require an online trusted session for **high-stakes graded** quizzes/simulations initially; avoid unsolved offline grade tampering.
- Version content, assets, rubrics and attempts so old cached data cannot silently overwrite newer records.
- Handle logout/account switching: isolate local data by user and wipe protected caches appropriately.
- Show offline/read-only states clearly; never represent unsynced grades as official.

---

## 10. UI/UX design system

**Visual direction:** professional forensic education, clean and readable, with immersive dark simulation scenes rather than overly decorative futuristic UI.

| Token | Suggested starting value |
|---|---|
| Dark background | `#0B1220` |
| Elevated surface | `#162033` |
| Primary blue/cyan | `#38BDF8` |
| Attention amber | `#F59E0B` |
| Success green | `#22C55E` |
| Typography | Inter or system typography; choose licensed/available assets |
| Component style | Material 3, accessible contrast, 8pt spacing convention |

### 10.1 Major navigation

- **Student:** Home · Learn · Simulate · AR · AI Tutor (grades/profile via Home).
- **Professor:** Classes · Content · Assessments · Analytics · Profile.
- **Admin:** Users · Courses · Reports · Audit · Settings.

### 10.2 Accessibility and ergonomics

- Support text scaling, talkback labels, high-contrast states, large touch targets and reduced-motion preferences.
- Provide captions/transcripts for video and instructions.
- Offer left-handed joystick layouts, adjustable control zones, camera sensitivity and motion input toggle.
- Avoid requiring gyroscope, camera or ARCore for core educational outcomes.
- Support both portrait LMS screens and landscape gameplay, preserving state across orientation changes.
- Use loading/sync/error/empty states rather than silent failures.

---

## 11. Suggested multi-module project layout

```text
forensim/
├── android/
│   ├── app/                       # Android entry point, navigation, configuration
│   ├── core/
│   │   ├── designsystem/          # Compose theme, components, icons
│   │   ├── common/                # Errors, shared models, utilities
│   │   ├── data/                  # Repositories, Supabase SDK, Room, sync
│   │   ├── auth/                  # Session and permission interfaces
│   │   └── testing/               # Shared fakes / fixtures
│   ├── feature/
│   │   ├── student/
│   │   ├── professor/
│   │   ├── admin/
│   │   ├── lms/
│   │   ├── quiz/
│   │   ├── ai-tutor/
│   │   ├── simulation/
│   │   └── hybrid-ar/
│   └── engine/
│       ├── renderer/              # Filament + GLB interface
│       ├── scene/                 # Procedural scene assembly
│       ├── controls/              # Touch/gyro/joystick
│       ├── physics/               # Collision, movement
│       ├── interaction/           # Targeting/tools/evidence
│       └── assessment/            # Client event recording (not final grading)
├── supabase/
│   ├── migrations/               # Versioned database schema/RLS
│   ├── functions/                # Authenticated server operations
│   ├── seed/                     # Development-only fixtures
│   └── tests/                    # RLS and Edge Function tests
├── assets/
│   ├── source-blender/           # Authoring source assets
│   ├── glb/                      # Exported assets, reviewed for mobile
│   ├── ar-markers/               # Print-ready reference markers
│   └── scene-templates/          # Scenario JSON and schemas
├── docs/
│   ├── architecture/
│   ├── forensic-procedures/
│   ├── api-contracts/
│   ├── curriculum/
│   └── release/
└── README.md
```

This can begin as a simpler Gradle project and split into modules when boundaries become stable; avoid premature complexity.

---

## 12. Development roadmap and exit criteria

### Phase 0 — Requirements, curriculum validation and technical spikes

**Build:** clickable Figma screens; approved scope; 3D rendering prototype with one room `.glb`, joystick, collision, interactable evidence and gyro toggle; custom marker tracking demo on an actual non-ARCore phone.

**Exit criteria:** proof that realistic target hardware can run gameplay and that the custom AR pose aligns and recovers acceptably. Select renderer/gameplay abstraction after measured profiling; confirm forensic procedures with faculty.

### Phase 1 — Foundation and authentication

**Build:** Kotlin app shell, theme, navigation, Supabase Auth, initial tables, RLS, roles, error handling, logging, Room/DataStore and tests.

**Exit criteria:** student/professor/admin each sees authorized screens; direct API requests cannot cross role or classroom boundaries; working logout and session recovery.

### Phase 2 — LMS minimum viable product

**Build:** course catalog, seven configurable modules, lessons, resource downloads, classroom enrollment, professor editing/publishing, practice quizzes and student progress.

**Exit criteria:** a professor can publish one end-to-end module; a student enrolls, studies, completes a practice quiz and sees accurate progress; offline cached lesson loads.

### Phase 3 — Secure graded assessment

**Build:** question-bank tooling, assessment schedules, attempt windows, server-held keys, server-side grading, feedback-release settings, instructor gradebook, audit logs.

**Exit criteria:** attempt replay/duplicate submission and direct client attempts to read answer keys fail safely; server computes grade independently of any client score.

### Phase 4 — First playable 3D case

**Build:** one authored crime scene, first-person movement, touch/gyro camera, interaction reticle, evidence inventory, tools, chain-of-custody UI, tutorial and event log.

**Exit criteria:** students can complete the case from entry to report using joystick/touch alone; collision blocks walls; input settings persist; procedure mistakes are recorded.

### Phase 5 — Procedural scenes and simulation assessment

**Build:** scene-template schema, seeded placement, spawn validation, more evidence/tool types, scenario browser, authoritative score endpoint, professor case assignments.

**Exit criteria:** identical template+seed reproduces placement; all evidence is reachable; a professor can assign a case and review a reliable, versioned action timeline and score.

### Phase 6 — Hybrid AR

**Build:** ARCore optional path, CameraX/OpenCV marker fallback, common GLB visualization/labels, print-ready markers, 3D viewer fallback and compatible learning activities.

**Exit criteria:** at least one complete activity works on an ARCore phone and a non-ARCore phone; denied camera permission routes to a useful 3D experience; learning objectives remain comparable.

### Phase 7 — AI tutor and learning analytics

**Build:** approved-content retrieval, course Q&A, released-performance summaries, limits, privacy disclosures, professor draft workflow and enforcement during assessments.

**Exit criteria:** tutor answers grounded study questions, can accurately explain allowed performance metrics, and cannot use its own endpoints to reveal unreleased assessment answers during graded attempts.

### Phase 8 — Optimization, accessibility, security and Play release

**Build:** graphics profiles, offline robustness, accessibility QA, security reviews, crash monitoring, privacy/Data Safety materials, support process, signed AAB and closed testing.

**Exit criteria:** release checklist passes across representative low/mid/high devices; legal/privacy and content approvals complete; staged rollout monitored.

**Recommended MVP release:** Phases 0–4 plus a small non-ARCore-safe 3D viewer and basic lesson AI if capacity permits. Roll out procedural generation, full hybrid AR and advanced AI after stability is demonstrated. A prototype of custom AR must still happen in Phase 0 to prevent late architectural surprises.

---

## 13. Testing strategy

| Test category | What to verify |
|---|---|
| Unit tests | Domain rules, permission predicates, quiz states, generator constraints, math/coordinate transforms |
| UI tests | Role navigation, validation, theme, settings, accessibility, progress views |
| Integration tests | Supabase SDK, Room sync, auth recovery, content publishing, server scoring |
| RLS/security tests | Student/teacher/admin boundaries, storage isolation, prohibited key access |
| Gameplay tests | Input dead zones, first-person camera, collisions, raycasts, evidence states, save/restore |
| Procedural tests | Deterministic seeds, reachability, spawn collisions, evidence-object completeness |
| AR device tests | ARCore supported/unsupported, marker visibility, tracking loss, calibration, fallback |
| AI red-team tests | Ask for graded answers indirectly, prompt injection, cross-class data exposure |
| Performance tests | Frame time, memory, startup latency, download/cache size, thermal throttling |
| Release tests | Google Play device filtering, permissions, app integrity, crash and ANR rates |

**Provisional performance objectives** (validate with representative devices):

- Target **30 FPS stable** in simulation on selected baseline supported hardware; higher if feasible.
- Prefer responsive touch/joystick input and avoid main-thread asset loading.
- Provide low/medium/high graphics settings and motion-control accessibility options.
- Record frame time and memory during a long play session; do not promise one universal APK size or frame rate without testing.

---

## 14. Google Play, privacy and operational readiness

- Publish an Android App Bundle; configure target SDK and required Google Play policies **based on the rules in force at submission time**.
- Treat ARCore as optional in manifest/store configuration; explicitly validate device filters in Play Console.
- Request camera permission only when starting a scanning/AR feature and explain its educational use.
- Provide accurate privacy policy, Data Safety answers, account-deletion path and user support contact.
- Specify retention and access policies for personal information, grades, activity logs and uploaded evidence media.
- Use TLS, managed secrets, minimum-privilege roles, server-side validation and database backups.
- Add monitoring for crashes, ANRs, Edge Function errors, AI costs and suspicious privileged actions.
- Require professor review for forensic accuracy and periodic updates to curriculum content.
- Ensure terms and behavior clearly describe this as a **training simulation**, not a validated investigative instrument.

---

## 15. Main risks and mitigations

| Risk | Mitigation |
|---|---|
| Filament is a renderer, not a full game engine | Validate custom movement/collision early; isolate renderer; consider libGDX simulation layer if prototype fails |
| Marker-based AR drifts or loses tracking | Known-size high-contrast markers, calibration, tracking status and seamless 3D viewer fallback |
| Unsupported/older Android devices | AR-Optional Play configuration, graphics tiers, touch fallback and downloadable assets |
| AI gives hints on active graded questions | Server assessment state gate, key isolation, retrieval filtering, red-team tests |
| Client can tamper with scores | Server-side rubric evaluation and validated event logs; acknowledge limits of client trust |
| AI-generated training content is inaccurate | Professor-reviewed content, curated sources and deterministic published rubrics |
| Asset size and loading overwhelm budget phones | Compressed GLBs/textures, streaming/caching, lazy loading and profiling |
| Role permission leaks | RLS default deny, authenticated Edge Functions, automated adversarial permission tests |
| Scope grows before core gameplay works | Build the 3D and non-ARCore AR technical spikes first; release in staged milestones |

---

## 16. Definition of done — first production candidate

- [ ] Student, professor and admin accounts have verified role-restricted workflows.
- [ ] At least one complete instructor-approved module contains lesson, practice quiz and graded quiz.
- [ ] Quiz answer keys are server-only and grading is server-authoritative.
- [ ] Students have a usable dashboard with saved and accurately calculated progress.
- [ ] The 3D scene is **playable** using a joystick, touch look and evidence interaction.
- [ ] Gyroscope ON/OFF, sensitivity and recenter behave correctly on supported phones.
- [ ] A complete case supports documentation, evidence collection and chain-of-custody recording.
- [ ] Practice mode explains mistakes; assessment mode scores according to reviewed rubrics.
- [ ] ARCore and non-ARCore devices can complete equivalent learning activities.
- [ ] AI tutor uses released course materials, explains performance and respects assessment locks.
- [ ] Accessibility, privacy, device compatibility, security and Play release testing pass.

---

## 17. Immediate next actions (recommended order)

1. Create Figma wireframes for Student, Professor, Admin, 3D HUD and AR evidence scanner.
2. Set up the Kotlin/Compose project, Supabase development project and CI checks.
3. **Prototype full first-person `.glb` gameplay** (one room + joystick + collisions + tap interaction + optional gyro) before constructing the full LMS.
4. **Prototype non-ARCore marker tracking** (CameraX + marker pose + Filament overlay) on a real unsupported phone.
5. Confirm classroom/role permissions and the exact seven-module curriculum with faculty.
6. Implement the database schema, RLS and server-only assessment endpoints.
7. Complete one end-to-end lesson → practice → playable case → score → tutor review flow.
8. Expand only after usability, educational correctness and device performance are demonstrated.

### Architectural decisions to lock after the spikes

- Whether SceneView/Filament plus custom collision sufficiently supports first-person gameplay, or a Kotlin-compatible game layer is necessary.
- Whether professor/admin workflows stay Android-only or benefit from an additional web dashboard.
- Which low-end device tier and minimum Android API level are feasible based on real performance tests.
- Which AI provider/model is selected after testing cost, privacy, quality and educational grounding.
- How many `.glb` assets and cases are included at install versus downloaded after login.

---

**North-star experience:** *Learn a procedure → practice it in a playable 3D crime scene → inspect evidence using AR or an equivalent 3D fallback → complete an assessment → receive grounded, professor-approved feedback and targeted AI study assistance.*
