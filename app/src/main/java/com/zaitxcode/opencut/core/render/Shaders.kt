package com.zaitxcode.opencut.core.render

/**
 * GLSL sources used by the preview and export renderers.
 *
 * The renderer uses two programs:
 *  - [TEXTURE_VERTEX] / [TEXTURE_FRAGMENT] draws a layer (video, image, text or
 *    sticker) with its transform, optional LUT and colour grading.
 *  - [COMPOSITE_FRAGMENT] blends the layer over the canvas with alpha.
 */
object Shaders {

    const val TEXTURE_VERTEX = """
attribute vec4 aPosition;
attribute vec4 aTexCoord;
uniform mat4 uMvpMatrix;
uniform mat4 uTexMatrix;
varying vec2 vTexCoord;
void main() {
    gl_Position = uMvpMatrix * aPosition;
    vTexCoord = (uTexMatrix * aTexCoord).xy;
}
"""

    const val TEXTURE_FRAGMENT = """
#extension GL_OES_EGL_image_external : require
precision mediump float;
varying vec2 vTexCoord;
uniform samplerExternalOES uTexture;
uniform sampler2D uLut;
uniform float uHasLut;
uniform float uLutIntensity;

uniform float uBrightness;
uniform float uContrast;
uniform float uSaturation;
uniform float uSharpen;
uniform float uHighlight;
uniform float uShadow;
uniform float uTemperature;
uniform float uTone;
uniform float uFade;
uniform float uLightSensation;
uniform float uVignette;
uniform float uGrain;
uniform float uHue;
uniform float uHslSaturation;
uniform float uHslLightness;
uniform float uChromaIntensity;
uniform vec3  uChromaColor;
uniform float uTime;

// The cube is stored as a 32x1024 strip: x = slice * 32 + red, y = green,
// so it can be sampled with a plain 2D texture on OpenGL ES 2.0.
const float LUT_SIZE = 32.0;

vec3 applyLut(vec3 color) {
    float slice = color.b * (LUT_SIZE - 1.0);
    float s0 = floor(slice);
    float s1 = min(s0 + 1.0, LUT_SIZE - 1.0);
    float f = slice - s0;
    float y = (color.g * (LUT_SIZE - 1.0) + 0.5) / LUT_SIZE;
    float x0 = (s0 * LUT_SIZE + color.r * (LUT_SIZE - 1.0) + 0.5) / (LUT_SIZE * LUT_SIZE);
    float x1 = (s1 * LUT_SIZE + color.r * (LUT_SIZE - 1.0) + 0.5) / (LUT_SIZE * LUT_SIZE);
    vec3 low = texture2D(uLut, vec2(x0, y)).rgb;
    vec3 high = texture2D(uLut, vec2(x1, y)).rgb;
    vec3 mapped = mix(low, high, f);
    return mix(color, mapped, uLutIntensity);
}

vec3 applyHsl(vec3 color) {
    float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
    vec3 offset = color - luma;
    offset.r *= 1.0 + uHue;
    offset.g *= 1.0 + uHue * 0.0;
    offset.b *= 1.0 - uHue;
    vec3 shifted = luma + offset;
    float gray = dot(shifted, vec3(0.2126, 0.7152, 0.0722));
    return mix(vec3(gray), shifted, 1.0 + uHslSaturation) + uHslLightness;
}

vec3 applyChroma(vec3 color) {
    float key = dot(color, uChromaColor);
    float mixAmount = smoothstep(0.25, 0.75, key) * uChromaIntensity;
    return mix(color, vec3(0.0), mixAmount);
}

void main() {
    vec4 texel = texture2D(uTexture, vTexCoord);
    vec3 color = texel.rgb;

    if (uHasLut > 0.5) {
        color = applyLut(color);
    }

    color = applyHsl(color);
    color = applyChroma(color);

    // Exposure / brightness
    color += uBrightness;

    // Contrast around mid grey
    color = (color - 0.5) * (1.0 + uContrast) + 0.5;

    // Saturation
    float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
    color = mix(vec3(luma), color, 1.0 + uSaturation);

    // Highlights / shadows
    float shadowMask = 1.0 - smoothstep(0.0, 0.5, luma);
    float highlightMask = smoothstep(0.5, 1.0, luma);
    color += uShadow * shadowMask + uHighlight * highlightMask;

    // Temperature (blue <-> amber) and tone (green <-> magenta)
    color.r += uTemperature * 0.15;
    color.b -= uTemperature * 0.15;
    color.g += uTone * 0.1;

    // Light sensation lifts the whole image, fade pulls it down
    color += uLightSensation * 0.2;
    color = mix(color, vec3(dot(color, vec3(0.299, 0.587, 0.114))), uFade);

    // Vignette
    vec2 centered = vTexCoord - 0.5;
    float vignette = 1.0 - dot(centered, centered) * uVignette * 2.0;
    color *= clamp(vignette, 0.0, 1.0);

    // Film grain
    float noise = fract(sin(dot(vTexCoord * uTime, vec2(12.9898, 78.233))) * 43758.5453);
    color += (noise - 0.5) * uGrain * 0.2;

    gl_FragColor = vec4(clamp(color, 0.0, 1.0), texel.a);
}
"""

    /**
     * Same shading as [TEXTURE_FRAGMENT], but for ordinary 2D textures.
     *
     * `samplerExternalOES` only accepts textures produced by a SurfaceTexture; images,
     * stickers and text layers are uploaded as `GL_TEXTURE_2D`, so they need a program
     * whose sampler has the matching type.
     */
    val TEXTURE_FRAGMENT_2D = TEXTURE_FRAGMENT
        .replace("#extension GL_OES_EGL_image_external : require\n", "")
        .replace("uniform samplerExternalOES uTexture;", "uniform sampler2D uTexture;")


    /** Composites a layer over the canvas with source-over alpha blending. */
    const val COMPOSITE_FRAGMENT = """
precision mediump float;
varying vec2 vTexCoord;
uniform samplerExternalOES uTexture;
uniform float uAlpha;
void main() {
    vec4 texel = texture2D(uTexture, vTexCoord);
    gl_FragColor = vec4(texel.rgb, texel.a * uAlpha);
}
"""

    /** Copies a texture into the framebuffer without any modification. */
    const val COPY_FRAGMENT = """
precision mediump float;
varying vec2 vTexCoord;
uniform samplerExternalOES uTexture;
void main() {
    gl_FragColor = texture2D(uTexture, vTexCoord);
}
"""

    /** Solid colour fill used for the canvas background. */
    const val SOLID_FRAGMENT = """
precision mediump float;
uniform vec4 uColor;
void main() {
    gl_FragColor = uColor;
}
"""
}
