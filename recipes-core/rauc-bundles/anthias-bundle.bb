SUMMARY = "RAUC update bundle for the Anthias image"
DESCRIPTION = "Signed A/B rootfs bundle. Install on a running device with \
    'rauc install anthias-bundle-<machine>.raucb' and reboot; U-Boot boots \
    the freshly written slot and rauc-mark-good confirms it."

inherit bundle

RAUC_BUNDLE_COMPATIBLE = "${MACHINE}"
RAUC_BUNDLE_VERSION = "${DISTRO_VERSION}-${DATETIME}"
RAUC_BUNDLE_VERSION[vardepsexclude] = "DATETIME"
RAUC_BUNDLE_DESCRIPTION = "Anthias OS ${DISTRO_VERSION} for ${MACHINE}"
RAUC_BUNDLE_FORMAT = "verity"

RAUC_BUNDLE_SLOTS = "rootfs"
RAUC_SLOT_rootfs = "anthias-image"
RAUC_SLOT_rootfs[fstype] = "ext4"

# Development PKI, committed on purpose so the OTA path is testable end to end
# out of the box. These are ?= so CI can point them at a real signing key
# without patching the recipe:
#
#   ANTHIAS_SIGNING_DIR = "/run/anthias-pki"   (in local.conf, from a secret)
#
# The device-side counterpart is ANTHIAS_KEYRING_FILE in rauc-conf.bbappend;
# a bundle signed by a key whose CA is not in that keyring will be rejected.
ANTHIAS_DEV_PKI_DIR := "${THISDIR}/files"
# kas `env:` passes an unset variable through as an empty string, not as
# "undefined", so ?= alone would leave RAUC_KEY_FILE as "/development-1.key.pem".
ANTHIAS_SIGNING_DIR ?= ""
ANTHIAS_SIGNING_DIR := "${@d.getVar('ANTHIAS_SIGNING_DIR') or d.getVar('ANTHIAS_DEV_PKI_DIR')}"
RAUC_KEY_FILE ?= "${ANTHIAS_SIGNING_DIR}/development-1.key.pem"
RAUC_CERT_FILE ?= "${ANTHIAS_SIGNING_DIR}/development-1.cert.pem"

# Fail loudly rather than silently shipping a dev-signed bundle to a fleet,
# and give a fresh clone an actionable error rather than a missing-file one.
python () {
    import os
    dev = d.getVar('ANTHIAS_DEV_PKI_DIR')
    if d.getVar('ANTHIAS_SIGNING_DIR') == dev:
        if d.getVar('ANTHIAS_RELEASE') == '1':
            bb.fatal("ANTHIAS_RELEASE=1 but the bundle would be signed with the "
                     "development key. Set ANTHIAS_SIGNING_DIR to a real key.")
        if not os.path.exists(os.path.join(dev, 'development-1.key.pem')):
            bb.fatal("No development PKI found. It is not committed because "
                     "this repository is public. Generate one with:\n"
                     "    ./scripts/make-dev-pki.sh")
}
