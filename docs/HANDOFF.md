# GAT v2 release — working handoff

---

## ▶ RESUME HERE (next session) — GitBook docs, + parked build

**Primary task: rewrite the GitBook docs via the GitBook MCP. IN PROGRESS.**
- MCP connected and working. IDs: org `T4Yc0SNaOS8Ez2RaAe8Y`, site `site_NCpJs`,
  space `UMXPxIZOpwi2uL22W18F`. All 24 live pages have been read.
- Skeleton agreed: **Goal → Inputs → Steps → Outputs**, screenshots carry the
  detail. Delivered as **change requests per page group** (drafts, never merged).
- **DONE — CR #118** `xAzrV71mNIIgMQt8I5cK`: Home trimmed to an overview with a
  card grid; new **Installation** page (`aGxkjSZ7XQWIImRwEuQP`) and **Getting
  started** page (`Ug5DeKfdTmJwrVlXllnz`, the v2 window + Preflight + first run).
- **DONE — CR #119** `JTxyYC8FJcxByLdQvuKr`: Batch Analysis marked v1-only
  (the `run(" Analyse Neurons", args)` script-parameter path does not exist in
  v2; `plugins.config` registers only `UI.GatPluginUI`). v1 content archived in
  an expandable. Maintainer decision was "mark deprecated, keep page".
- **TODO — remaining CRs:** (a) workflows: pages 1, 2, 3, 4; (b) calcium: page 8
  + children (now a stepwise 8-button panel, all screenshots obsolete);
  (c) troubleshooting restructured around what Preflight actually reports.
  Pages 5, 6, 7, Benchmarks, Contributing, Citing, Citations, Feedback and
  System Requirements are UI-independent — light edits only.
- **Blockers on merging:** (1) every v2 screenshot must be captured from a real
  Fiji — drafts carry `📸 Screenshot needed (v2)` danger hints as markers;
  (2) do NOT merge before the v2 update site actually ships, or the live docs
  will describe a plugin users can't install. `TODO(maintainer)` markers in the
  drafts flag the spots needing your input (Home status banner, BIG-EPFL site).

- **⚠ NEW DIRECTION (2026-08-28, maintainer):** *"simplify docs so it's easy to
  follow — we had a lot of text earlier."* CRs #118/#119 are written but are
  still too wordy against this bar. **Before merging, trim them**: short steps,
  tables over paragraphs, screenshots carrying the detail, no background prose
  that doesn't change what the user clicks. Apply the same bar to every
  remaining page group.

