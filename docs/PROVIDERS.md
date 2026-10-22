# Content providers

OpenCut works with no provider at all. Everything the editor ships with —
seven colour LUTs, five effects, the cross-fade transition and the sticker shapes
— is generated at runtime from code, so the APK contains no downloadable media.

A provider is an optional extra: a way to add *more* filters, effects, stickers,
fonts or sounds from a catalogue you control.

## The interface

`core/providers/AssetProvider.kt`:

```kotlin
interface AssetProvider {
    val id: String
    val displayName: String
    val worksOffline: Boolean
    suspend fun catalogue(): List<RemoteAsset>
    suspend fun download(asset: RemoteAsset, destination: File): File
}
```

Two implementations exist:

| Class | Transport | Notes |
|---|---|---|
| `LocalAssetProvider` | none | the built-in content, always available |
| `HttpAssetProvider` | HTTPS | reads a JSON catalogue from a base URL you supply |

Select the active provider at start-up:

```kotlin
AssetProviders.use(AssetProviders.custom("https://example.org/opencut"))
```

Reset to the offline catalogue with `AssetProviders.reset()`.

## Catalogue format

`GET {baseUrl}/catalogue.json` must return a JSON array:

```json
[
  {
    "id": "lut.amber",
    "name": "Amber",
    "kind": "FILTER",
    "url": "https://example.org/opencut/assets/amber.cube",
    "previewUrl": "https://example.org/opencut/previews/amber.jpg",
    "bytes": 401408,
    "license": "CC-BY-4.0",
    "author": "Example Studio"
  }
]
```

Fields:

| Field | Required | Meaning |
|---|---|---|
| `id` | yes | stable identifier, unique inside the catalogue |
| `name` | yes | label shown in the UI |
| `kind` | yes | `FILTER`, `EFFECT`, `STICKER`, `TRANSITION`, `FONT`, `SOUND`, `TEMPLATE` |
| `url` | for downloads | absolute URL of the payload |
| `previewUrl` | no | thumbnail shown before downloading |
| `bytes` | no | exact size; when set, the download is rejected on mismatch |
| `license` | **yes** | licence of the asset, e.g. `CC0-1.0`, `CC-BY-4.0` |
| `author` | no | attribution string |

## The licence rule

`RemoteAsset.isRedistributable` is true only when `license` is not blank.
`HttpAssetProvider.catalogue()` filters out everything else, and `download()`
throws `AssetProviderException` for an unlicensed asset. This is deliberate: an
open-source editor must not become a distribution channel for artwork that nobody
is allowed to ship.

## What this is not

This interface is **not** a client for a proprietary service. The repository:

* contains no third party host name, API key, signature algorithm or CDN path;
* does not call the effect, filter, template or music services of a commercial
  editor;
* does not download or embed artwork, fonts, sounds or templates that belong to
  somebody else.

Those services are governed by their own terms, which do not permit third-party
clients to consume them, and their content is proprietary. Point
`HttpAssetProvider` at a server you operate, serving assets you made or licensed,
and the editor will use them like any other catalogue.
