# Building and distributing Anthias OS images

## Overlays

| Overlay | Purpose |
|---|---|
| `kas/anthias-nanopi-r3s-lts.yml` | Base: layers, machine, distro, targets. Development defaults. |
| `kas/scratch.yml` | Puts DL_DIR/SSTATE_DIR/TMPDIR on a big volume. Local only. |
| `kas/ci.yml` | `rm_work`, reproducible builds, hash-equivalence. For hosted runners. |
| `kas/production.yml` | Lockdown + release signing + update URL. |

Compose with `:` — e.g. a release build is
`kas/anthias-nanopi-r3s-lts.yml:kas/ci.yml:kas/production.yml`.

## Distribution shape

GitHub Pages is capped at 1 GB per site, which is about six of our 163 MB
bundles, and it is not meant to be a binary CDN. So:

- **Releases** hold the bundles and `.wic.xz` images. 2 GB per asset, permanent.
- **Pages** holds only `<machine>/latest.json`, a few hundred bytes, pointing
  at the Release asset.
- **Actions artifacts** are for PR builds only — they expire, so they can never
  be the thing a fleet updates from.

A device fetches `${ANTHIAS_UPDATE_URL}/${compatible}/latest.json`, compares
`version` against `/etc/anthias-version`, and installs if they differ.

## Two independent signatures

1. **RAUC bundle signature** — the only one that matters to the device. Made
   with `ANTHIAS_SIGNING_DIR`, checked against the CA baked into the rootfs at
   `/usr/lib/rauc/ca.cert.pem`. Losing this key means no device can ever be
   updated again; leaking it means anyone can update every device.
2. **SLSA build provenance** — `actions/attest-build-provenance`, keyless via
   OIDC. Proves the bytes on the Release came out of this workflow at this
   commit. Useful to humans and auditors; the device does not consult it.

The manifest `sha256` is neither of those — it is a cheap integrity check so a
truncated download is not handed to `rauc`.

## Required repository configuration

Secrets: `RAUC_SIGNING_KEY`, `RAUC_SIGNING_CERT`, `RAUC_CA_CERT`,
`ANTHIAS_SSH_AUTHORIZED_KEY`. Variable: `ANTHIAS_UPDATE_URL`.
Settings → Pages → Source: GitHub Actions.

## Measured build cost

On 6 cores / 30 GB, from cold: 4978 tasks, ~75 min, TMPDIR peaked at **56 GB**,
DL_DIR 11 GB, SSTATE 4.5 GB. That 56 GB is why `kas/ci.yml` enables `rm_work`
and why the workflow builds under `/mnt` — a hosted runner has ~14 GB free on
`/`. With a warm sstate the same build finishes in ~90 seconds.
