#!/usr/bin/env bash
# Builds libmakterm_core.so for Android and copies it into core/main/jniLibs.
#
# CI: requires cargo, rustup and an Android NDK (ANDROID_NDK_HOME or
#     $ANDROID_HOME/ndk/<version>).
# Termux: the host triple already matches arm64 Android, so the arm64 library
#     is built natively without cross-compilation.
#
# Optional environment variables:
#   ABIS="arm64-v8a x86_64"  limit the ABIs to build
#   API=26                   Android API level for the linker
#   CLANG=/path/to/clang     override the compiler used as linker
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
PROJECT="$(cd "$ROOT/../.." && pwd)"
API="${API:-26}"
ABIS="${ABIS:-arm64-v8a armeabi-v7a x86_64 x86}"
HOST="$(rustc -vV | awk '/^host:/{print $2}')"

if [ -z "${ANDROID_NDK_HOME:-}" ]; then
    if [ -n "${ANDROID_HOME:-}" ] && [ -d "$ANDROID_HOME/ndk" ]; then
        ANDROID_NDK_HOME="$(find "$ANDROID_HOME/ndk" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -1)"
    fi
fi
if [ -z "${ANDROID_NDK_HOME:-}" ] || [ ! -d "$ANDROID_NDK_HOME" ]; then
    echo "Android NDK not found. Set ANDROID_NDK_HOME or ANDROID_HOME." >&2
    exit 1
fi

HOST_TAG="linux-x86_64"
SYSROOT="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/$HOST_TAG/sysroot"
CLANG="${CLANG:-$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/$HOST_TAG/bin/clang}"
AR_BIN="${AR:-$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/$HOST_TAG/bin/llvm-ar}"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

build_one() {
    local abi="$1" triple="$2" clang_triple="$3"
    case " $ABIS " in
        *" $abi "*) ;;
        *) return 0 ;;
    esac

    local linker="$WORK/$triple-clang"
    cat > "$linker" <<EOF
#!/bin/sh
exec "$CLANG" --target=$clang_triple --sysroot="$SYSROOT" "\$@"
EOF
    chmod +x "$linker"

    local so
    if [ "$HOST" = "$triple" ]; then
        (cd "$ROOT" && cargo build --release --locked)
        so="$ROOT/target/release/libmakterm_core.so"
    else
        if command -v rustup >/dev/null 2>&1; then
            rustup target add "$triple" >/dev/null
        fi
        local upper
        upper="$(echo "$triple" | tr '[:lower:]-' '[:upper:]_')"
        (cd "$ROOT" && env \
            "CARGO_TARGET_${upper}_LINKER=$linker" \
            "CC_${triple//-/_}=$linker" \
            "AR_${triple//-/_}=$AR_BIN" \
            cargo build --release --locked --target "$triple")
        so="$ROOT/target/$triple/release/libmakterm_core.so"
    fi

    local dest="$PROJECT/core/main/src/main/jniLibs/$abi"
    mkdir -p "$dest"
    cp "$so" "$dest/libmakterm_core.so"
    echo "built $abi: $dest/libmakterm_core.so"
}

build_one "arm64-v8a" "aarch64-linux-android" "aarch64-linux-android$API"
build_one "armeabi-v7a" "armv7-linux-androideabi" "armv7a-linux-androideabi$API"
build_one "x86_64" "x86_64-linux-android" "x86_64-linux-android$API"
build_one "x86" "i686-linux-android" "i686-linux-android$API"
