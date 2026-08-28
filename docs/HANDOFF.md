# GAT v2 release — working handoff

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
  Gut Analysis Toolbox, IJPB-plugins (MorphoLibJ), StarDist, PTBIOP.
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
