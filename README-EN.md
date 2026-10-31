# TiktokPatchXposed

[Русский](README.md) | **English**

An LSPosed module for TikTok. It removes ads, lets you download anything in the quality you want,
turns on features TikTok rolls out only to some people, and adds many small things: auto-scroll
everywhere, your own color, font and AMOLED theme, hiding parts of the screen, country flags,
invisible mode and auto streaks. All settings live right inside TikTok.

Current version: **4.4.4**. Tested on **TikTok 47.5.3**, **47.4.3** and **47.1.4** (Google Play and universal APK),
**Android 16**. Most features find what they need in TikTok by themselves, so
they survive TikTok updates.

<details>
<summary><b>Screenshots</b></summary>

<p align="center">
  <img src="docs/media/main-en.png" width="250" alt="The module's main settings screen">
  <img src="docs/media/status-en.png" width="250" alt="Feature status">
  <img src="docs/media/interface-en.png" width="250" alt="The Interface section">
</p>
<p align="center">
  <img src="docs/media/hide-en.png" width="250" alt="Hide elements">
  <img src="docs/media/color-en.png" width="250" alt="Own accent color">
  <img src="docs/media/downloads-en.png" width="250" alt="Downloads and links">
</p>
</details>

## Installation

**You need:** Android 8.1 or newer, root and [LSPosed](https://github.com/JingMatrix/LSPosed)
(libxposed 101–102 or the old LSPosed 1.9.x), TikTok from Google Play (`com.zhiliaoapp.musically`).

1. Download the APK from the latest [release](https://github.com/hik0w/TiktokPatchXposed/releases/latest) and install it.
2. Enable the module in LSPosed and tick TikTok.
3. Force-stop TikTok and open it again.

**Settings:** TikTok → Profile → ☰ → Settings and privacy → **TiktokPatchXposed** (the first row).
The screen is translated into 13 languages (Arabic and Chinese included) and follows TikTok's language. When a setting needs a
restart, a "Restart" button appears at the bottom. To update, install the new APK over the old
one; settings are kept.

Features marked 🧪 work but are not fully tested. All new features are off by default.

<details>
<summary><b>All features</b></summary>

### Feed
- **No ads**: in the feed, at startup, inside videos and in other video lists.
- **Hide LIVE**: streams in the feed and the LIVE button.
- **Hide "People you may know"**: videos and whole cards with people from your contacts.
- **Hide paid content and products**, **hide AI videos**.
- **Only fresh videos**: hides recommendations older than the chosen period.
- **Hide videos I've seen**: the module remembers what you watched and leaves it out when TikTok sends it again.
- **Own filter**: by words, hashtags, authors, countries, minimum likes and views.

### Playback
- **Auto-scroll everywhere**: in search, following and chats; profiles and liked videos have their own switch in the
  long-press menu. Off everywhere by default; photo posts by music length.
- **Exact date and time**: on videos, stories, comments and reposts, with seconds if you like.
- **Exact numbers**: likes, comments, shares, views and profile counts in full.
- 🧪 **No like on double tap**: double-tapping a video no longer likes it.
- 🧪 **Picture-in-picture**: TikTok's own floating video window.
- **3× speed** in TikTok's own speed menu.
- **Screen refresh rate**: hold 60, 90 or 120 Hz; TikTok no longer lowers it to the video's frame rate.
- **Own speeds** and **remember speed**.
- **Resume where you left off**, **keep "Clear display"**.
- **Seek on every video**: on short ones the bar stays hidden, drag along the bottom edge of the video.
- 🧪 **Sound in muted videos**: brings back sound TikTok turned off over copyright, if the video still has it.
- **Quality for Wi-Fi and mobile data separately** (best, saver or TikTok's choice), 🧪 **H.264 video**,
  **no HDR**, 🧪 **photos in the best quality** (up to 8 MP).

### Interface
- **Own accent color**: a palette or HEX; recolors TikTok's red in buttons, icons, likes and your name.
- **Own font**: the system font or any `.ttf` / `.otf` from your phone instead of TikTok Sans.
- **AMOLED theme**: pure black, turns TikTok's dark theme on by itself.
- **Hide elements**: side buttons, the music disc, author and caption, the Tako button.
- **Interface opacity**, **OLED burn-in protection** and **keep brightness** (TikTok no longer turns it up in the camera and editor).
- **New profile layout**, 🧪 **account creation date** under the name, **hide stories in the Inbox**.
- **Time on messages**: under every chat message.
- **"Read all"**: hold the Inbox tab.
- 🧪 **Keep unsent messages**: a message the other person unsent stays, marked "Unsent" in red; photos, videos and stickers are still in beta.

### Comments
- **TikTok's translate buttons**: an icon on every foreign comment and "Translate all" in the
  comments header, even if TikTok never showed them to you.
- **Auto-translate comments**: turns TikTok's translation on for all comments right away.
- **Copy text only**, **author's country** (a flag next to the video date, in profiles and in comments), **@username of commenters**.
- **Comment reposts**; 🧪 **repost your own videos**.
- 🧪 **Up to 9 photos in a comment**.

### Downloads and links
- **No watermark**, **download any video**, **quality picker** and "Audio only".
- **All photos from a carousel**, **stickers and photos from comments**, **stickers from chats**.
- **Save stories and highlights** (hold a story) and **profile photos** (hold the enlarged photo).
- 🧪 **Record LIVEs**: a REC button in a LIVE records it in the quality you pick to Movies/TikTok (MP4), or copies the stream link. Android 10+.
- 🧪 **Upload in HD**, as with TikTok's "Upload HD" switched on, and a 🧪 bitrate choice: 12, 20 or 30 Mbit/s.
- 🧪 **Upload the original (up to 4K)**: an unedited video goes up as the file itself, without TikTok re-encoding it.
- **Clean links** (no tracking), **links in your browser**, **screenshots everywhere**.

### Privacy
- 🧪 **Invisible mode**: not showing up in story and highlight views (a separate 🧪 switch), profile and video views. On your phone the story still counts as watched.
- **In chats:** hide "typing…", 🧪 "online" and 🧪 "Read" (with "Mark as read" in a message's menu
  and "Read" when you reply).
- 🧪 **Block location**, 🧪 **hide VPN**, 🧪 **random device ID**, 🧪 **hide root**,
  🧪 **no motion tracking**, **no passkey prompts**.
- **Analytics:** block third-party trackers (Google/Firebase, Facebook, AppsFlyer) and 🧪 don't send TikTok's analytics.

### Region
- **Country**: SIM, carrier and time zone of about 190 countries (Germany by default).
- **Recommendations in my language**.
- 🧪 **Own recommendation region**: the SIM stays in the chosen country while the feed comes from another one.

The module doesn't change the account's region or your IP address, so the result isn't guaranteed.

### Streaks
- **Auto streaks**: keep existing streaks in one-to-one chats going while TikTok is open. Sent
  when a streak starts to fade, or at your own time (if it would end sooner, it goes an hour before
  the end). A flame or a sticker from your favorites, to everyone or only chosen chats; the settings
  show how it will look in the chat. If you already wrote to that chat today (also from another
  phone), nothing is sent again. If TikTok rejects a flame, you get a notification.

### Experiments
- **Hidden TikTok features**: hold-to-speed-up, audio and photo comments, comments to favorites and more.
- 🧪 **No forced login**, 🧪 **no hashtag limit**, 🧪 **camera extras** (teleprompter, green screen).

### Settings screen
Search across settings, how many features are on in each section, "Feature status" (what was found in your
TikTok version, and why something was not). Under "More": export and import of settings as a `.json` file,
reset all settings and a **bug report** (log, crashes, device; attach it to an issue). If TikTok closes right
after starting three times in a row, the module runs one launch in **safe mode** so the report can be saved.
</details>

<details>
<summary><b>Troubleshooting</b></summary>

- **The module isn't in TikTok's settings**: check that the module is enabled in LSPosed with TikTok
  ticked, then force-stop TikTok.
- **A feature stopped working after a TikTok update**: open "Feature status". If it says "Not found
  in this TikTok version", open an [issue](https://github.com/hik0w/TiktokPatchXposed/issues) with your TikTok version.
- **TikTok shows "Restore the app"**: tap **Cancel**; restoring resets TikTok's data.
- **Limits:** the TikTok icon can't be changed; some elements drawn as pictures may keep TikTok's colors.
</details>

<details>
<summary><b>Compatibility and building</b></summary>

The module relies only on TikTok's readable names and finds obfuscated places by itself on the
first launch of a new version (by strings and signatures), then remembers them. Compressed code
(betas, universal APKs), moved classes and TikTok patched with LSPatch are supported. Each feature
starts separately: if one isn't found, the others keep working.

Building needs Java 17 and Android SDK 36.

```shell
cd source
./gradlew testDebugUnitTest assembleRelease
```

The APK appears in `source/app/build/outputs/apk/release/`.
</details>

## Disclaimer

Modifying the client and automated actions can lead to account restrictions. Use at your own risk.
This is an unofficial project, not affiliated with TikTok or ByteDance.

**MIT** license, see [LICENSE](LICENSE).
