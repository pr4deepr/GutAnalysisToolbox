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
  failure mode from the case half of open item 1 — the check now accepts
  whatever casing the update site actually ships. (The *version-suffix*
  disagreement below is a separate, still-open question.)
- **CI cache key fixed** (`.github/workflows/test.yml`): now hashes `pom.xml`
  (repo root) instead of the stale `Gat-IJ-Plugin/GAT-Java-Plugin/pom.xml`.
- **SonarQube config cleaned** (`.github/workflows/sonarqube.yml.txt`, still
  disabled): project key changed from the `tophatpatrick` fork to
  `pr4deepr_GutAnalysisToolbox`, added `-Dsonar.organization=pr4deepr`, fixed
  the cache path, and dropped the stale `working-directory`. Enabling it still
  requires renaming to `.yml` and adding the `SONAR_TOKEN` secret (see the
  note in the file).

## Open items (need decisions / info)

1. **Model filename version suffix (case part now handled in code).**
   The remaining disagreement is not case — it is the version suffix on the
   subtype model, which `equalsIgnoreCase` cannot bridge:
   | Source | Neuron | Subtype | Ganglia |
   |--------|--------|---------|---------|
   | GatPluginUI | `2D_enteric_neuron_V4_1.zip` | `2D_enteric_neuron_subtype_V4.zip` | `2D_Ganglia_RGB_v3.bioimage.io.model` |
   | README | `2D_enteric_neuron_v4_1.zip` | `2D_enteric_neuron_subtype_v4_1.zip` | `2D_Ganglia_RGB_v3` (folder) |
   GatPluginUI expects `subtype_V4`; README says `subtype_v4_1`. The
   **update site is the source of truth**. Get the exact filenames from
   `sites.imagej.net/GutAnalysisToolbox/db.xml.gz` (or from the maintainer),
   then set the expected string in `GatPluginUI.run()` and the README to the
   real name so they agree. (Egress to the update site is blocked in this
   session — see the egress note.)

2. **Old macro files on the update site.** `gat_v2` deleted all `.ijm`/`.groovy`
   from the repo; confirm they should also be removed from the update site on
   the next upload so the site only serves the v2 JAR + models.

3. **pom.xml coordinates.** Still placeholder: `groupId org.example`,
   `artifactId GAT-Java-Plugin`, `version 1.0`. Also `exec-maven-plugin` points
   at `UI.DevLauncher`, which does not exist (no `main` in `src/main`). Decide
   real coordinates (the artifactId becomes the published JAR name on the
   update site) before fixing, and decide whether to add a `DevLauncher` main
   or drop the `exec-maven-plugin` block. Left untouched here because these
   values are maintainer-owned and can't be verified against the current
   published JAR name while egress is blocked.

4. **Docs consolidation.** Live docs are at
   `https://gut-analysis-toolbox.gitbook.io/docs` and show the OLD macro
   interface. Not synced into this repo (only `wiki_images/` screenshots are
   here). Need either GitBook→GitHub sync (repo name) or draft markdown in
   `docs/` for import.

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
