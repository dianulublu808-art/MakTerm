ALPINE_DIR=$PREFIX/local/alpine
ROOTFS_TAR="$PREFIX/files/alpine.tar.gz"

mkdir -p "$ALPINE_DIR"

# proot itself is executed from the app's native library directory, while
# LD_LIBRARY_PATH points at $PREFIX/local/lib. Copy its shared dependencies
# there so the dynamic linker can resolve them.
if [ -n "$NATIVE_LIB_DIR" ] && [ -d "$NATIVE_LIB_DIR" ]; then
    mkdir -p "$PREFIX/local/lib"
    for proot_lib in libtalloc.so libandroid-shmem.so; do
        if [ -e "$NATIVE_LIB_DIR/$proot_lib" ] && [ ! -e "$PREFIX/local/lib/$proot_lib" ]; then
            cp "$NATIVE_LIB_DIR/$proot_lib" "$PREFIX/local/lib/$proot_lib" 2>/dev/null || true
        fi
    done
fi

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
        echo "ReTerminal: reopen the app and install the root filesystem again." >&2
        exit 1
    fi

    echo "ReTerminal: extracting the root filesystem, this can take a few minutes..."

    # Remove leftovers from a previous, interrupted extraction. Otherwise the
    # directory looks installed but is unusable.
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
        df -h "$PREFIX" >&2 2>/dev/null || true
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

ARGS="--kill-on-exit"
ARGS="$ARGS -w /"

for system_mnt in /apex /odm /product /system /system_ext /vendor \
 /linkerconfig/ld.config.txt \
 /linkerconfig/com.android.art/ld.config.txt \
 /plat_property_contexts /property_contexts; do

 if [ -e "$system_mnt" ]; then
  system_mnt=$(realpath "$system_mnt")
  ARGS="$ARGS -b ${system_mnt}"
 fi
done
unset system_mnt

ARGS="$ARGS -b /sdcard"
ARGS="$ARGS -b /storage"
ARGS="$ARGS -b /dev"
ARGS="$ARGS -b /data"
ARGS="$ARGS -b /dev/urandom:/dev/random"
ARGS="$ARGS -b /proc"
ARGS="$ARGS -b $PREFIX"
ARGS="$ARGS -b $PREFIX/local/stat:/proc/stat"
ARGS="$ARGS -b $PREFIX/local/vmstat:/proc/vmstat"

if [ -e "/proc/self/fd" ]; then
  ARGS="$ARGS -b /proc/self/fd:/dev/fd"
fi

if [ -e "/proc/self/fd/0" ]; then
  ARGS="$ARGS -b /proc/self/fd/0:/dev/stdin"
fi

if [ -e "/proc/self/fd/1" ]; then
  ARGS="$ARGS -b /proc/self/fd/1:/dev/stdout"
fi

if [ -e "/proc/self/fd/2" ]; then
  ARGS="$ARGS -b /proc/self/fd/2:/dev/stderr"
fi


ARGS="$ARGS -b $PREFIX"
ARGS="$ARGS -b /sys"

if [ ! -d "$PREFIX/local/alpine/tmp" ]; then
 mkdir -p "$PREFIX/local/alpine/tmp"
 chmod 1777 "$PREFIX/local/alpine/tmp"
fi
ARGS="$ARGS -b $PREFIX/local/alpine/tmp:/dev/shm"

ARGS="$ARGS -r $PREFIX/local/alpine"
ARGS="$ARGS -0"
ARGS="$ARGS --link2symlink"
ARGS="$ARGS --sysvipc"
ARGS="$ARGS -L"

$PROOT $ARGS sh $PREFIX/local/bin/init "$@"
