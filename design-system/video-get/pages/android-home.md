# Android Home Override

- Override the master palette with a strictly monochrome black, white, and neutral-gray scheme. Use near-black primary actions, white/light-gray surfaces, and inverted dark-mode equivalents.
- Use Android system sans typography for Chinese body readability and offline operation; do not fetch remote fonts.
- Respect system bars through `Scaffold`, use a single `LazyColumn`, stable item keys, and adaptive full-width cards.
- All interactive targets are at least 48dp with 8dp or greater separation.
- Clipboard access occurs only from the labeled paste button. Shared links are validated before any download begins.
- Hide idle help/status copy to keep the page task-focused. Show active, invalid, ready, and completed states with text plus outline icons; never rely on color alone.
- Use Material outline icons consistently, with content descriptions on standalone icon controls and decorative icons hidden.
- Place a compact-width but 48dp-or-taller clear action immediately left of the primary analyze action.
