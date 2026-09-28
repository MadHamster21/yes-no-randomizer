# Current Play Store screenshots

Regenerated September 27, 2026 with the built-in imagegen tool in the selected
clean, playful style. Open [index.html](index.html) for all four cards and the
real-capture alternative. [Download the generated set](../generated-screenshots.zip).

## Upload order

1. Let the dice decide.
2. Light or dark. Your call.
3. Float your answer.
4. Say it in your language.

`phone/generated/01.png` through `04.png` are the current upload files. All are
opaque 1080 x 1920 RGB PNGs. Generated originals remain in
`review/generated-originals/`; the export script scales them proportionally.
The exact prompts and input reference roles are in [generation-prompts-2026-09-27.md](generation-prompts-2026-09-27.md).

Every visible English app action reads **Float answer**. Spanish reads
**Respuesta flotante**. Generated panels can still differ slightly in typography,
spacing and die shading. `phone/real/` retains the actual-capture alternative.
The September 26 `raw/` captures already show these current controls and are
used as authoritative UI references. Later app changes fixed rendering during
resizing without changing the steady screen layout.

The main gallery, root generated PNGs, and both generated ZIP links now point
to this set. Previous artwork remains available in Git history.

The listing [plain-text fields](../copy/) highlight the floating answer and
preservation across rotation/language changes. The title stays Yes/No Randomizer.
No Play Console changes were made.

## Validation

Run `python store-assets/2026-09/scripts/validate.py --updated` from the repository
root to verify dimensions, RGB format, listing limits and local gallery links.
Run `store-assets/2026-09/scripts/review-updated.ps1` for the browser image/toggle
checks and gallery preview. See `validation.json` for results.

App verification from the preceding code task: 19 instrumentation tests passed
on the API 37 emulator. This artwork refresh changes no app source.
