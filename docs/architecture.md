# How GAT's code is organised

A plain-language map of the repository, for contributors who are not Java
developers. GAT v2 is a Fiji plugin built with Maven.

## Top-level layout

```
GutAnalysisToolbox/
├── pom.xml                 ← build recipe (dependencies, how to package for Fiji)
├── README.md               ← install / usage docs
├── src/main/java/          ← all the actual code
├── src/main/resources/     ← plugins.config: adds "GATV2 › Start GAT" to the Fiji menu
├── src/test/java/          ← automated tests
├── docs/                   ← documentation (this file)
└── wiki_images/            ← screenshots used in docs
```

## The code (`src/main/java/`), split by job

| Folder | Plain meaning |
|--------|--------------|
| `UI/` | Everything you see — windows, buttons, forms, dialogs. `GatPluginUI` is the entry point; `Preflight` is the startup "is everything installed?" check. Each screen in the app is one file under `UI/panes/`. |
| `Features/` | The workflows — neuron counting, ganglia segmentation, image tools. The "verbs". |
| `Analysis/` | Number-crunching — spatial analysis, calcium traces, cell counts. |
| `services/` | Standalone helpers — merging CSV files, multiplex image registration. |

## Mental model

You click a button (`UI/`) → it runs a workflow (`Features/`) → which does the
maths (`Analysis/`) and calls Fiji plugins under the hood. One folder for looks,
one for actions, one for maths, one for utilities.

## Two things worth knowing

- **Models are not part of the build.** `pom.xml` never references the
  StarDist / ganglia model files. They are checked at runtime by
  `UI/Preflight.java` in Fiji's `models/` folder, and are delivered through the
  GAT update site — not compiled into the plugin.
- **Dependencies come from Fiji update sites**, not from GAT. The plugin calls
  StarDist, DeepImageJ, MorphoLibJ, CLIJ, etc. at runtime; those must be enabled
  as update sites (see the README install section).
