#!/usr/bin/env python3
"""Generate the Koda brand SVG masters and matching Android VectorDrawables."""
from pathlib import Path
from xml.sax.saxutils import escape

ROOT = Path(__file__).resolve().parents[2]
BRAND = ROOT / "assets/brand"
DRAWABLE = ROOT / "app-android/src/main/res/drawable"
MIP26 = ROOT / "app-android/src/main/res/mipmap-anydpi-v26"
MIP33 = ROOT / "app-android/src/main/res/mipmap-anydpi-v33"

for directory in (BRAND, DRAWABLE, MIP26, MIP33, DRAWABLE.parent / "values"):
    directory.mkdir(parents=True, exist_ok=True)

C = {
    "indigo": "#4338CA", "teal": "#0F766E", "mint": "#2DD4BF",
    "amber": "#FBBF24", "ink": "#1E1B4B", "cream": "#F8FAFC",
    "white": "#FFFFFF", "coral": "#FB7185", "gold": "#F59E0B",
    "lavender": "#A78BFA", "green": "#22C55E",
}

# Shared Koda geometry. SVG masters and Android vectors are emitted from these same paths.
TAIL = "M 82,87 C 92,81 101,85 110,92 L 122,101 L 111,107 L 122,115 C 109,119 99,115 89,109 L 77,103 Z"
TAIL_CHEVRON = "M 104,95 L 113,102 L 106,106 L 114,113"
WAVE_ARM = "M 96,88 C 104,81 108,71 108,61 L 108,50 C 108,45 111,42 115,43 C 119,44 120,48 119,53 L 118,71 C 117,84 111,94 101,100 Z"
WAVE_HAND = "M 108,49 C 105,47 104,43 106,40 C 108,37 112,38 114,41 C 114,37 118,36 121,39 C 123,42 121,46 119,48 C 122,47 125,49 125,52 C 124,56 120,57 116,56 L 112,55 C 110,54 108,52 108,49 Z"
LEFT_ARM = "M 38,83 C 31,78 27,69 28,60 L 29,52 C 30,47 27,44 23,45 C 19,46 18,50 19,55 L 20,70 C 21,82 27,91 37,97 Z"
LEFT_HAND = "M 29,51 C 32,49 33,45 31,42 C 29,39 25,40 23,43 C 23,39 19,38 16,41 C 14,44 16,48 18,50 C 15,49 12,51 12,54 C 13,58 17,59 21,58 L 25,57 C 27,56 29,54 29,51 Z"
HEAD = "M 31,46 L 24,14 Q 23,9 28,12 L 49,29 C 58,25 71,25 80,29 L 101,12 Q 106,9 105,15 L 99,46 C 107,54 111,65 110,77 C 108,94 94,104 64,106 C 34,104 20,94 18,77 C 17,65 23,53 31,46 Z"
EAR_L = "M 29,20 L 43,31 L 36,40 Q 32,34 29,20 Z"
EAR_R = "M 100,20 L 85,31 L 93,40 Q 97,34 100,20 Z"
MASK = "M 64,35 C 57,40 53,47 53,56 C 43,58 37,64 38,75 C 38,87 48,95 64,97 C 80,95 90,87 90,75 C 91,64 84,58 75,56 C 75,47 70,40 64,35 Z"
EYE_L = "M 45,56 C 45,52 48,50 51,51 C 55,52 56,57 55,62 C 54,67 51,69 48,67 C 45,65 44,60 45,56 Z"
EYE_R = "M 73,56 C 73,52 76,50 79,51 C 83,52 84,57 83,62 C 82,67 79,69 76,67 C 73,65 72,60 73,56 Z"
PUPIL_L = "M 49,55 C 51,53 53,55 53,59 C 53,62 52,64 50,64 C 48,64 47,61 47,59 C 47,57 48,56 49,55 Z"
PUPIL_R = "M 77,55 C 79,53 81,55 81,59 C 81,62 80,64 78,64 C 76,64 75,61 75,59 C 75,57 76,56 77,55 Z"
NOSE = "M 59,72 C 60,69 68,69 69,72 C 68,76 66,77 64,77 C 62,77 60,76 59,72 Z"
SMILE = "M 57,80 Q 64,87 71,80"
BLUSH_L = "M 40,75 C 42,72 47,72 49,75 C 47,78 42,78 40,75 Z"
BLUSH_R = "M 79,75 C 81,72 86,72 88,75 C 86,78 81,78 79,75 Z"
CLOSED_EYE_L = "M 45,60 Q 50,65 55,60"
CLOSED_EYE_R = "M 73,60 Q 78,65 83,60"
JOY_MOUTH = "M 56,77 Q 64,73 72,77 C 71,85 68,88 64,88 C 60,88 57,85 56,77 Z"
TONGUE = "M 60,83 Q 64,80 68,83 Q 67,87 64,87 Q 61,87 60,83 Z"

