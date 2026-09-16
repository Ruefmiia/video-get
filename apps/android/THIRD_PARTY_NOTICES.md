# Android third-party notices

The Android application currently embeds the following download runtime:

- `io.github.junkfood02.youtubedl-android:library:0.18.1`
- `io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1`

Project source: <https://github.com/yausername/youtubedl-android>

The upstream project is licensed under GNU GPL version 3. Distribution of an APK containing these components must comply with GPL-3.0, including providing the applicable license text and corresponding source as required. This file is an engineering notice, not legal advice.

The runtime also bundles yt-dlp, Python and FFmpeg-related native artifacts. Before a public release, generate a complete software bill of materials, retain source/build information for the exact artifacts, and review all transitive notices.

The native Kotlin Threads parser was informed by the public-domain `yt-dlp-threads` extractor:

- Project source: <https://github.com/tribixbite/yt-dlp-threads>
- License: The Unlicense

The implementation in Video Get is maintained locally and does not dynamically download or execute this plugin.
