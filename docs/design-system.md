# ForenSim Android design system

## UI audit

| Area | Before the refresh | Compose changes |
|---|---|---|
| Student dashboard | Several unrelated sections were composed as a single lazy-list item. Active work competed with shortcuts. | Next lesson and graded work lead. Modules and results use stable list keys. Shortcuts and metrics reflow with screen width and text size. |
| Professor and admin | Fixed bottom navigation, dense inline controls, and limited workspace context. | Shared bottom bar or rail, tab-specific headings, retained scroll state, wrapping filter chips, grouped metrics, and searchable admin accounts. |
| Learning and assessment | Module, lesson, quiz, and result states had similar visual weight. Graded exit confirmation applied only to the toolbar. | Module and result panels, clearer reading hierarchy, accessible progress and answer states, and a consistent graded-exit dialog. |
| AI tutor | Static messaging dominated the chat and contextual conversations lacked a visible back action. | A module-aware introduction, suggestions, distinct reply and source treatments, labeled composer, and contextual back navigation. |
| Evidence and 3D lab | Hardware details preceded specimens, the specimen viewer required gestures, and HUD controls competed for space. | Specimen-first library, labeled rotate/zoom/reset controls, adaptive viewer, darker HUD scrims, and an action area that reserves its own space. |
| Account and settings | Long forms and control groups were hard to scan at larger text sizes. | Clear form hierarchy, wrapping account actions, accessible settings rows, adaptive appearance choices, and grouped simulation controls. |

## Foundations

The refresh used [UI/UX Pro Max](https://github.com/nextlevelbuilder/ui-ux-pro-max-skill) to generate design directions and look up Jetpack Compose, lazy-list, and touch guidance. The final design uses the skill’s minimal hierarchy and low-motion recommendations with ForenSim’s existing adult academic audience, detective mark, graphite background, and cyan accents.

| Role | Dark | Light |
|---|---|---|
| Background | `#121212` | `#F4F6F7` |
| Card | `#202428` | `#FFFFFF` |
| Text | `#EDEDED` | `#0B1220` |
| Secondary text | `#ACB6BF` | `#475569` |
| Primary | `#38BDF8` | `#0369A1` |
| Primary container | `#16303D` | `#E0F0F6` |
| Warning | `#F59E0B` | `#B45309` |
| Success | `#22C55E` | `#15803D` |

- Use Material 3 semantic colors and `ForensimTheme.colors` for status and HUD colors.
- Use the system sans. Long-form reading uses 16sp type with a 24sp line height.
- Use the 4/8/16/24/32/48dp spacing scale, 48dp minimum touch targets, and a 680dp reading width.
- Use `WorkspaceScaffold` for role navigation and `WorkspaceList` for catalog and dashboard screens. The rail begins at 600dp when there is room for its destinations.
- Use `PageIntro` for page hierarchy and `FeaturePanel` for a primary next step, briefing, or result. The measuring grid is drawn in Compose and carries no information.
- Use the existing theme previews under Settings for System, Light, and Dark. The dark theme remains the default.
- Use motion for progress and state changes. Navigation and tutor scroll honor the app’s reduced-motion setting. Compose honors the system animation duration scale.
- Keep GLSurfaceView transitions immediate. The evidence viewer requests a frame after a viewport change, gesture, or button action.

The evidence lab supports interactive 3D specimens. Camera marker detection and ARCore placement remain future work.
