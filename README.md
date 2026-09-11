# meta-anthias

Yocto layer that builds an [Anthias](https://anthias.screenly.io) digital
signage appliance for the **FriendlyELEC NanoPi R3S LTS** (Rockchip RK3566),
field-updatable over the air with **RAUC**.

The design goal is *don't fork Anthias*. The application is not rebuilt here:
the image runs the stock `ghcr.io/screenly/anthias-*:<tag>-arm64` container
images through upstream's own `bin/upgrade_containers.sh`, which is exactly
the `DEVICE_TYPE=arm64` path Anthias already supports for generic 64-bit ARM
SBCs. Yocto supplies the host — kernel, Docker, RAUC, systemd — and nothing
else.

## Layout

| Path | What it is |
| --- | --- |
| `conf/distro/anthias.conf` | Poky derivative: systemd, `virtualization`, `rauc`; no X11/Wayland/GL on the host |
| `conf/machine/nanopi-r3s-lts.conf` | The LTS board (HDMI) |
| `conf/machine/nanopi-r3s.conf` | The original headless R3S, for OTA testing without a display |
| `conf/machine/include/nanopi-r3s.inc` | Shared RK3566 + RAUC + U-Boot-env settings |
| `recipes-kernel/linux/files/rk3566-nanopi-r3s-lts.dts` | **New device tree** — see below |
| `recipes-anthias/anthias/` | Upstream Anthias checkout + systemd glue |
| `recipes-core/images/anthias-image.bb` | The image |
| `recipes-core/rauc*/` | RAUC `system.conf`, keyring, and the update bundle |
| `kas/anthias-nanopi-r3s-lts.yml` | Reproducible build config |

## The device tree is the interesting part

Mainline Linux has `rk3566-nanopi-r3s.dts`, but that is the **original** R3S,
which has no video output at all — it enables `&vop` and `&gpu` and stops
there. The LTS revision adds the full-size HDMI connector, and that is
precisely what turns the board into a signage player.

`rk3566-nanopi-r3s-lts.dts` includes the upstream dts and adds the missing
display pipeline:

```
VOP2 video port 0  ->  Synopsys DW-HDMI TX  ->  hdmi-connector
```

plus the HDMI audio DAI (`&hdmi_sound`, `&i2s0_8ch`). The rails the HDMI PHY
needs (`vdda_0v9`, `vcca1v8_image` off the RK809) are already in the upstream
dts, so nothing else has to change. A `linux-yocto` bbappend drops the file
into the kernel tree and registers it in the rockchip dts Makefile, so it
builds like any in-tree dtb and lands in the fitImage.

**This is the one thing that needs hardware to confirm.** Everything else in
this layer is exercised by the build; HDMI bring-up on a real LTS board is
not.

## A/B updates

Built on meta-rockchip's Rockchip RAUC reference integration (`RK_RAUC_DEMO`),
which supplies a U-Boot boot script implementing `BOOT_ORDER` / `BOOT_x_LEFT`
and `systemd-repart` drop-ins. The shipped `.wic` contains **only** `rootfsA`;
on first boot repart grows the disk into:

| Partition | Size | Contents |
| --- | --- | --- |
| `rootfsA` | ≤ 5 GB | slot A |
| `rootfsB` | ≤ 5 GB | slot B, created on first boot |
| `data` | rest of the disk | everything that must outlive an update |

`/data` holds the Docker data-root (`/data/docker`, set in
`/etc/docker/daemon.json`), the Anthias SQLite DB and config
(`/data/anthias/config`), and uploaded assets (`/data/anthias/assets`). The
matching paths in `/home/anthias` are symlinks, so an A/B rootfs swap never
touches device state or forces a re-pull of ~1.5 GB of container images.

Update flow:

```sh
rauc install anthias-bundle-nanopi-r3s-lts.raucb
reboot
```

U-Boot boots the freshly written slot, `rauc-mark-good` confirms it, and a
slot that fails to come up three times falls back to the other one.

### Signing keys

`recipes-core/rauc/files/ca.cert.pem` and
`recipes-core/rauc-bundles/files/development-1.*` are a **development PKI**,
committed on purpose so the OTA path is testable end to end out of the box.
Replace both before shipping anything to a real fleet.

## Boot sequence

```
data.mount                 (systemd-repart created it on first boot)
  -> anthias-provision.service   creates /data/{docker,rauc,anthias/*}
  -> docker.service              data-root on /data/docker
  -> anthias.service             /usr/libexec/anthias/anthias-start
                                   -> upstream bin/upgrade_containers.sh
                                   -> docker compose up -d
```

`anthias-start` falls back to the locally cached images if the registry is
unreachable — a signage player has to come back after a power cut even with
no network.

The viewer container is `privileged` and brings its own compositor (`cage`)
and its own Mesa, taking DRM master on `/dev/dri/card0` directly, exactly as
it does on Armbian today. That is why the host has no graphics stack at all;
it only has to provide the DRM device, which is why the kernel config
fragment builds `DRM_ROCKCHIP` / `ROCKCHIP_VOP2` / `ROCKCHIP_DW_HDMI` /
`DRM_PANFROST` in rather than as modules.

## Building

```sh
pip install kas
kas build meta-anthias/kas/anthias-nanopi-r3s-lts.yml
```

Artefacts land in `build/tmp/deploy/images/nanopi-r3s-lts/`:

* `anthias-image-nanopi-r3s-lts.rootfs.wic` — flash to microSD or eMMC
* `anthias-bundle-nanopi-r3s-lts.raucb` — OTA bundle

## Versions

Yocto **wrynose** (5.4, the April 2026 LTS) across all layers. Note that the
`poky` combo repository was retired in January 2026, so the build is assembled
from `openembedded-core` + `bitbake` + `meta-yocto` directly. Kernel is
`linux-yocto` 6.18; U-Boot 2026.01, which already carries
`nanopi-r3s-rk3566_defconfig`.