# A compact open-arm leap, with feet and amber trails kept inside the same 128 px artboard.
FLY_BODY = "M 43,82 C 43,94 48,108 64,113 C 80,108 85,94 85,82 Z"
FLY_ARM_L = "M 39,83 C 30,81 23,75 19,65 L 11,48 C 9,43 12,39 16,40 C 20,41 22,46 24,51 L 31,65 C 34,68 39,70 46,72 Z"
FLY_ARM_R = "M 89,83 C 98,81 105,75 109,65 L 117,48 C 119,43 116,39 112,40 C 108,41 106,46 104,51 L 97,65 C 94,68 89,70 82,72 Z"
FLY_HAND_L = "M 11,49 C 8,46 5,42 7,38 C 9,35 13,37 16,40 C 15,35 19,33 22,36 C 24,39 22,43 20,46 L 17,51 Z"
FLY_HAND_R = "M 117,49 C 120,46 123,42 121,38 C 119,35 115,37 112,40 C 113,35 109,33 106,36 C 104,39 106,43 108,46 L 111,51 Z"
FLY_FOOT_L = "M 44,105 C 44,100 49,98 54,101 L 61,108 C 64,112 61,118 56,118 L 48,116 C 44,114 42,110 44,105 Z"
FLY_FOOT_R = "M 84,105 C 84,100 79,98 74,101 L 67,108 C 64,112 67,118 72,118 L 80,116 C 84,114 86,110 84,105 Z"
FLY_SWISH_L = "M 16,91 C 9,94 7,99 9,104 M 21,103 C 16,108 16,113 20,116"
FLY_SWISH_R = "M 112,91 C 119,94 121,99 119,104 M 107,103 C 112,108 112,113 108,116"
THINK_ARM = "M 98,97 C 102,92 105,88 107,82 L 109,76 C 110,72 114,71 117,73 C 120,75 119,79 117,83 L 114,92 C 111,101 106,106 99,109 Z"
THINK_PAW = "M 90,88 C 88,85 90,81 94,80 C 96,76 102,76 104,81 C 108,83 108,88 105,91 C 103,96 98,98 94,95 C 91,94 89,91 90,88 Z"
THINK_BUBBLE = "M 3,6 C 3,2 7,0 13,1 C 20,2 24,7 24,13 C 24,19 20,23 14,23 L 10,23 L 6,28 L 7,22 C 4,20 2,16 2,12 Z"
QUESTION_MARK = "M 8,9 C 8,6 11,5 14,6 C 18,7 19,11 16,14 C 14,16 11,17 11,20 M 11,23 L 11,24"


def p(d, fill, stroke=None, sw=0):
    return {"d": d, "fill": C.get(fill, fill), "stroke": C.get(stroke, stroke) if stroke else None, "sw": sw}


def line(d, color, sw):
    return {"d": d, "fill": "none", "stroke": C[color], "sw": sw}


def mascot_layers(expression="open", arms="wave", tail=True, extra=()):
    layers = []
    if tail:
        layers += [p(TAIL, "amber", "ink", 4), line(TAIL_CHEVRON, "ink", 3.4)]
    if arms == "wave":
        layers += [p(WAVE_ARM, "teal", "ink", 4), p(WAVE_HAND, "mint", "ink", 3.5)]
    elif arms == "celebrate":
        layers += [p(LEFT_ARM, "teal", "ink", 4), p(LEFT_HAND, "mint", "ink", 3.5),
                   p(WAVE_ARM, "teal", "ink", 4), p(WAVE_HAND, "mint", "ink", 3.5)]
    elif arms == "fly":
        layers += [line(FLY_SWISH_L, "amber", 4), line(FLY_SWISH_R, "amber", 4),
                   p(FLY_FOOT_L, "mint", "ink", 3.5), p(FLY_FOOT_R, "mint", "ink", 3.5),
                   p(FLY_BODY, "teal", "ink", 4),
                   p(FLY_ARM_L, "teal", "ink", 4), p(FLY_HAND_L, "mint", "ink", 3.5),
                   p(FLY_ARM_R, "teal", "ink", 4), p(FLY_HAND_R, "mint", "ink", 3.5)]
    elif arms == "think":
        layers += [p(THINK_ARM, "teal", "ink", 4)]
    # Silhouette and ear insets
    layers += [p(HEAD, "teal", "ink", 4.5), p(EAR_L, "amber"), p(EAR_R, "mint")]
    layers += [p(MASK, "cream")]
    if expression == "open":
        layers += [p(EYE_L, "white"), p(EYE_R, "white"), p(PUPIL_L, "ink"), p(PUPIL_R, "ink"),
                   p(BLUSH_L, "coral"), p(BLUSH_R, "coral"), p(NOSE, "ink"), line(SMILE, "ink", 2.7)]
    else:
        layers += [line(CLOSED_EYE_L, "ink", 3.3), line(CLOSED_EYE_R, "ink", 3.3),
                   p(BLUSH_L, "coral"), p(BLUSH_R, "coral"), p(JOY_MOUTH, "ink"), p(TONGUE, "coral")]
    layers.extend(extra)
    if arms == "think":
        # Paw and question bubble sit in front of the face to make the guide pose clear.
        layers += [p(THINK_PAW, "mint", "ink", 3), p(THINK_BUBBLE, "white", "indigo", 2.8),
                   line(QUESTION_MARK, "indigo", 2.8)]
    return layers


