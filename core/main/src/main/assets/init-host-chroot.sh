#!/bin/sh
SU="/system/bin/su"
ALPINE_DIR=$PREFIX/local/alpine
ROOTFS_TAR="$PREFIX/files/alpine.tar.gz"

mkdir -p "$ALPINE_DIR"

# /bin/sh is often an absolute symlink (e.g. Alpine's /bin/sh -> /bin/busybox),
# so it must not be tested from the host filesystem. Check real files instead.
rootfs_ok() {
    [ -e "$ALPINE_DIR/etc/os-release" ] || [ -e "$ALPINE_DIR/etc/alpine-release" ] || return 1
    [ -e "$ALPINE_DIR/bin/busybox" ] || [ -e "$ALPINE_DIR/usr/bin/bash" ] \
        || [ -e "$ALPINE_DIR/bin/bash" ] || [ -e "$ALPINE_DIR/usr/bin/pacman" ]
}

if ! rootfs_ok; then
    if [ ! -s "$ROOTFS_TAR" ]; then
        echo "ReTerminal: root filesystem archive is missing: $ROOTFS_TAR" >&2
        exit 1
    fi

    echo "ReTerminal: extracting the root filesystem, this can take a few minutes..."

    for rootfs_entry in "$ALPINE_DIR"/* "$ALPINE_DIR"/.[!.]*; do
        [ -e "$rootfs_entry" ] || continue
        case "$rootfs_entry" in
            "$ALPINE_DIR/root"|"$ALPINE_DIR/tmp") continue ;;
        esac
        rm -rf "$rootfs_entry"
    done

    tar -xf "$ROOTFS_TAR" -C "$ALPINE_DIR" 2>"$PREFIX/local/tar-error.log" || true

    if ! rootfs_ok; then
        echo "ReTerminal: failed to extract the root filesystem." >&2
        if [ -s "$PREFIX/local/tar-error.log" ]; then
            tail -n 5 "$PREFIX/local/tar-error.log" >&2
        fi
        exit 1
    fi

    rm -f "$PREFIX/local/tar-error.log"
    echo "ReTerminal: root filesystem ready"
fi

# Older versions replaced Alpine's busybox `rm` with a shell wrapper that
# hangs under proot. Restore the original busybox symlink if needed.
if [ -f "$ALPINE_DIR/etc/alpine-release" ] && [ -e "$ALPINE_DIR/bin/busybox" ]; then
    if [ ! -L "$ALPINE_DIR/bin/rm" ]; then
        rm -f "$ALPINE_DIR/bin/rm" 2>/dev/null || true
        ln -sf /bin/busybox "$ALPINE_DIR/bin/rm" 2>/dev/null || true
    fi
fi

MOUNTS=""

mnt_bind() {
    src="$1"
    dst="$ALPINE_DIR${2:-$1}"
    if [ -e "$src" ] && [ ! -e "$dst" ]; then
        mkdir -p "$(dirname "$dst")" 2>/dev/null
        if [ -d "$src" ]; then
            $SU -c "mkdir -p '$dst'"
        else
            $SU -c "touch '$dst'"
        fi
    fi
    if [ -e "$src" ]; then
        $SU -c "mount --bind '$src' '$dst'" 2>/dev/null
        MOUNTS="$MOUNTS $dst"
    fi
}

for system_mnt in /apex /odm /product /system /system_ext /vendor \
 /linkerconfig/ld.config.txt \
 /linkerconfig/com.android.art/ld.config.txt \
 /plat_property_contexts /property_contexts; do
    if [ -e "$system_mnt" ]; then
        system_mnt=$(realpath "$system_mnt")
        mnt_bind "$system_mnt"
    fi
done
unset system_mnt

mnt_bind /sdcard
mnt_bind /storage
mnt_bind /dev
mnt_bind /data
mnt_bind /proc
mnt_bind /sys
mnt_bind /dev/urandom /dev/random
mnt_bind $PREFIX

if [ -e "/proc/self/fd" ]; then mnt_bind /proc/self/fd /dev/fd; fi
if [ -e "/proc/self/fd/0" ]; then mnt_bind /proc/self/fd/0 /dev/stdin; fi
if [ -e "/proc/self/fd/1" ]; then mnt_bind /proc/self/fd/1 /dev/stdout; fi
if [ -e "/proc/self/fd/2" ]; then mnt_bind /proc/self/fd/2 /dev/stderr; fi

if [ ! -d "$PREFIX/local/alpine/tmp" ]; then
    $SU -c "mkdir -p '$PREFIX/local/alpine/tmp' && chmod 1777 '$PREFIX/local/alpine/tmp'"
fi
mnt_bind "$PREFIX/local/alpine/tmp" /dev/shm

if [ -e "$PREFIX/local/stat" ]; then
    $SU -c "cp '$PREFIX/local/stat' '$ALPINE_DIR/proc/stat'" 2>/dev/null
fi
if [ -e "$PREFIX/local/vmstat" ]; then
    $SU -c "cp '$PREFIX/local/vmstat' '$ALPINE_DIR/proc/vmstat'" 2>/dev/null
fi

cleanup() {
    for m in $MOUNTS; do
        $SU -c "umount -l '$m'" 2>/dev/null
    done
}
trap cleanup EXIT INT TERM

$SU -c "'$CHROOT' '$ALPINE_DIR' /usr/bin/env -i HOME=/root PATH=/bin:/sbin:/usr/bin:/usr/sbin RETERM_LOGIN_SHELL=${RETERM_LOGIN_SHELL:-0} sh '$PREFIX/local/bin/init' $*"
cleanup
