# CapCut 15.3.0 — reverse engineering notes

This document records what was learned from the shipped **CapCut – Video Editor
15.3.0** package (`com.lemon.lvoverseas`, versionCode `15300200`) using
[REA](https://rea.tools) and the artefacts it produced.

> **Clean-room statement.** Nothing in this repository is derived from CapCut's
> source, resources, artwork, fonts or strings. The analysis below was used to
> understand *what a mobile video editor of this class does* and to design an
> independent data model and engine. All code in `app/src/main/java/com/zaitxcode/opencut/`
> was written from scratch. No CapCut asset is redistributed.

---

## 1. How the target was obtained and analysed

| Step | Tool | Result |
|---|---|---|
| Fetch the package | `apkmirror.com` → `.apkm` bundle | `base.apk` + 34 splits, 266 MB |
| Package identity, manifest, permissions | REA `inspect_android_package` (JADX 0.7.1) | `ev_fb8cf181d6d6dd926e5d2db5ebfea8c935a6c86e0b4cf92732de8150c2f011f0` |
| Class inventory | REA `search_android_classes` + local JADX 1.5.2 | 140 328 classes, 19 425 resources |
| Native engine | REA `analyze` (Ghidra 12.1.4) | `libcccreator.so`, `libmiddleware.so`, `libmiddle-bridge.so`, `libclipflow.so` |
| Project format | kotlinx-serialiser metadata in the decompiled DEX | 145 schema classes |

Target digest: `base.apk` SHA-256 `481e6dc971f422439cf18665f0c5d8a78b0c56b2cf44879b78327c54a0ac55b6`.

### 1.1 Package facts (observed)

```
package_name   com.lemon.lvoverseas
version_name   15.3.0
version_code   15300200
min_sdk        21
target_sdk     34
class_count    140328
resource_count 19425
permissions    37 (media, camera, mic, notifications, ads, billing, …)
```

The manifest declares **440 activities** and **97 services / providers /
receivers**. `com.vega.main.MainActivity` is the launcher; the editor is
`com.vega.edit.editpage.activity.EditActivity`.

---

## 2. Architecture

CapCut is not a monolith of Java code. It is a thin Kotlin/Java shell around a
native editing engine, wrapped by a code-generated bridge.

```
┌──────────────────────────────────────────────────────────────┐
│ Kotlin UI shell            com.vega.edit.editpage            │
│  ├─ BaseEditActivity                                        │
│  ├─ BaseEditPreviewFragment      (preview canvas)           │
│  ├─ BaseEditPlayerControlFragment (transport controls)       │
│  └─ BaseEditDockFragment          (timeline)                │
│  ViewModels: Edit / Preview / Track / Panel / Player / TopBar│
├──────────────────────────────────────────────────────────────┤
│ Feature packages (48 709 files under com.vega)               │
│  edit.adjust · edit.filter · edit.audio · edit.mask · …      │
├──────────────────────────────────────────────────────────────┤
│ Bridge    com.vega.middlebridge.*   12 199 files             │
│  ├─ swig/  11 700 SWIG mirrors of the native `lvve` model    │
│  ├─ client/ Composition, Audio, Canvas, Draft, Template …    │
│  └─ engine/Engine.init(String) — one native entry point      │
├──────────────────────────────────────────────────────────────┤
│ Native   libcccreator.so      `vesdk`   editor + compositor  │
│          libmiddleware.so     `painter` render graph          │
│          libmiddle-bridge.so  `lvve`    editor middleware     │
│          libclipflow.so       `clipflow` node-graph workflows │
│          libfast_engine.so    expression / animation engine  │
│          libquick.so          `LEPUS` script runtime         │
│          liblens.so           real-time effect framework     │
└──────────────────────────────────────────────────────────────┘
```

### 2.1 The native editor engine (`vesdk`)

`libcccreator.so` exports a C++ namespace `vesdk::pub` — the Video Editor SDK:

* `H264Mp4CompileSetting` — encoder configuration, CRF ratio.
* `SwingPostProcessFilter`, `StickerAnimationFilter` — effect primitives.
* `VECommonParam`, `VideoEffect`, `AudioEffect` — per-effect parameter blocks.
* `ILASDKJianyingJNI` — the JNI façade used by the Java layer.

### 2.2 The preview render graph (`painter`)

`libmiddleware.so` contains a node graph renderer:

* `painter::GraphBuilder::AddRenderFilterGraphNode(...)` with
  `RenderNodeDesc`, `GraphData`, `GraphResourceBuilder`.
* `IRenderNode`, `IRenderFilter` — the graph nodes.
* `retouch_sdk::*` — the retouch layer (`BokehCleaner`, `BeautyFaceHandler`,
  `ExpressionHandler`, `UVLiquefyFilterPlayback`, `XTLayerPlayback`).

So the preview is a **filter graph**, not a per-effect `if` chain: each layer
contributes nodes, and nodes are composed by the graph builder.

### 2.3 The editor middleware (`lvve`)

`libmiddle-bridge.so` carries `lvve::*` types — `MaterialCommonMask`,
`MaterialTextTemplate`, `ColorAdjustMaskParams`, `IntelligentTrackParams`,
`SmartColorAdjustEnable` — plus SWIG JNI bindings
(`Java_com_vega_middlebridge_swig_*`) that mirror them into Java.

### 2.4 Ghidra inventory of the arm64 libraries

| Library | Role | Procedures | Strings | Evidence id |
|---|---|---:|---:|---|
| `libcccreator.so` | `vesdk` editor + compositor | 173 106 | 72 207 | `ev_8a7dfd6c9c56f7fe82337…` |
| `libmiddleware.so` | `painter` render graph | 47 612 | 62 001 | `ev_673726eecdbaa141fbecd…` |
| `libclipflow.so` | `clipflow` node-graph workflows | 10 464 | 10 670 | `ev_373623b934509496810aa…` |

The procedure counts show where the complexity actually is: the editor SDK is an
order of magnitude larger than the clip-flow engine, and the render graph is close
behind — grading, effects and compositing dominate the native code base.

---

## 3. The editor operation surface

`com.vega.middlebridge.swig` contains **2 080 `*Module` classes**. Each module is
one operation of the editor engine. Grouping them by the operation they implement
gives the functional taxonomy of the editor:

| Domain | Modules | Examples |
|---|---:|---|
| Text | 335 | `AddMultiTextModule`, `AddTextTemplateModule`, `AddScriptVideoWordsModule`, `AddTagForWordsModule` |
| Track / clip | 287 | `AddCompositionModule`, `AddTextLayerModule`, `AddStickerLayerModule`, `AddSegmentAlgorithmsModule` |
| Mask / matting | 280 | `AddMattingModule`, `AddCustomMattingModule`, `AddCommonMaskModule`, `AddVideoTrackingListenerModule` |
| Ad / business | 195 | `AdComponentBatchSplitModule`, `AdComponentUpdateTimeRangeModule` |
| AI | 175 | `AddAigcSegmentModule`, `AddSingleSmartMotionKeyframesModule` |
| Template | 175 | `AddTemplateCombinationModule`, `AddBeatTemplateModule` |
| Animation / keyframes | 135 | `AddAnimationModule`, `AddCommonKeyframeModule`, `AddSingleLockedKeyFrameModule` |
| Audio | 113 | `AddAudioModule`, `AddAudioBeatModule`, `AddTtsPartModule`, `AlignMusicTimeRangeModule` |
| Player | 112 | `Preview*`, `Seek*`, `Cache*` |
| Media | 97 | `AddImageStickerModule`, `Import*` |
| Transform | 84 | `CropVideoModule`, `CanvasUpdateTransformModule`, `ApplySmartCropModule` |
| Colour filters | 60 | `AddGlobalFilterModule`, `BeautyFacePresetModule` |
| Export | 50 | `ExportCompositionToFileSyncModule`, `ExportAudioStartModule`, `DraftZipUploadModule` |
| Speed | 26 | `ChangeSegmentVideoSpeedModule`, `CurveSpeedUtilsModule`, `FreezeVideoModule` |
| Sticker | 24 | `AddStickerModule`, `GetStickerBoundingBoxRectModule` |

Verb distribution across the whole surface: `Get` 276, `Set` 208, `Update` 194,
`Add` 103, `Remove` 83, `Reset` 39, `Split` 34, `Replace` 31, `Move` 21,
`Apply` 19, `Convert` 15.

### 3.1 Editor UI structure

The screen is assembled from three parts, visible in the decompiled classes:

* **Dock** (`com.vega.edit.base.dock`) — the horizontally scrollable tool bar.
  `DockItem`, `DockItemGroup`, `DockGroupView`, `CommonDockManager`,
  `FlavorDockProvider`. Items are addressed by component name.
* **Panel** (`IPanel`, `FlavorPanelProvider`) — the sheet opened by a dock item.
* **Timeline** (`com.vega.edit.base.multitrack`) — `EditTrackAdapter`,
  `BaseTrackAdapter`, `BeatData`, `BaseTrackKeyframeItemView`.

The clip context menu actions (found as `Component("…")` literals) are:
`video_copy`, `video_delete`, `video_split`, `video_volume`, `video_adjust`,
`video_filter`, `video_palette`, `video_stable`, `video_eye_correction`,
`video_figure_beauty`, `video_figure_body`, `video_figure_hair`,
`single_function_video_quality_adjust`, `filter_*`, `high_light_*`.

---

## 4. The project (draft) format

`com.vega.draft.data.template` is a kotlinx.serialization model. The complete
schema of **145 classes** was extracted from the generated `$$serializer` classes.

### 4.1 Root document

```
Project
├── id, version, new_version, name, duration
├── create_time, update_time, color_space, fps
├── config            (video_mute, subtitle_recognition_id, …)
├── canvas_config     { ratio, width, height }
├── tracks[]          → Track
├── materials         → Materials
├── keyframes         → KeyFrames
├── platform, cover, relationships, extra_info, group_container, purchaseInfo
```

### 4.2 Track and segment

```
Track  { id, type, segments[], flag }

Segment (24 fields)
  id, source_timerange{start,duration}, target_timerange{start,duration},
  speed, is_tone_modify, mirror, reverse, intensifies_audio, cartoon,
  volume, last_nonzero_volume, clip{scale,rotation,transform,flip,alpha},
  material_id, extra_material_refs[], render_index, keyframe_refs[],
  enable_lut, enable_adjust, enable_color_curves, enable_color_wheels,
  visible, group_id, hdr_settings, template_id
```

The `source_timerange` / `target_timerange` split is the key idea: the first
describes *where in the media file* the content comes from, the second *where on the
timeline* it sits. Splitting, trimming and speed changes move them independently.

### 4.3 Materials

`Materials` groups assets by family:
`videos, audio_fades, beats, canvases, effects, video_effects, images, stickers,
tail_leaders, audio_effects, texts, transitions, material_animations, audios,
speeds, placeholders, masks, chromas, text_templates, realtime_denoises,
video_trackings, hsl, audio_balances, color_curves, drafts, primary_color_wheels,
log_color_wheels`.

Representative material shapes:

| Material | Fields |
|---|---|
| `MaterialVideo` | `duration, path, reverse_path, intensifies_path, width, height, crop{8 corners}, crop_ratio, crop_scale, has_audio, stable{stable_level,matrix_path}, matting{flag,path}, ai_matting, gameplay{path,algorithm}` |
| `MaterialText` | `content, font_name, font_path, font_size, letter_spacing, line_spacing, alignment, text_alpha, background_alpha, border_width, text/background/border/shadow color, typesetting, bold_width, italic_degree, underline, bloom_*` |
| `MaterialAudio` | `duration, path, wave_points, music_id, tone_type, intensifies_path` |
| `MaterialSticker` | `sticker_id, name, path, icon_url, preview_cover_url, unicode, category_id` |
| `MaterialTransition` | `name, effect_id, path, duration, is_overlap` |
| `MaterialEffect` | `effect_id, name, path, value, adjust_params[], apply_target_type, apply_time_range, render_index` |
| `MaterialSpeed` | `mode, speed, curve_speed{points[]}` |
| `MaterialChroma` | `color, intensity_value, shadow_value, path` |
| `MaterialVideoMask` | `resource_id, resource_type, path, config` |
| `MaterialPrimaryColorWheels` / `MaterialLogColorWheels` | `intensity, shadow, midtone, highlight, offset` |
| `MaterialColorCurves` | `luma, red, green, blue` (each a list of `{x,y}` points) |

### 4.4 Keyframes

`KeyFrames` holds one list per property family:
`videos, texts, audios, filters, adjusts, stickers`.

| Family | Animated properties |
|---|---|
| `VideoKeyFrame` | `position, scale, rotation, alpha, volume, filter_value, brightness/contrast/saturation/sharpen/highlight/shadow/temperature/tone/fade/light_sensation/vignetting/particle, lut_value, chroma_intensity, chroma_shadow, mask_config, key_frame_graph, figure_stretch/slim/zoom, color wheel intensities` |
| `TextKeyFrame` | `position, scale, rotation, border_width, text_alpha, background_alpha, shadow_alpha/angle/point/smoothing, border/text/background/shadow color, bloom_*` |
| `StickerKeyFrame` | `position, scale, rotation` |
| `AudioKeyFrame` | `volume` |
| `AdjustKeyFrame` | the colour grading values |
| `FilterKeyFrame` | `value` (filter strength) |

Every keyframe carries `time_offset` relative to its layer plus a `flags`/`type`
discriminator.

### 4.5 Canvas and clip geometry

`CanvasConfig { ratio, width, height }` — the composition surface.
`Clip { scale{x,y}, rotation, transform{x,y}, flip{horizontal,vertical}, alpha }`.

---

## 5. What was built from this

The analysis was used to design **OpenCut**, an independent open-source editor.
The mapping is one-to-one on *concepts*, never on code or assets.

| Observed in CapCut | Implemented in OpenCut |
|---|---|
| `Project` / `Tracks` / `Segments` | `com.zaitxcode.opencut.core.model.Project` / `Track` / `Segment` |
| `source_timerange` + `target_timerange` | `Segment.source` + `Segment.target` (`TimeRange`) |
| `Clip { scale, rotation, transform, flip, alpha }` | `com.zaitxcode.opencut.core.model.Clip` |
| `CanvasConfig { ratio, width, height }` | `com.zaitxcode.opencut.core.model.Canvas` |
| `KeyFrames` per property family | `KeyframeLibrary` with `KeyframeKind` |
| Colour grading value set | `Adjustment` (12 sliders) |
| `MaterialSpeed { mode, speed, curve_speed }` | `SpeedSpec` + `CurvePoint` |
| `MaterialChroma` | `ChromaSpec` |
| `MaterialVideoMask` / `MaskParam` | `MaskSpec` |
| `MaterialColorCurves` | `CurvesSpec` |
| `MaterialTextTemplate` / `MaterialText` | `MaterialKind.TEXT` with `content` |
| `MaterialTransition` | `TransitionSpec` |
| `painter::GraphBuilder` filter graph | `FrameComposer` (one draw pass per layer + grading uniforms) |
| `vesdk` H.264 export | `ExportEngine` (MediaCodec encoder + MediaMuxer) |
| Dock / Panel / Timeline split | `DockBar` / `panel/Panels.kt` / `TimelineView` |
| 2 080 engine modules | The editor operations in `core/editor/EditorStore.kt` |

### 5.1 Engine differences

OpenCut deliberately uses **platform components instead of a proprietary SDK**:

* preview and export share one OpenGL ES 2.0 renderer (`FrameComposer`), so the
  exported file matches the preview;
* video decoding is `MediaCodec` → `SurfaceTexture` → external OES texture;
* colour grading is a fragment shader with 14 uniforms (brightness, contrast,
  saturation, temperature, tone, highlight, shadow, fade, light sensation,
  vignette, grain, HSL, chroma, LUT slot);
* export is `MediaCodec` H.264 into a `MediaMuxer`, with an AAC audio pass that
  decodes, resamples and mixes every audio layer first.

---

## 6. Evidence and limitations

* All conclusions above are **static observations** of a shipped artefact. The APK
  was never executed; no runtime behaviour was measured.
* Manifest and class counts come from REA's JADX provider
  (`ev_fb8cf181d6d6dd926e5d2db5ebfea8c935a6c86e0b4cf92732de8150c2f011f0`).
  Signatures were not verified — the package identity is claimed by the store page
  and the manifest, not by a signature check.
* Incoming reference counts recovered by JADX cover direct `invoke` sites only;
  reflective and dynamic call sites remain unknown.
* The native Ghidra runs cover the shipped arm64 libraries; other ABIs were not
  analysed and may differ.
* Network behaviour, server APIs and account features were not analysed.
