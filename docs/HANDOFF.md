# GAT v2 release — working handoff

Working branch: `claude/gat-v2-update-site-dohqji` (based on `gat_v2`).
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

## Open items (need decisions / info)

1. **Model filename case mismatch (blocks a passing preflight).**
   `UI/GatPluginUI.java` checks exact, case-sensitive names that disagree with
   README and the old repo copies:
   | Source | Neuron | Subtype | Ganglia |
   |--------|--------|---------|---------|
   | GatPluginUI | `2D_enteric_neuron_V4_1.zip` | `2D_enteric_neuron_subtype_V4.zip` | `2D_Ganglia_RGB_v3.bioimage.io.model` |
   | README | `2D_enteric_neuron_v4_1.zip` | `2D_enteric_neuron_subtype_v4_1.zip` | `2D_Ganglia_RGB_v3` (folder) |
   The **update site is the source of truth**. Get the exact filenames from
   `sites.imagej.net/GutAnalysisToolbox/db.xml.gz` (or from the maintainer) and
   make `GatPluginUI` + README match exactly.

2. **Old macro files on the update site.** `gat_v2` deleted all `.ijm`/`.groovy`
   from the repo; confirm they should also be removed from the update site on
   the next upload so the site only serves the v2 JAR + models.

3. **pom.xml coordinates.** Still placeholder: `groupId org.example`,
   `artifactId GAT-Java-Plugin`, `version 1.0`. Also `exec-maven-plugin` points
   at `UI.DevLauncher`, which does not exist. Decide real coordinates (the
   artifactId becomes the published JAR name on the update site) before fixing.

4. **CI.** `.github/workflows/test.yml` cache key hashes a wrong path
   (`Gat-IJ-Plugin/GAT-Java-Plugin/pom.xml`; pom is at repo root).
   `sonarqube.yml.txt` is disabled and points at the old path + the
   `tophatpatrick` fork's project key. SonarCloud is free for this public repo.

5. **Docs consolidation.** Live docs are at
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
