# Android Home Override

- Use the master rose background and blue primary action, with semantic light/dark color schemes.
- Use Android system sans typography for Chinese body readability and offline operation; do not fetch remote fonts.
- Respect system bars through `Scaffold`, use a single `LazyColumn`, stable item keys, and adaptive full-width cards.
- All interactive targets are at least 48dp with 8dp or greater separation.
- Clipboard access occurs only from the labeled paste button. Shared links are validated before any download begins.
- Show invalid, planned, ready, and idle states with text plus surface color; never rely on color alone.
- Use Material outline icons consistently, with content descriptions on standalone icon controls and decorative icons hidden.
