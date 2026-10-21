# Evidence

Everything in this folder was produced by [REA](https://rea.tools) from the
shipped CapCut 15.3.0 package (`com.lemon.lvoverseas`).

| File | What it is |
|---|---|
| `rea-session-bundle.json` | A REA session bundle exported with the MCP `export_evidence_bundle` tool. |
| `canonical-evidence-bundle.json` | The same bundle re-validated and re-written with `rea evidence-export` (canonical form). |
| `inspect_android_package.json` | The raw Evidence record of the APK inspection: package identity, 140 328 classes, 19 425 resources, 37 permissions, decoded manifest. Evidence id `ev_fb8cf181d6d6dd926e5d2db5ebfea8c935a6c86e0b4cf92732de8150c2f011f0`. |
| `rea-native-libcccreator.json` | Ghidra inventory of `libcccreator.so` (arm64, the `vesdk` editor engine): 173 106 procedures, 72 207 strings. Evidence id `ev_8a7dfd6c9c56f7fe82337…`. |
| `rea-native-libmiddleware.json` | Ghidra inventory of `libmiddleware.so` (arm64, the `painter` render graph): 47 612 procedures, 62 001 strings. Evidence id `ev_673726eecdbaa141fbecd…`. |
| `rea-native-libclipflow.json` | Ghidra inventory of `libclipflow.so` (arm64): 10 464 procedures, 10 670 strings, 28 segments. Evidence id `ev_373623b934509496810aa86984a98eaf05c0c06a05edb801408ed71a8c1f7b57`. |
| `capcut-editor-modules.txt` | The 2 080 `*Module` class names of `com.vega.middlebridge.swig` — the operation surface of the native editor engine. |
| `capcut-draft-schema.json` | The JSON schema of the 145 kotlinx-serialisation classes in `com.vega.draft.data.template` — the project file format. |

## Reproducing

```bash
# package identity, manifest and permissions
rea inspect-android-package base.apk --format json

# native inventory of one library
rea analyze lib/arm64-v8a/libclipflow.so --provider ghidra --snapshot clipflow.gdb --format json

# validate an exported bundle
rea evidence-export rea-session-bundle.json canonical-evidence-bundle.json --overwrite
```

The APK used here is `base.apk` from the `com.lemon.lvoverseas_15.3.0-15300200`
APKMirror bundle, SHA-256
`481e6dc971f422439cf18665f0c5d8a78b0c56b2cf44879b78327c54a0ac55b6`.

## Limits

These are static observations of a shipped artefact. The package was never
executed, no signature was verified, and reflective or dynamically loaded call
sites are not covered by the recovered references.