ASSETS = {}
ASSETS["mascot_wave"] = mascot_layers()
ASSETS["mascot_celebrate"] = mascot_layers("joy", "celebrate", True, [
    p("M 11.5,24 L 13.4,28.6 L 18,30 L 13.4,31.4 L 11.5,36 L 10,31.4 L 5,30 L 10,28.6 Z", "amber", "ink", 1.6),
])
ASSETS["mascot_fly"] = mascot_layers("joy", "fly")
ASSETS["mascot_think"] = mascot_layers("open", "think")
# Review illustration: stacked lesson cards sit behind Koda; the check badge is foremost.
ASSETS["review_done"] = [
    p("M 11,77 Q 11,72 16,72 L 77,72 Q 82,72 82,77 L 82,112 Q 82,117 77,117 L 16,117 Q 11,117 11,112 Z", "white", "indigo", 3.5),
    line("M 22,87 L 56,87 M 22,97 L 45,97 M 22,107 L 49,107", "lavender", 4),
] + mascot_layers("joy", "wave", True) + [
    p("M 100,91 C 110,91 118,99 118,109 C 118,119 110,126 100,126 C 90,126 82,119 82,109 C 82,99 90,91 100,91 Z", "green", "ink", 3.5),
    line("M 91,108 L 98,115 L 110,101", "white", 4.5),
]
# Compact, high-contrast icon for the review shortcut at small UI sizes.
ASSETS["review_cards"] = [
    p("M 44,15 Q 44,11 49,11 L 102,11 Q 107,11 107,16 L 107,73 Q 107,78 102,78 L 49,78 Q 44,78 44,73 Z", "lavender", "indigo", 5.5),
    p("M 29,27 Q 29,22 34,22 L 89,22 Q 94,22 94,27 L 94,86 Q 94,91 89,91 L 34,91 Q 29,91 29,86 Z", "mint", "indigo", 5.5),
    p("M 13,40 Q 13,35 18,35 L 75,35 Q 80,35 80,40 L 80,101 Q 80,106 75,106 L 18,106 Q 13,106 13,101 Z", "white", "indigo", 5.5),
    line("M 25,52 L 65,52 M 25,64 L 57,64 M 25,76 L 49,76", "indigo", 6),
    p("M 88,78 C 101,78 112,89 112,102 C 112,115 101,125 88,125 C 75,125 65,115 65,102 C 65,89 75,78 88,78 Z", "green", "indigo", 4.5),
    line("M 77,101 L 85,109 L 100,93", "white", 6),
]
ASSETS["streak_flame"] = [
    p("M 43,34 C 39,48 39,61 47,70 C 44,56 54,49 59,43 C 60,55 70,57 69,69 C 76,63 79,55 77,47 C 92,59 97,70 95,83 C 93,103 80,116 63,118 C 42,120 26,105 27,84 C 28,69 34,51 43,34 Z", "coral", "ink", 5),
    p("M 63,70 C 60,80 51,85 52,96 C 53,106 59,111 66,110 C 77,108 81,97 76,88 C 74,84 69,80 68,74 C 67,80 64,83 62,85 C 63,79 65,75 63,70 Z", "amber"),
]
ASSETS["xp_spark"] = [
    p("M 64,8 C 69,37 76,48 104,54 C 76,60 69,71 64,100 C 59,71 52,60 24,54 C 52,48 59,37 64,8 Z", "amber", "ink", 4.5),
    p("M 101,79 C 103,89 106,92 116,94 C 106,96 103,100 101,110 C 99,100 96,96 86,94 C 96,92 99,89 101,79 Z", "mint", "indigo", 2.5),
]
ASSETS["lesson_trophy"] = [
    p("M 40,25 L 88,25 L 84,58 C 82,72 74,80 64,80 C 54,80 46,72 44,58 Z", "amber", "ink", 4.5),
    p("M 40,32 L 26,32 L 26,43 C 26,55 33,62 46,63", "indigo", "ink", 4.5),
    p("M 88,32 L 102,32 L 102,43 C 102,55 95,62 82,63", "indigo", "ink", 4.5),
    p("M 57,79 L 71,79 L 72,94 L 87,94 L 87,104 L 41,104 L 41,94 L 56,94 Z", "lavender", "ink", 4.5),
    p("M 64,35 L 68,45 L 79,46 L 71,53 L 73,64 L 64,58 L 55,64 L 57,53 L 49,46 L 60,45 Z", "cream", "indigo", 2.2),
]


