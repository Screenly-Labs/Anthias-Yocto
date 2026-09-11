SUMMARY = "Anthias digital signage — host-side stack"
DESCRIPTION = "Installs the upstream Anthias checkout plus the systemd glue \
that runs it on a Yocto host. The application itself is not modified and not \
rebuilt: the stack is the stock ghcr.io/screenly/anthias-* arm64 container \
images, brought up by upstream's own bin/upgrade_containers.sh."
HOMEPAGE = "https://anthias.screenly.io"
LICENSE = "GPL-2.0-only"
LIC_FILES_CHKSUM = "file://LICENSE;md5=61a0e5313885795074e963ad3dd0afd1"

SRC_URI = " \
    git://github.com/Screenly/Anthias.git;protocol=https;branch=master;name=anthias \
    file://anthias-start \
    file://anthias-provision \
    file://anthias.service \
    file://anthias-provision.service \
    file://docker-daemon.json \
    file://docker-after-data.conf \
"
SRCREV_anthias = "a9b82f58dca48096751bd6dd2d0dc631a766fe5e"
PV = "0.20.0+git"

S = "${UNPACKDIR}/git"

inherit systemd useradd

do_configure[noexec] = "1"
do_compile[noexec] = "1"

ANTHIAS_USER ?= "anthias"
ANTHIAS_UID ?= "1000"
ANTHIAS_HOME ?= "/home/${ANTHIAS_USER}"

USERADD_PACKAGES = "${PN}"
GROUPADD_PARAM:${PN} = "-g ${ANTHIAS_UID} ${ANTHIAS_USER}"
USERADD_PARAM:${PN} = "-u ${ANTHIAS_UID} -g ${ANTHIAS_USER} -d ${ANTHIAS_HOME} -M -s /bin/bash ${ANTHIAS_USER}"

SYSTEMD_SERVICE:${PN} = "anthias.service anthias-provision.service"
SYSTEMD_AUTO_ENABLE:${PN} = "enable"

do_install() {
    # The upstream checkout, verbatim. bin/upgrade_containers.sh and the
    # compose template are the parts we actually use, but shipping the whole
    # tree keeps collect_debug.sh and the other operator tools working and
    # makes 'is this stock Anthias?' answerable with a diff.
    install -d ${D}${ANTHIAS_HOME}/anthias
    cp -a ${S}/. ${D}${ANTHIAS_HOME}/anthias/
    rm -rf ${D}${ANTHIAS_HOME}/anthias/.git

    # State that must survive an A/B update lives on /data; the rootfs only
    # carries the symlinks. anthias-provision.service creates the targets
    # before docker starts.
    ln -sf /data/anthias/config   ${D}${ANTHIAS_HOME}/.anthias
    ln -sf /data/anthias/assets   ${D}${ANTHIAS_HOME}/anthias_assets
    ln -sf /data/anthias/asoundrc ${D}${ANTHIAS_HOME}/.asoundrc
    chown -R ${ANTHIAS_UID}:${ANTHIAS_UID} ${D}${ANTHIAS_HOME}

    install -d ${D}${libexecdir}/anthias
    install -m 0755 ${UNPACKDIR}/anthias-start     ${D}${libexecdir}/anthias/
    install -m 0755 ${UNPACKDIR}/anthias-provision ${D}${libexecdir}/anthias/

    install -d ${D}${sysconfdir}/docker
    install -m 0644 ${UNPACKDIR}/docker-daemon.json ${D}${sysconfdir}/docker/daemon.json

    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${UNPACKDIR}/anthias.service           ${D}${systemd_system_unitdir}/
    install -m 0644 ${UNPACKDIR}/anthias-provision.service ${D}${systemd_system_unitdir}/

    install -d ${D}${systemd_system_unitdir}/docker.service.d
    install -m 0644 ${UNPACKDIR}/docker-after-data.conf \
        ${D}${systemd_system_unitdir}/docker.service.d/10-anthias-data.conf
}

FILES:${PN} += " \
    ${ANTHIAS_HOME} \
    ${libexecdir}/anthias \
    ${sysconfdir}/docker \
    ${systemd_system_unitdir} \
"

# Runtime dependencies of upstream's bin/upgrade_containers.sh: bash, bc,
# envsubst (gettext), ip (iproute2), sudo and the docker CLI + compose plugin.
RDEPENDS:${PN} = " \
    bash \
    bc \
    gettext \
    iproute2 \
    sudo \
    docker-moby \
    docker-compose \
"

# It is an upstream source tree: python sources, shell helpers and a couple of
# vendored JS bundles. None of it is executed at build time and none of it is
# a compiled artefact, so the usual binary QA does not apply.
INSANE_SKIP:${PN} += "file-rdeps"
