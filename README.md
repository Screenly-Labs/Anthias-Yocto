# Anthias-Yocto

A Yocto layer that builds [Anthias](https://anthias.screenly.io) into a
standalone, self-updating appliance image for the FriendlyELEC NanoPi R3S LTS
(Rockchip RK3566).

Anthias itself runs **unmodified**: the image pulls the stock
`ghcr.io/screenly/anthias-*:latest-arm64` containers and starts them with
upstream's own `bin/upgrade_containers.sh`. Yocto supplies the host — kernel,
Docker, RAUC, systemd — and nothing else.

## Quick start

```sh
uv tool install kas
./scripts/make-dev-pki.sh        # generates a local signing key; not in git
kas build kas/anthias-nanopi-r3s-lts.yml
```

Artefacts land in `build/tmp/deploy/images/nanopi-r3s-lts/`:

| File | Use |
|---|---|
| `anthias-image-nanopi-r3s-lts.rootfs.wic` | flash to SD/eMMC (`bmaptool copy`) |
| `anthias-bundle-nanopi-r3s-lts.raucb` | OTA update bundle |

A cold build needs roughly **70 GB** of disk and takes ~75 min on 6 cores.
Add `kas/scratch.yml` to put everything on a dedicated volume.

## What you get

- **A/B updates with rollback** — RAUC over U-Boot `BOOT_ORDER` with
  three-strikes fallback. `systemd-repart` creates the B slot and a `/data`
  partition on first boot.
- **Persistent state** — Docker's data-root, the Anthias DB, config and assets
  all live on `/data`, so a slot swap never re-pulls ~1.5 GB of images.
- **Self-updating** — a systemd timer polls a static JSON manifest and installs
  the bundle it points at. No update server, nothing listening.
- **HDMI** — the LTS board's HDMI output has no upstream device tree, so this
  layer ships `rk3566-nanopi-r3s-lts.dts`.

## Layout

```
kas/            build configurations, composed with ':'
classes/        anthias-hardening.bbclass (production lockdown)
conf/           machine + distro definitions
recipes-anthias/    the Anthias stack and the self-update client
recipes-core/       image, RAUC config and bundle
recipes-kernel/     device tree and kernel config
scripts/        make-dev-pki.sh
```

See [README-ci.md](README-ci.md) for building in GitHub Actions, signing and
distribution.

## Pinned dependencies

Every GitHub Action is pinned to a commit SHA with the version in a trailing
comment, and `.github/dependabot.yml` keeps those pins current — a floating tag
can be moved under you, and this workflow signs artefacts a fleet will install.

The Yocto layers are pinned the same way, in
`kas/anthias-nanopi-r3s-lts.lock.yml`. kas picks that up automatically; without
it the layers track branch heads and no two builds are guaranteed to agree.
Refresh it deliberately:

```sh
kas lock --update kas/anthias-nanopi-r3s-lts.yml
```

Dependabot cannot manage that file, so layer bumps are a manual, reviewed step.

## Signing keys

`scripts/make-dev-pki.sh` generates a local development CA and signing key.
They are **not committed** — this repository is public, and that CA is the root
of trust for every device flashed with a non-production image.

For a real fleet use `kas/production.yml`, which refuses to build unless
`ANTHIAS_SIGNING_DIR` points at a key that is not the development one.

## Status

Built and verified end to end on the build host: the image and bundle build
clean, the bundle verifies against the keyring extracted from the built rootfs,
and the device tree compiles with the HDMI pipeline enabled.

**Not yet validated on hardware.** Nobody has booted this on an LTS board, so
HDMI output, the first-boot repartition and a live A/B swap are all unproven.