def svg_path(layer):
    attrs = [f'd="{escape(layer["d"])}"', f'fill="{layer["fill"]}"']
    if layer.get("evenodd"):
        attrs.append('fill-rule="evenodd"')
    if layer["stroke"]:
        attrs += [f'stroke="{layer["stroke"]}"', f'stroke-width="{layer["sw"]}"', 'stroke-linecap="round"', 'stroke-linejoin="round"']
    return "    <path " + " ".join(attrs) + "/>"


def android_path(layer):
    fill = "#00000000" if layer["fill"] == "none" else layer["fill"]
    attrs = [f'android:pathData="{escape(layer["d"])}"', f'android:fillColor="{fill}"']
    if layer.get("evenodd"):
        attrs.append('android:fillType="evenOdd"')
    if layer["stroke"]:
        attrs += [f'android:strokeColor="{layer["stroke"]}"', f'android:strokeWidth="{layer["sw"]}"', 'android:strokeLineCap="round"', 'android:strokeLineJoin="round"']
    return "    <path " + " ".join(attrs) + "/>"


def write_svg(name, layers, width=128, background=None, transform=None, viewbox=None, background_rect=None):
    viewbox = viewbox or f"0 0 {width} {width}"
    prefix = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{width}" viewBox="{viewbox}">', f'  <title>{name.replace("_", " ").title()} — Koda, Kotlin learning companion</title>']
    if background_rect:
        x, y, w, h, radius = background_rect
        prefix.append(f'  <rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{radius}" fill="{background}"/>')
    elif background:
        prefix.append(f'  <rect width="{width}" height="{width}" rx="25" fill="{background}"/>')
    if transform:
        prefix.append(f'  <g transform="{transform}">')
    prefix.extend(svg_path(l) for l in layers)
    if transform:
        prefix.append('  </g>')
    prefix.append('</svg>')
    (BRAND / f"{name}.svg").write_text("\n".join(prefix) + "\n", encoding="utf-8")


def write_vector(name, layers, width=128, height=None, group=None):
    height = height or width
    lines = [f'<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="{width}dp" android:height="{height}dp" android:viewportWidth="{width}" android:viewportHeight="{height}">']
    if group:
        lines.append(f'  <group android:scaleX="{group[0]}" android:scaleY="{group[1]}" android:translateX="{group[2]}" android:translateY="{group[3]}">')
    lines.extend(android_path(l) for l in layers)
    if group:
        lines.append('  </group>')
    lines.append('</vector>')
    (DRAWABLE / f"{name}.xml").write_text("\n".join(lines) + "\n", encoding="utf-8")


# Illustrations: transparent, viewBox 128x128.
for name, layers in ASSETS.items():
    write_svg(name, layers)
    write_vector("brand_" + name, layers)


