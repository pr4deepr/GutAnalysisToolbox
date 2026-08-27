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

## "Can only enable GAT?" — answer

No. Fiji will not auto-enable other update sites, and Template Matching is
unlisted so it can never auto-resolve. Realistic model: GAT site + the listed
dependency sites (Updater prompts for those) + Preflight guidance. For a true
one-click experience, ship the self-contained Fiji from the `pom.xml` `app`
profile.