- **v2 pane screenshots — hunt results (don't redo the search):**
  - NOT in git. Searched every ref of `pr4deepr/GutAnalysisToolbox` (incl.
    `gat_v2`, `test`) and all 27 branches of the `tophatpatrick` fork where v2
    was developed. Only v1 `wiki_images/`, `Tools/commands/close_image.jpg`, and
    `Sample Images/**/segmentation_preview/*_roi_overlay_composite.jpg` (these
    last are v2 pipeline *outputs*, useful for docs, but not UI shots).
  - **STRONG LEAD, unverified:** the GitBook space holds **21 uploaded but
    unreferenced** images `image1.png … image21.png` (~637 px wide, no page
    links to them). Very likely the v2 pane screenshots. Was mid-download when
    the session ended — next step is to view them and, if they are the panes,
    wire them into the drafts by file ID (`/files/<fileId>`, no re-upload).
  - IDs: list them with `invoke_operation listFiles` on the space. Download URL
    pattern:
    `https://460806082-files.gitbook.io/~/files/v0/b/gitbook-x-prod.appspot.com/o/spaces%2FUMXPxIZOpwi2uL22W18F%2Fuploads%2F<uploadKey>%2Fimage<N>.png?alt=media`
    with uploadKey N=1..21: `CyZfliWNIIu47s60rFbn, WtoFZHxhLAL5mlKgBuD8,
    i7Hxqj3FZllKQnqnbP8Y, ogSHapICiTwJSREEZaVL, FIgQNgcLi1jP6sm9KtvJ,
    waOwWfBkzq3V6pTfXucT, sDoan5NW687iVKovKCH3, jTX5t66z1ao5L8PG9y3O,
    YeJNBXzxvZJy76yq7st0, bBxonvrd4wWQYBUckFSD, KmkjRTcTl3pkjqT2hcVb,
    MYnhlhUwcfBZfCl5CDo3, g9fwPRztbcWlBclJLBPi, uOeLYvcRnhdRdZsWwr3n,
    BP23m07Pk5O8iG3NO5jA, r96vtR8NyKhstYIJ38s5, 72a0zTu1Qkb4sFrdXBSf,
    khfxzBrPHlz1HiAwmrTK, tTYNTgikYX2lsaNuFTeX, N3MW05zhw0ZfPIgL7u0C,
    rAEkVKG0ySuxhuXRZeZC`
  - **Ask the maintainer** which commit/place they meant if these turn out not
    to be the v2 panes — they referred to "an earlier GATv2 commit" that no
    reachable git ref appears to contain.
- The read-only per-site endpoint `…/docs/~gitbook/mcp` exists (no auth) but we
  chose the write-capable server instead; don't need the read-only one.

**BUILD GREEN — pom-scijava migration DONE and validated (2026-08-30).**
scijava recovered (maintainer restored content; image.sc [t/120874] post #10).
- The migrated pom was **promoted to `pom.xml`** (the `pom-scijava.draft.xml`
  file is removed). Parent `pom-scijava 45.1.0`; BSD-3 + metadata; mockito→
  `mockito-core` + `mockito-junit-jupiter`=`${mockito.version}`. Java target =
  pom-scijava default (imglib2 8 needs Java 11+, so 8 isn't an option).
- The `populate-app` "install into Fiji" step was moved OUT of the default
  build into a profile: `mvn -Pinstall-to-fiji package -Dfiji.app.directory=…`.
  A plain `mvn package` now just builds the jar.
- **`mvn clean package` builds `target/GutAnalysisToolbox_-2.0.0.jar` and all
  10 tests pass** (JDK 17 + Maven 3.9.9, both staged — see toolchain below).
- **Enforcer caveat:** built with `-Denforcer.skip=true`. The `BanDuplicateClasses`
  rule fails locally because this machine's `~/.m2` got polluted during the
  outage (JARs tagged scijava-sourced, but the rule resolves them central-only)
  — NOT a real duplicate-class finding, and the JARs were locked so I couldn't
  purge them. Re-verify on a clean checkout / CI with enforcer ON before trusting.
- **Packaging (RESOLVED via shading):** `io.github.vincenzopalazzo:material-ui-swing`
  is NOT in Fiji by default (confirmed absent from both local Fiji installs). It
  also drags in TWO transitive jars (confirmed via `dependency:tree` 2026-08-30):
  `com.github.jiconfont:jiconfont-swing:1.0.1` and `com.github.jiconfont:jiconfont:1.0.0`.
  So GAT's full non-Fiji runtime closure is **three jars** (`material-ui-swing`,
  `jiconfont-swing`, `jiconfont`); a missing `jiconfont` surfaces as
  `NoClassDefFoundError: jiconfont/IconCode`, a missing material-ui as
  `NoClassDefFoundError: mdlaf/...`.
  **Fix implemented:** `pom.xml` now runs `maven-shade-plugin` (package phase)
  with an explicit include list of exactly those three artifacts, so the built
  `GutAnalysisToolbox_-2.0.0.jar` is self-contained (verified: 168 mdlaf + 6
  jiconfont classes bundled, **0** ij/imglib2/clij/scijava classes — Fiji-provided
  deps are correctly NOT bundled, avoiding duplicate-class conflicts). No
  relocation (mdlaf/jiconfont packages are unique to GAT). The update site now
  ships ONE jar; no version-syncing of separate dep jars. CLIJ deps ARE already
  in Fiji via the clij update site. NOTE: shade needs online mode on first run
  (pulls a plexus-utils the offline cache lacked).
- **To test in Fiji:** a bare jar-drop fails (`NoClassDefFoundError: mdlaf/...`
  then `jiconfont/IconCode`). Use `mvn -Pinstall-to-fiji package
  -Dfiji.app.directory=<Fiji>` (copies GAT + full dep closure) — but note
  `populate-app` needs online mode and will fail on locked jars if the target
  Fiji is running, so close Fiji first. Otherwise copy the GAT jar + all three
  jars above (from ~/.m2) into `<Fiji>/jars/`. Test Fiji: `C:\Clean_FIJI\Fiji`
  (set up this session with GAT + all three deps + models). Prefer a test Fiji,
  not the main.

**Uncommitted working-tree changes (persist on disk; NOTHING committed — user
rule is no auto-commit). Review with `git status` / `git diff`:**
- `pom.xml` (modified) — now the migrated pom-scijava pom (draft file removed).
- `src/main/java/UI/panes/Tools/ToolsPane.java` (modified) — one-word fix:
  `2D_enteric_neuron_V4_1.zip` → `..._v4_1.zip` (line 315 + its javadoc). Missed
  by the earlier filename reconciliation; on a case-sensitive filesystem the
  Test Rescaling / Test Probability tools would silently fall through to the
  *subtype* model.
- `src/main/java/UI/Preflight.java` (modified) — new non-blocking
  `checkPluginVersions()`: logs installed StarDist/DeepImageJ/CSBDeep versions,
  warns if outside a validated range. **DeepImageJ ≥ 3.0.0** guard is active
  (justified by the v1 macro); StarDist/CSBDeep ranges are `TODO(maintainer)`.
  Now compile-verified (build is green).
- `src/main/java/UI/Preflight.java` (modified) — new non-blocking
  `checkTensorFlow()`: GAT's bundled StarDist/DeepImageJ models are TensorFlow
  **1.x** graphs, so they crash under Fiji's default TF2. Detects the active TF
  version (reflection on `org.tensorflow.TensorFlow.version()`, falling back to
  the installed `libtensorflow` jar) and, if it is not `1.15.x`, shows the exact
  enable steps. **Confirmed on Clean_FIJI (2026-08-30): StarDist crashed until
  the TensorFlow update site was enabled AND "TensorFlow 1.15.0 CPU" selected.**

**Key finding — StarDist/DeepImageJ commands are already current** (don't redo):
the `main`-branch v1 macros use the SAME invocations as the v2 Java
`PluginCalls` (`DeepImageJ Run model_path=… display_output=all` identical;
StarDist differs only `outputType` Both vs Label Image), and they run on
DeepImageJ **>v3**. So the command layer needs no rewrite — fragility is model
format (v3 model, names already reconciled) + DL backends → handled by the
version-aware Preflight guard above.

**Local toolchain staged this session:** JDK 17 at
`C:\Users\Pradeep\jdks\jdk-17.0.20.1+1`; Maven 3.9.9 at
`C:\Users\Pradeep\maven\apache-maven-3.9.9` (IntelliJ's bundled 3.6.3 is too old
for pom-scijava 45's enforcer; system JDK is 25/26, too new for `-source 8`).

---

Working branch: `claude/gat-v2-update-site-handoff-rui7sn` (continues
`claude/gat-v2-update-site-dohqji`, which is based on `gat_v2`).
This branch = the `gat_v2` transition (macros → Maven Java plugin) plus the
commits described below.

## Context

GAT v2 is now a Maven-built Fiji plugin (was ImageJ `.ijm` macros).
- Entry point: `src/main/resources/plugins.config` → `GATV2, "Start GAT", UI.GatPluginUI`
- Startup env check: `src/main/java/UI/Preflight.java`
- Code layout is documented in `docs/architecture.md`.
- Models and plugin files are distributed via the Fiji update site
  `https://sites.imagej.net/GutAnalysisToolbox/` (NOT bundled by the build;
  `pom.xml` does not reference models).

## Done on this branch

- Reconciled the required update-site list across README, the `pom.xml`
  self-contained `app` profile, and `Preflight`. Authoritative site list:
  3D ImageJ Suite, BIG-EPFL, CSBDeep, clij, clij2, DeepImageJ,
  Gut Analysis Toolbox, IJPB-plugins (MorphoLibJ), StarDist, PTBIOP, TensorFlow.
- **TensorFlow 1.15 is required.** Enabling the TensorFlow update site is only
  half the fix — the user must also select **TensorFlow 1.15.0 CPU** via
  *Edit › Options › TensorFlow…* (GAT's models are TF 1.x graphs; StarDist
  crashes under the default TF2). Added the `TensorFlow` site to the `app`
  profile and a non-blocking `Preflight.checkTensorFlow()` warning. Docs/GitBook
  install steps must call this out.
- Calcium imaging needs the **Template Matching** plugin, which is an
  *unlisted* update site added manually: `https://sites.imagej.net/Template_Matching/`.
  `Preflight` now warns (non-blocking) if its command ("Align slices in
  stack...") is missing; README documents the manual-add step.
- Added `docs/architecture.md` (plain-language code map).
- **Preflight model check is now case-insensitive** (`UI/Preflight.checkModels`).
  It lists `Fiji/models` and compares expected names with `equalsIgnoreCase`
  instead of a case-sensitive `File.exists()`. This removes the Linux/CI
  failure mode where a pure case difference would fail preflight.
- **Model filenames reconciled (open item 1 — RESOLVED).** Source of truth =
  the repo's `Models/` folder on `main` plus the old
  `Tools/commands/gat_settings.ijm` (master), which agree:
  neuron `2D_enteric_neuron_v4_1.zip`, subtype
  `2D_enteric_neuron_subtype_v4.zip` (no `_1`), ganglia
  `2D_Ganglia_RGB_v3.bioimage.io.model`. Fixed `GatPluginUI.run()` (was
  `..._V4_1` / `subtype_V4` casing) and the README (subtype was wrongly
  `..._v4_1`; ganglia now shows the full `.bioimage.io.model` name) to match.
- **CI cache key fixed** (`.github/workflows/test.yml`): now hashes `pom.xml`
  (repo root) instead of the stale `Gat-IJ-Plugin/GAT-Java-Plugin/pom.xml`.
- **SonarQube config cleaned** (`.github/workflows/sonarqube.yml.txt`, still
  disabled): project key changed from the `tophatpatrick` fork to
  `pr4deepr_GutAnalysisToolbox`, added `-Dsonar.organization=pr4deepr`, fixed
  the cache path, and dropped the stale `working-directory`. Enabling it still
  requires renaming to `.yml` and adding the `SONAR_TOKEN` secret (see the
  note in the file).
- **pom coordinates + plugin menu set (open item — pom coordinates RESOLVED).**
  `pom.xml` now uses `groupId io.github.pr4deepr`, `artifactId
  GutAnalysisToolbox_` (ImageJ `_` convention → published JAR
  `GutAnalysisToolbox_-2.0.0.jar`), `version 2.0.0`. Removed the broken
  `exec-maven-plugin` block (pointed at a non-existent `UI.DevLauncher`).
  Plugin menu changed to the conventional layout in
  `src/main/resources/plugins.config`:
  `Plugins>GutAnalysisToolbox, "GATV2", UI.GatPluginUI` → **Plugins ▸
  GutAnalysisToolbox ▸ GATV2**. (No `DevLauncher` re-added; there is no dev
  `main` — launch via Fiji or add one later if wanted.)
- **CI hardened against SciJava outages** (`.github/workflows/test.yml`). On
  2026-08-27 a run failed purely because `maven.scijava.org` returned HTTP 503
  for core ImageJ deps (scifio, imagej-ops, mines-jtk) — these are NOT on Maven
  Central (verified 404), so there is no alternative repo to point at. Fix:
  cache `~/.m2/repository` with a `restore-keys` prefix (a warm cache from one
  green run then avoids scijava for unchanged deps), retry `mvn` 3× with
  backoff + resolver/wagon retryHandler flags, and a 40-min job timeout so a
  full outage fails fast instead of the ~1h43m hang it caused. Also removed a
  duplicate `clij2_` dependency in `pom.xml` that Maven warned about.

## Biggest open problem: StarDist / DeepImageJ version drift

**This is the maintainer's #1 pain: new DeepImageJ / StarDist releases (and new
Fiji releases) break GAT's segmentation.** Root cause, confirmed in the code:
GAT drives both plugins through their **macro-command string interface**, which
is a moving target:
- StarDist — `PluginCalls.runStarDist2DLabel` calls
  `IJ.run("Command From Macro", "command=[de.csbdresden.stardist.StarDist2D],
  args=[...]")`. The command class has been stable; the usual breakage is the
  **CSBDeep/TensorFlow backend** (note `showCsbdeepProgress` arg), not the
  string.
- DeepImageJ — `PluginCalls.runDeepImageJForGanglia` calls
  `IJ.run(in3C, "DeepImageJ Run", "model_path=[...] input_path=null
  output_folder=null display_output=all")`. These args are the **DeepImageJ 2.x**
  API; DeepImageJ 3 changed model loading + the engine system (JDLL). The old
  `check_plugin.ijm` already warned models are "not compatible with DeepImageJ
  v3". This is almost certainly the "new DeepImageJ doesn't work" case.

**Key constraint the maintainer raised:** you *cannot* pin or downgrade a plugin
version in a normal user's Fiji — the Updater installs latest from each enabled
site. So "tell users to install DIJ 2.x" is not viable. That narrows it to:

- **Path A — ride latest.** Accept everyone is on latest; update GAT's calls to
  the current DIJ 3 / StarDist API and release via GAT's own update site; add a
  **version-aware Preflight** that reads installed StarDist/DIJ versions and
  warns on mismatch so the next break is legible, not cryptic. Cheap, but a
  perpetual treadmill.
- **Path B — own the inference stack** (escape the treadmill, since you can't
  pin in the user's Fiji): run the models yourself instead of via the
  user-updatable plugins. Either ship a frozen self-contained Fiji (pom `app`
  profile — you pin because you distribute it; but a user hitting "Update"
  re-breaks it), or bundle the runtime in GAT's JAR via **JDLL**
  (`io.bioimage:dl-modelrunner`, the library DIJ 3 uses under the hood) with a
  pinned engine. Cleanest target = the **ganglia/DeepImageJ** path (bioimage.io
  model + JDLL); StarDist `.zip` is harder to move off-plugin. Bigger refactor,
  larger JAR, engine downloads, possible classpath friction.

**Decision pending (maintainer):** Path A vs B (recommendation: A now — track
latest + Preflight version guard; B for the ganglia model later via JDLL).

## Resuming in Claude Code (local) — do these there, not in cloud

The remaining work needs a real Fiji + Maven, which the cloud session lacks
(egress blocked — see note below). Resume on the branch
`claude/gat-v2-update-site-handoff-rui7sn` locally and:

1. **Capture the current plugin macro API (ground truth).** In your latest
   working Fiji: *Plugins › Macros › Record…*, run the **DeepImageJ** ganglia
   model and **StarDist 2D** once, and copy the recorded `run(...)` lines. Those
   give the exact current command names + arg keys to port into
   `PluginCalls.runDeepImageJForGanglia` / `runStarDist2DLabel` (Path A).
2. **Build & test locally:** `mvn -B test` (needs `maven.scijava.org`, which is
   reachable from your machine). The suite is 10 mock-based unit tests
   (GangliaOps ×7, NeuronsHuPipeline ×1, CalciumAnalysis ×2); they mock ImageJ,
   so no models/GPU needed. None cover `Preflight` or the invocation strings —
   consider a `PreflightTest`.
3. **Exercise the plugin in a real Fiji** (`mvn -Papp package` builds a
   self-contained Fiji, or drop `GutAnalysisToolbox_-2.0.0.jar` into
   `Fiji.app/plugins`) to actually validate segmentation end-to-end — nothing so
   far has been run in Fiji.
4. **Decide Path A vs B** above and implement.

## Open items (need decisions / info)

1. **Old macro files on the update site.** `gat_v2` deleted all `.ijm`/`.groovy`
   from the repo; confirm they should also be removed from the update site on
   the next upload so the site only serves the v2 JAR + models.

2. **Docs consolidation — leaning GitBook-only (no GitHub sync).** GitBook
   already lets non-coders edit and keeps its own version history, so a sync is
   not needed just for that. A GitBook Git Sync scaffold (`.gitbook.yaml` +
   `docs/` stub pages) was prototyped and then **reverted** to keep the repo
   clean. Sync would only add: (a) a Markdown backup of the docs in this repo,
   (b) docs edited in the same PR as code, and (c) the ability for an AI coding
   assistant to help author docs (GitBook's "AI coding assistants / skill.md"
   feature is built on top of the repo connection). If none of those are
   wanted, keep docs in GitBook only. The real work either way is **content**:
   the live pages are verbose and still describe the old macro UI, and need
   rewriting for the v2 plugin (suggested skeleton: Goal → Inputs → Steps →
   Outputs, with screenshots carrying the detail).

## Environment / egress note

The cloud session's egress policy currently BLOCKS (403 at proxy CONNECT):
`maven.scijava.org` (so `mvn` can't download ImageJ deps — build unverifiable),
`sites.imagej.net` (can't read the update site), `*.gitbook.io` (can't read
docs). To unblock: edit the `default_cloud` environment's network policy in the
Claude Code web app (or org admin settings), then start a NEW session.
**Running Claude Code locally has none of these blocks** — your machine reaches
scijava/imagej and has Fiji, which is why the "Resuming in Claude Code" steps
above belong there.

## "Can only enable GAT?" — answer

No. Fiji will not auto-enable other update sites, and Template Matching is
unlisted so it can never auto-resolve. Realistic model: GAT site + the listed
dependency sites (Updater prompts for those) + Preflight guidance. For a true
one-click experience, ship the self-contained Fiji from the `pom.xml` `app`
profile.