def write_preview():
    """Write a vector contact sheet from the same paths used for production artwork."""
    width, height = 1280, 844
    card_width, card_height, gap = 296, 232, 16
    x_margin, first_y, row_gap = 24, 88, 24
    names = ["app_icon", *ASSETS.keys()]
    parts = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}">',
        '<rect width="100%" height="100%" fill="#F1F5F9"/>',
        '<text x="32" y="42" fill="#1E1B4B" font-family="system-ui, sans-serif" font-size="28" font-weight="700">KODA / Kotlin learning companion</text>',
        '<text x="32" y="66" fill="#475569" font-family="system-ui, sans-serif" font-size="14">A playful trail guide for learning Kotlin, built from original vector artwork.</text>',
    ]
    for index, name in enumerate(names):
        column, row = index % 4, index // 4
        x = x_margin + column * (card_width + gap)
        y = first_y + row * (card_height + row_gap)
        parts.append(f'<rect x="{x}" y="{y}" width="{card_width}" height="{card_height}" rx="24" fill="#FFFFFF"/>')
        if name == "app_icon":
            parts.append(f'<rect x="{x + 84}" y="{y + 13}" width="128" height="128" rx="28" fill="{C["indigo"]}"/>')
            parts.append(f'<g transform="translate({x + 84} {y + 13})">')
            parts.extend(svg_path(layer) for layer in ICON_CORE)
            parts.append('</g>')
        else:
            parts.append(f'<g transform="translate({x + 64} {y + 8}) scale(1.25)">')
            parts.extend(svg_path(layer) for layer in ASSETS[name])
            parts.append('</g>')
        parts.append(
            f'<text x="{x + 18}" y="{y + card_height - 20}" fill="#1E1B4B" '
            f'font-family="system-ui, sans-serif" font-size="20" font-weight="600">{name.replace("_", " ")}</text>'
        )
    parts.append('</svg>')
    (BRAND / "preview.svg").write_text("\n".join(parts) + "\n", encoding="utf-8")

# Source and Android launcher foreground use Koda's compact core inside the adaptive safe zone.
ICON_CORE = [p(TAIL, "amber", "ink", 4), line(TAIL_CHEVRON, "ink", 3.4),
             p(HEAD, "teal", "ink", 4.5), p(EAR_L, "amber"), p(EAR_R, "mint"), p(MASK, "cream"),
             p(EYE_L, "white"), p(EYE_R, "white"), p(PUPIL_L, "ink"), p(PUPIL_R, "ink"),
             p(BLUSH_L, "coral"), p(BLUSH_R, "coral"), p(NOSE, "ink"), line(SMILE, "ink", 2.7)]
write_preview()
write_svg("app_icon_foreground", ICON_CORE, width=108, transform="translate(23.44 25.44) scale(0.44)")
write_vector("ic_launcher_foreground", ICON_CORE, width=108, height=108, group=(0.44, 0.44, 23.44, 25.44))
write_svg("app_icon", ICON_CORE, width=72, background=C["indigo"], transform="translate(23.44 25.44) scale(0.44)", viewbox="18 18 72 72", background_rect=(18, 18, 72, 72, 14))

# A cohesive single-color silhouette with face cutouts for themed monochrome launchers.
MONO = [
    {"d": TAIL, "fill": "#FFFFFFFF", "stroke": None, "sw": 0},
    # Even-odd inner contours form transparent eye and nose cutouts in the head silhouette.
    {"d": " ".join((HEAD, EYE_L, EYE_R, NOSE)), "fill": "#FFFFFFFF", "stroke": None, "sw": 0, "evenodd": True},
]
write_svg("app_icon_monochrome", MONO, width=108, transform="translate(23.44 25.44) scale(0.44)")
write_vector("ic_launcher_monochrome", MONO, width=108, height=108, group=(0.44, 0.44, 23.44, 25.44))

# Palette resource used by the adaptive icon background.
(DRAWABLE.parent / "values" / "brand_colors.xml").write_text('''<resources>\n    <color name="brand_launcher_background">#4338CA</color>\n    <color name="brand_indigo">#4338CA</color>\n    <color name="brand_teal">#0F766E</color>\n    <color name="brand_mint">#2DD4BF</color>\n    <color name="brand_amber">#FBBF24</color>\n    <color name="brand_ink">#1E1B4B</color>\n    <color name="brand_cream">#F8FAFC</color>\n</resources>\n''', encoding="utf-8")

adaptive = '''<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n    <background android:drawable="@color/brand_launcher_background" />\n    <foreground android:drawable="@drawable/ic_launcher_foreground" />\n</adaptive-icon>\n'''
(MIP26 / "ic_launcher.xml").write_text(adaptive, encoding="utf-8")
(MIP26 / "ic_launcher_round.xml").write_text(adaptive, encoding="utf-8")
(MIP33 / "ic_launcher.xml").write_text('''<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n    <background android:drawable="@color/brand_launcher_background" />\n    <foreground android:drawable="@drawable/ic_launcher_foreground" />\n    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />\n</adaptive-icon>\n''', encoding="utf-8")
(MIP33 / "ic_launcher_round.xml").write_text((MIP33 / "ic_launcher.xml").read_text(encoding="utf-8"), encoding="utf-8")
