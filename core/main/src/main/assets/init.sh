set -e  # Exit immediately on Failure

export PATH=/bin:/sbin:/usr/bin:/usr/sbin:/usr/share/bin:/usr/share/sbin:/usr/local/bin:/usr/local/sbin:/system/bin:/system/xbin
export HOME=/root

# Some images (systemd-based Arch in particular) ship resolv.conf as a dangling
# symlink, so writing through it would fail. Write a new file and rename it over
# the old path: rename replaces the symlink itself and needs no extra helpers.
if [ ! -s /etc/resolv.conf ]; then
    mkdir -p /etc 2>/dev/null || true
    if {
        echo "nameserver 8.8.8.8"
        echo "nameserver 1.1.1.1"
    } > /etc/resolv.conf.new 2>/dev/null; then
        mv -f /etc/resolv.conf.new /etc/resolv.conf 2>/dev/null || true
    fi
fi


export PS1='\[\033[01;32m\]\u@reterm\[\033[00m\]:\[\033[01;34m\]\w\[\033[00m\]\$ '
# shellcheck disable=SC2034
export PIP_BREAK_SYSTEM_PACKAGES=1

#fix linker warning
if [ ! -f /linkerconfig/ld.config.txt ]; then
    mkdir -p /linkerconfig 2>/dev/null || true
    touch /linkerconfig/ld.config.txt 2>/dev/null || true
fi

# Detect the installed distribution so first-boot provisioning works for
# Alpine (apk) and Arch Linux ARM (pacman) alike.
if command -v apk >/dev/null 2>&1; then
    DISTRO_PM=apk
elif command -v pacman >/dev/null 2>&1; then
    DISTRO_PM=pacman
else
    DISTRO_PM=none
fi

pacman_install() {
    if pacman -Sy --noconfirm --needed "$@"; then
        return 0
    fi
    # Arch Linux ARM tarballs occasionally ship an unpopulated keyring, which
    # makes every install fail with a signature error. Initialise it and retry.
    if command -v pacman-key >/dev/null 2>&1; then
        echo "ReTerminal: initialising the pacman keyring, this may take a minute..."
        pacman-key --init || true
        pacman-key --populate archlinuxarm || true
        pacman -Sy --noconfirm --needed "$@"
        return $?
    fi
    return 1
}

install_pkgs() {
    case "$DISTRO_PM" in
        apk) apk add "$@" ;;
        pacman) pacman_install "$@" ;;
        *) return 1 ;;
    esac
}

provision() {
    echo "ReTerminal: first boot setup - updating packages, installing bash curl git"
    case "$DISTRO_PM" in
        # `apk upgrade` rewrites the whole base system and is very slow under
        # proot, so only fetch what the terminal needs.
        apk) apk update && apk add bash curl git ;;
        pacman) pacman_install bash curl git ;;
        *)
            echo "ReTerminal: unknown distribution, skipping first boot setup"
            return 0
            ;;
    esac
}

if [ "$#" -eq 0 ]; then
    if [ ! -f /etc/reterm_provisioned ]; then
        if provision; then
            sed -i '/^root:/s|/bin/sh$|/bin/bash|' /etc/passwd 2>/dev/null || true
            touch /etc/reterm_provisioned
            echo "ReTerminal: first boot setup finished"
        else
            echo "ReTerminal: first boot setup failed, will retry next session"
        fi
    fi

    if [ -f /etc/profile ]; then
        . /etc/profile || true
    fi
    export PS1='\[\033[01;32m\]\u@reterm\[\033[00m\]:\[\033[01;34m\]\w\[\033[00m\]\$ '
    cd "$HOME" 2>/dev/null || cd / 2>/dev/null || true
    if [ -f /initrc ]; then
        . /initrc || true
    fi

    shell_bin=/bin/sh
    if [ -x /bin/bash ]; then
        shell_bin=/bin/bash
    fi
    case "${RETERM_LOGIN_SHELL:-0}" in
        1)
            if [ ! -x /bin/bash ]; then
                install_pkgs bash || true
            fi
            if [ -x /bin/bash ]; then
                shell_bin=/bin/bash
            else
                echo "ReTerminal: bash is not available, falling back to default shell"
            fi
            ;;
        2)
            shell_bin=/bin/sh
            ;;
        3)
            if [ -x /bin/ash ]; then
                shell_bin=/bin/ash
            elif [ -x /bin/sh ]; then
                shell_bin=/bin/sh
            fi
            ;;
    esac
    exec "$shell_bin"
else
    exec "$@"
fi
