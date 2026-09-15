# Extension popup override

This page is a 400px utility surface, so these rules override the editorial and showcase-oriented parts of `MASTER.md`.

- Use local system fonts only: `Inter`, `Segoe UI`, `Microsoft YaHei`, sans-serif. Never load remote fonts.
- Keep the rose brand color for the transfer rail and status marker. Use blue only for the single primary action.
- Use compact flat surfaces with 1px borders and one small shadow depth. No decorative hero, gradients, or floating cards.
- Signature element: a left-hand transfer rail connects the source, analysis, and download states.
- Minimum control height is 44px. Every field has a visible label; every async state uses `aria-live` or `role=status`.
- Reserve progress space to avoid layout shifts. Respect `prefers-reduced-motion`.
