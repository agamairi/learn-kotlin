# Stitch screen designs

Google Stitch generated these four mobile screens through its MCP service using Gemini 3.8 Flash on 2026-10-25. They share the warm minimal design system in private project `4275928902607641859`.

| Screen | Stitch screen ID | Export | Native implementation |
| --- | --- | --- | --- |
| Home | `9ec0e353cf3643da868e4fe6d7f28415` | [image](home.png), [HTML](home.html) | `ui/home/HomeScreen.kt` |
| Review | `2d4a84b1f42544608803e9a6993109bb` | [image](review.png), [HTML](review.html) | `ui/review/ReviewScreen.kt` |
| Stats | `043b725c7b7f4bea889d683f69aa9ee7` | [image](stats.png), [HTML](stats.html) | `ui/stats/` |
| Settings | `b0e1edc743b74dbbba1101229cbf227c` | [image](settings.png), [HTML](settings.html) | `ui/settings/SettingsScreen.kt` |

The native Compose screens implement the generated hierarchy and grouping. Home separates Koda's check-in from the next lesson, daily goal, and review entry. Review shows due cards and the five-box distribution. Stats groups actual XP history, streak consistency, and chapter results. Settings uses grouped controls and adaptive goal tiles. The existing quiz layout and approved Koda vectors remain; a clay-coloured introduction precedes the new lesson mistake round.

These exports contain illustrative sample data. The app uses actual saved data, has no fictional user profile or estimated lesson duration, and uses the real 1/2/4/8/16-day review schedule. It preserves the existing AI-provider disclosure and all settings actions. The generated placeholder fox is replaced with the approved Koda assets. Credential-like mock field values were removed from the exported HTML; the Stitch service credential is never included in these references or the app.

HTML is a design reference, not a web view embedded in the app. Production screens use theme colours, native controls, local assets, accessibility semantics, and the existing reduced-motion preference.
