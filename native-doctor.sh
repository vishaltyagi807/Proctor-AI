#!/usr/bin/env sh
set -u

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
FRONTEND_DIR="$ROOT_DIR/frontend"
TAURI_DIR="$FRONTEND_DIR/src-tauri"

if [ -t 1 ]; then
    BOLD=$(printf '\033[1m')
    DIM=$(printf '\033[2m')
    GREEN=$(printf '\033[32m')
    YELLOW=$(printf '\033[33m')
    RED=$(printf '\033[31m')
    RESET=$(printf '\033[0m')
else
    BOLD=""
    DIM=""
    GREEN=""
    YELLOW=""
    RED=""
    RESET=""
fi

COMMON_FAILED=0
DESKTOP_FAILED=0
ANDROID_FAILED=0
IOS_FAILED=0
COMMON_WARNED=0
DESKTOP_WARNED=0
ANDROID_WARNED=0
IOS_WARNED=0
GROUP=COMMON

usage() {
    cat <<EOF
Usage: ./native-doctor.sh [all|desktop|android|ios]

Checks that this machine has everything needed to build the ProctorAI desktop
and mobile apps. Nothing is installed or changed; every missing item comes with
the command that installs it.

Targets:
  all       Check desktop, Android and iOS (default)
  desktop   Check the desktop app for this operating system
  android   Check Android APK and AAB builds
  ios       Check iOS builds (macOS only)
EOF
}

section() { printf '\n%s%s%s\n' "$BOLD" "$1" "$RESET"; }
pass() { printf '  %s✔%s %s\n' "$GREEN" "$RESET" "$1"; }
hint() { [ -n "${1:-}" ] && printf '      %s→ %s%s\n' "$DIM" "$1" "$RESET"; return 0; }

miss() {
    printf '  %s✘%s %s\n' "$RED" "$RESET" "$1"
    hint "${2:-}"
    eval "${GROUP}_FAILED=\$((${GROUP}_FAILED + 1))"
}

caution() {
    printf '  %s!%s %s\n' "$YELLOW" "$RESET" "$1"
    hint "${2:-}"
    eval "${GROUP}_WARNED=\$((${GROUP}_WARNED + 1))"
}

info() { printf '  %s·%s %s\n' "$DIM" "$RESET" "$1"; }

has() { command -v "$1" >/dev/null 2>&1; }

version_ge() {
    awk -v have="$1" -v need="$2" 'BEGIN {
        split(have, a, "."); split(need, b, ".")
        for (i = 1; i <= 3; i++) {
            if ((a[i] + 0) > (b[i] + 0)) exit 0
            if ((a[i] + 0) < (b[i] + 0)) exit 1
        }
        exit 0
    }'
}

first_version() {
    printf '%s' "$1" | grep -oE '[0-9]+(\.[0-9]+){1,2}' | head -n 1
}

detect_os() {
    case "$(uname -s 2>/dev/null || echo unknown)" in
        Linux*)
            OS=linux
            if grep -qi microsoft /proc/version 2>/dev/null; then
                OS=wsl
            fi
            ;;
        Darwin*) OS=macos ;;
        MINGW* | MSYS* | CYGWIN*) OS=windows ;;
        *) OS=unknown ;;
    esac
    DISTRO=""
    if [ -f /etc/os-release ]; then
        DISTRO=$(. /etc/os-release && printf '%s %s' "${ID:-}" "${ID_LIKE:-}")
    fi
}

linux_family() {
    case " $DISTRO " in
        *" debian "* | *" ubuntu "* | *" linuxmint "* | *" pop "*) printf 'apt' ;;
        *" fedora "* | *" rhel "* | *" centos "*) printf 'dnf' ;;
        *" arch "* | *" manjaro "*) printf 'pacman' ;;
        *" opensuse"* | *" suse "*) printf 'zypper' ;;
        *) printf 'unknown' ;;
    esac
}

linux_deps_hint() {
    case "$(linux_family)" in
        apt) printf 'sudo apt install libwebkit2gtk-4.1-dev build-essential curl wget file libxdo-dev libssl-dev libayatana-appindicator3-dev librsvg2-dev' ;;
        dnf) printf 'sudo dnf install webkit2gtk4.1-devel openssl-devel curl wget file libappindicator-gtk3-devel librsvg2-devel libxdo-devel && sudo dnf group install "c-development"' ;;
        pacman) printf 'sudo pacman -S --needed webkit2gtk-4.1 base-devel curl wget file openssl appmenu-gtk-module libappindicator-gtk3 librsvg xdotool' ;;
        zypper) printf 'sudo zypper in webkit2gtk3-devel libopenssl-devel curl wget file libappindicator3-1 librsvg-devel && sudo zypper in -t pattern devel_basis' ;;
        *) printf 'Install WebKitGTK 4.1, GTK 3, libsoup 3, librsvg, libxdo and a C toolchain: https://v2.tauri.app/start/prerequisites/' ;;
    esac
}

pkg_hint() {
    if [ "$OS" = macos ]; then
        printf 'brew install %s' "$1"
    elif [ "$OS" = windows ]; then
        printf 'winget install %s' "$2"
    else
        case "$(linux_family)" in
            apt) printf 'sudo apt install %s' "$3" ;;
            dnf) printf 'sudo dnf install %s' "$3" ;;
            pacman) printf 'sudo pacman -S %s' "$3" ;;
            zypper) printf 'sudo zypper in %s' "$3" ;;
            *) printf 'Install %s with your package manager' "$3" ;;
        esac
    fi
}

free_gb() {
    df -Pk "$1" 2>/dev/null | awk 'NR == 2 { printf "%d", $4 / 1048576 }'
}

rust_target_installed() {
    printf '%s\n' "$RUST_TARGETS" | grep -qx "$1"
}

check_rust_targets() {
    missing=""
    for target in "$@"; do
        rust_target_installed "$target" || missing="$missing $target"
    done
    if [ -z "$missing" ]; then
        pass "Rust targets:$(printf ' %s' "$@")"
    else
        miss "Rust targets missing:$missing" "rustup target add$missing"
    fi
}

check_common() {
    GROUP=COMMON
    section "Common toolchain"

    if has node; then
        node_version=$(first_version "$(node --version 2>/dev/null)")
        node_major=${node_version%%.*}
        if version_ge "$node_version" 22.12.0 || { [ "$node_major" = 20 ] && version_ge "$node_version" 20.19.0; }; then
            pass "Node.js $node_version"
        else
            miss "Node.js $node_version is too old. Vite 8 needs 20.19+ or 22.12+" "Install the current LTS: https://nodejs.org"
        fi
    else
        miss "Node.js is not installed" "Install the current LTS: https://nodejs.org"
    fi

    if has npm; then
        pass "npm $(npm --version 2>/dev/null)"
    else
        miss "npm is not installed" "npm ships with Node.js: https://nodejs.org"
    fi

    cli_pkg="$FRONTEND_DIR/node_modules/@tauri-apps/cli/package.json"
    if [ -f "$cli_pkg" ]; then
        pass "Frontend dependencies installed (Tauri CLI $(sed -n 's/^ *"version": *"\([^"]*\)".*/\1/p' "$cli_pkg" | head -n 1))"
    else
        miss "Frontend dependencies are not installed" "cd frontend && npm ci"
    fi

    rust_needed=$(sed -n 's/^rust-version *= *"\([^"]*\)".*/\1/p' "$TAURI_DIR/Cargo.toml" 2>/dev/null | head -n 1)
    rust_needed=${rust_needed:-1.90}
    if has rustc && has cargo; then
        rust_version=$(first_version "$(rustc --version 2>/dev/null)")
        if version_ge "$rust_version" "$rust_needed"; then
            pass "Rust $rust_version (needs $rust_needed+)"
        else
            miss "Rust $rust_version is older than the required $rust_needed" "rustup update stable"
        fi
    else
        miss "Rust is not installed" "curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh"
    fi

    RUST_TARGETS=""
    if has rustup; then
        RUST_TARGETS=$(rustup target list --installed 2>/dev/null)
        pass "rustup $(first_version "$(rustup --version 2>/dev/null)")"
    else
        miss "rustup is not installed, so Rust targets cannot be added" "Install Rust with rustup: https://rustup.rs"
    fi

    missing_files=""
    for file in src-tauri/tauri.conf.json src-tauri/Cargo.toml src-tauri/capabilities/default.json \
        src-tauri/icons/32x32.png src-tauri/icons/128x128.png src-tauri/icons/128x128@2x.png \
        src-tauri/icons/icon.icns src-tauri/icons/icon.ico public/favicon.svg; do
        [ -f "$FRONTEND_DIR/$file" ] || missing_files="$missing_files $file"
    done
    if [ -z "$missing_files" ]; then
        pass "Tauri project files and icons present"
    else
        miss "Project files missing:$missing_files" "Icons: cd frontend && npm run icons"
    fi

    server=${VITE_SERVER_URL:-}
    if [ -z "$server" ] && [ -f "$FRONTEND_DIR/.env.native" ]; then
        server=$(sed -n 's/^VITE_SERVER_URL=//p' "$FRONTEND_DIR/.env.native" | tail -n 1)
    fi
    if [ -n "$server" ]; then
        pass "Apps preconnect to $server"
    else
        info "VITE_SERVER_URL is not set, so the apps ask for the server address at sign-in"
    fi
}

check_desktop_linux() {
    if has pkg-config; then
        pass "pkg-config"
    else
        miss "pkg-config is not installed" "$(linux_deps_hint)"
    fi
    missing_libs=""
    for module in webkit2gtk-4.1 javascriptcoregtk-4.1 libsoup-3.0 gtk+-3.0 librsvg-2.0; do
        if has pkg-config && pkg-config --exists "$module" 2>/dev/null; then
            :
        else
            missing_libs="$missing_libs $module"
        fi
    done
    if [ -z "$missing_libs" ]; then
        pass "WebKitGTK $(pkg-config --modversion webkit2gtk-4.1 2>/dev/null), GTK $(pkg-config --modversion gtk+-3.0 2>/dev/null), libsoup $(pkg-config --modversion libsoup-3.0 2>/dev/null), librsvg"
    else
        miss "Development libraries missing:$missing_libs" "$(linux_deps_hint)"
    fi
    if [ -f /usr/include/xdo.h ]; then
        pass "libxdo"
    else
        miss "libxdo development headers are missing" "$(linux_deps_hint)"
    fi
    if has cc || has gcc; then
        pass "C compiler"
    else
        miss "No C compiler found" "$(linux_deps_hint)"
    fi
    if has file; then
        pass "file (needed for AppImage bundles)"
    else
        miss "file is not installed (needed for AppImage bundles)" "$(linux_deps_hint)"
    fi
    if has pkg-config && pkg-config --exists ayatana-appindicator3-0.1 2>/dev/null; then
        pass "libayatana-appindicator"
    else
        info "libayatana-appindicator is not installed. Only needed if the app adds a tray icon"
    fi
    DESKTOP_FORMATS="deb, rpm, AppImage"
}

check_desktop_macos() {
    if xcode-select -p >/dev/null 2>&1; then
        pass "Xcode command line tools ($(xcode-select -p))"
    else
        miss "Xcode command line tools are not installed" "xcode-select --install"
    fi
    if has clang; then
        pass "clang $(first_version "$(clang --version 2>/dev/null)")"
    else
        miss "clang is not available" "xcode-select --install"
    fi
    missing=""
    for target in aarch64-apple-darwin x86_64-apple-darwin; do
        rust_target_installed "$target" || missing="$missing $target"
    done
    if [ -z "$missing" ]; then
        pass "Rust targets for universal macOS builds"
    else
        caution "Universal (Intel + Apple Silicon) builds need Rust targets:$missing" "rustup target add$missing"
    fi
    identities=$(security find-identity -v -p codesigning 2>/dev/null | grep -c "Developer ID Application" || true)
    if [ "${identities:-0}" -gt 0 ]; then
        pass "Developer ID signing identity found"
    else
        caution "No Developer ID certificate. Builds run locally but Gatekeeper blocks them on other Macs" "Add a Developer ID Application certificate in Xcode, then set APPLE_SIGNING_IDENTITY"
    fi
    if [ -n "${APPLE_ID:-}${APPLE_API_KEY:-}" ]; then
        pass "Notarization credentials set"
    else
        info "Set APPLE_ID, APPLE_PASSWORD and APPLE_TEAM_ID (or APPLE_API_KEY) to notarize release builds"
    fi
    DESKTOP_FORMATS="app, dmg"
}

check_desktop_windows() {
    host=$(rustc -vV 2>/dev/null | sed -n 's/^host: //p')
    case "$host" in
        *msvc*) pass "Rust toolchain $host" ;;
        "") ;;
        *) miss "Rust uses $host. Tauri on Windows needs the MSVC toolchain" "rustup default stable-msvc" ;;
    esac
    vswhere="/c/Program Files (x86)/Microsoft Visual Studio/Installer/vswhere.exe"
    if [ -x "$vswhere" ] && [ -n "$("$vswhere" -products '*' -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath 2>/dev/null)" ]; then
        pass "Microsoft C++ Build Tools"
    else
        miss "Microsoft C++ Build Tools (Desktop development with C++) are not installed" "winget install Microsoft.VisualStudio.2022.BuildTools --override \"--add Microsoft.VisualStudio.Workload.VCTools --includeRecommended\""
    fi
    webview=""
    for key in 'HKLM\SOFTWARE\WOW6432Node\Microsoft\EdgeUpdate\Clients\{F3017226-FE2A-4295-8BDF-00C3A9A7E4C5}' \
        'HKCU\Software\Microsoft\EdgeUpdate\Clients\{F3017226-FE2A-4295-8BDF-00C3A9A7E4C5}'; do
        webview=$(MSYS_NO_PATHCONV=1 reg query "$key" /v pv 2>/dev/null | awk '/pv/ { print $3 }')
        [ -n "$webview" ] && break
    done
    if [ -n "$webview" ]; then
        pass "WebView2 runtime $webview"
    else
        miss "WebView2 runtime is not installed" "winget install Microsoft.EdgeWebView2Runtime"
    fi
    info "Tauri downloads WiX and NSIS automatically on the first build"
    DESKTOP_FORMATS="msi, exe"
}

check_desktop() {
    GROUP=DESKTOP
    DESKTOP_FORMATS=""
    case "$OS" in
        linux | wsl)
            section "Desktop app (Linux)"
            [ "$OS" = wsl ] && info "WSL builds Linux packages. Running the app needs WSLg, and Windows installers must be built on Windows"
            check_desktop_linux
            ;;
        macos)
            section "Desktop app (macOS)"
            check_desktop_macos
            ;;
        windows)
            section "Desktop app (Windows)"
            check_desktop_windows
            ;;
        *)
            section "Desktop app"
            miss "Unsupported operating system: $(uname -s 2>/dev/null)" "Build on Windows, macOS or Linux"
            ;;
    esac
    space=$(free_gb "$FRONTEND_DIR")
    if [ -n "$space" ] && [ "$space" -lt 5 ]; then
        caution "Only ${space} GB free. A release build needs about 5 GB" "Free some disk space before building"
    fi
}

check_android() {
    GROUP=ANDROID
    section "Android (APK and AAB)"

    java_bin=""
    if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
        java_bin="$JAVA_HOME/bin/java"
    elif has java; then
        java_bin=$(command -v java)
    fi
    if [ -n "$java_bin" ]; then
        java_version=$(first_version "$("$java_bin" -version 2>&1 | head -n 1)")
        java_major=${java_version%%.*}
        if [ "$java_major" = 1 ]; then
            java_major=$(printf '%s' "$java_version" | cut -d. -f2)
        fi
        if [ "${java_major:-0}" -ge 17 ] 2>/dev/null; then
            if [ -x "$(dirname "$java_bin")/javac" ]; then
                pass "JDK $java_version"
            else
                miss "Java $java_version is a runtime only. Gradle needs a full JDK" "Install JDK 17 or 21 (Android Studio ships one): $(pkg_hint openjdk@17 EclipseAdoptium.Temurin.17.JDK openjdk-17-jdk)"
            fi
            [ -n "${JAVA_HOME:-}" ] || caution "JAVA_HOME is not set" "export JAVA_HOME=\"$(dirname "$(dirname "$java_bin")")\""
        else
            miss "Java $java_version is too old. The Android build needs JDK 17+" "$(pkg_hint openjdk@17 EclipseAdoptium.Temurin.17.JDK openjdk-17-jdk)"
        fi
    else
        miss "Java is not installed. The Android build needs JDK 17+" "$(pkg_hint openjdk@17 EclipseAdoptium.Temurin.17.JDK openjdk-17-jdk)"
    fi

    sdk=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
    if [ -z "$sdk" ]; then
        for candidate in "$HOME/Android/Sdk" "$HOME/Library/Android/sdk" "${LOCALAPPDATA:-}/Android/Sdk"; do
            [ -d "$candidate" ] && sdk=$candidate && break
        done
        if [ -n "$sdk" ]; then
            miss "ANDROID_HOME is not set (an SDK was found at $sdk)" "export ANDROID_HOME=\"$sdk\""
        else
            miss "Android SDK not found" "Install Android Studio (https://developer.android.com/studio) and set ANDROID_HOME"
        fi
    elif [ -d "$sdk" ]; then
        pass "Android SDK at $sdk"
    else
        miss "ANDROID_HOME points to $sdk, which does not exist" "Set ANDROID_HOME to your Android SDK folder"
        sdk=""
    fi

    if [ -n "$sdk" ]; then
        sdkmanager=$(ls "$sdk"/cmdline-tools/*/bin/sdkmanager 2>/dev/null | head -n 1)
        if [ -n "$sdkmanager" ]; then
            pass "Android SDK command line tools"
        else
            caution "Android SDK command line tools are not installed" "Android Studio > Settings > Android SDK > SDK Tools > Android SDK Command-line Tools"
            sdkmanager="sdkmanager"
        fi

        if [ -x "$sdk/platform-tools/adb" ] || [ -x "$sdk/platform-tools/adb.exe" ]; then
            pass "Android platform tools (adb)"
        else
            caution "Android platform tools are not installed (needed to run on a device)" "$sdkmanager \"platform-tools\""
        fi

        compile_sdk=$(sed -n 's/^ *compileSdk *= *\([0-9][0-9]*\).*/\1/p' "$TAURI_DIR/gen/android/app/build.gradle.kts" 2>/dev/null | head -n 1)
        compile_sdk=${compile_sdk:-37}
        if [ -d "$sdk/platforms/android-$compile_sdk" ]; then
            pass "Android platform $compile_sdk"
        else
            caution "Android platform $compile_sdk is not installed. Gradle downloads it if the SDK licenses are accepted" "$sdkmanager \"platforms;android-$compile_sdk\""
        fi

        build_tools=$(ls "$sdk/build-tools" 2>/dev/null | sort -V | tail -n 1)
        if [ -n "$build_tools" ]; then
            pass "Android build tools $build_tools"
        else
            caution "Android build tools are not installed. Gradle downloads them if the SDK licenses are accepted" "$sdkmanager \"build-tools;36.0.0\""
        fi

        if [ -f "$sdk/licenses/android-sdk-license" ]; then
            pass "Android SDK licenses accepted"
        else
            miss "Android SDK licenses are not accepted" "$sdkmanager --licenses"
        fi

        ndk_found=$(ls -d "$sdk"/ndk/*/ 2>/dev/null | sort -V | tail -n 1)
        ndk_found=${ndk_found%/}
        if [ -n "${NDK_HOME:-}" ] && [ -f "$NDK_HOME/source.properties" ]; then
            pass "Android NDK $(sed -n 's/^Pkg.Revision *= *//p' "$NDK_HOME/source.properties")"
        elif [ -n "${NDK_HOME:-}" ]; then
            miss "NDK_HOME points to $NDK_HOME, which is not an NDK" "export NDK_HOME=\"${ndk_found:-$sdk/ndk/<version>}\""
        elif [ -n "$ndk_found" ]; then
            miss "NDK_HOME is not set (an NDK was found at $ndk_found)" "export NDK_HOME=\"$ndk_found\""
        else
            miss "Android NDK is not installed" "$sdkmanager \"ndk;27.3.13750724\" and export NDK_HOME=\"$sdk/ndk/27.3.13750724\""
        fi
    fi

    check_rust_targets aarch64-linux-android armv7-linux-androideabi i686-linux-android x86_64-linux-android

    manifest="$TAURI_DIR/gen/android/app/src/main/AndroidManifest.xml"
    if [ -f "$manifest" ]; then
        pass "Android project generated (src-tauri/gen/android)"
        if grep -q 'android.permission.CAMERA' "$manifest"; then
            pass "Camera permission declared"
        else
            miss "The Android manifest does not declare the camera permission" "cd frontend && node scripts/android-manifest.mjs"
        fi
        if grep -rqs "signingConfig" "$TAURI_DIR/gen/android/app/build.gradle.kts"; then
            pass "Release signing configured in Gradle"
        else
            caution "Release signing is not configured. Release APKs are unsigned and will not install" "Add a signingConfigs block: https://v2.tauri.app/distribute/sign/android/"
        fi
    else
        caution "Android project not generated yet" "cd frontend && npm run tauri:android:init"
    fi

    if [ -n "$sdk" ] && { [ -x "$sdk/platform-tools/adb" ] || [ -x "$sdk/platform-tools/adb.exe" ]; }; then
        devices=$("$sdk/platform-tools/adb" devices 2>/dev/null | awk 'NR > 1 && $2 == "device"' | wc -l | tr -d ' ')
        info "Connected Android devices or emulators: ${devices:-0}"
    fi

    space=$(free_gb "$FRONTEND_DIR")
    if [ -n "$space" ] && [ "$space" -lt 15 ]; then
        caution "Only ${space} GB free. Android builds need about 15 GB for Gradle, the NDK and four Rust targets" "Free some disk space before building"
    fi
}

check_ios() {
    GROUP=IOS
    section "iOS"
    if [ "$OS" != macos ]; then
        info "iOS apps can only be built on a Mac with Xcode"
        IOS_SKIPPED=1
        return
    fi
    IOS_SKIPPED=0

    developer_dir=$(xcode-select -p 2>/dev/null)
    case "$developer_dir" in
        *Xcode*.app*)
            if has xcodebuild; then
                pass "$(xcodebuild -version 2>/dev/null | head -n 1)"
            else
                miss "xcodebuild is not available" "Install Xcode from the App Store"
            fi
            if xcodebuild -checkFirstLaunchStatus >/dev/null 2>&1; then
                pass "Xcode first launch completed"
            else
                miss "Xcode needs its first-launch setup and license" "sudo xcodebuild -runFirstLaunch && sudo xcodebuild -license accept"
            fi
            ;;
        *)
            miss "Full Xcode is required for iOS (only the command line tools are selected)" "Install Xcode from the App Store, then: sudo xcode-select -s /Applications/Xcode.app"
            ;;
    esac

    if has pod; then
        pass "CocoaPods $(pod --version 2>/dev/null)"
    else
        miss "CocoaPods is not installed" "brew install cocoapods"
    fi
    if has xcodegen; then
        pass "XcodeGen $(first_version "$(xcodegen --version 2>/dev/null)")"
    else
        caution "XcodeGen is not installed. Tauri installs it with Homebrew on the first init" "brew install xcodegen"
    fi

    check_rust_targets aarch64-apple-ios aarch64-apple-ios-sim x86_64-apple-ios

    if xcrun simctl list runtimes 2>/dev/null | grep -q "iOS"; then
        pass "iOS simulator runtime"
    else
        caution "No iOS simulator runtime installed" "Xcode > Settings > Components > iOS"
    fi

    if [ -n "${APPLE_DEVELOPMENT_TEAM:-}" ] || grep -q '"developmentTeam"' "$TAURI_DIR/tauri.conf.json" 2>/dev/null; then
        pass "Apple development team configured"
    else
        caution "No Apple development team. Simulator builds work, device and App Store builds need one" "Set APPLE_DEVELOPMENT_TEAM or bundle.iOS.developmentTeam in tauri.conf.json"
    fi

    if [ -d "$TAURI_DIR/gen/apple" ]; then
        pass "iOS project generated (src-tauri/gen/apple)"
    else
        caution "iOS project not generated yet" "cd frontend && npm run tauri:ios:init"
    fi
}

status_line() {
    label=$1
    failed=$2
    warned=$3
    detail=$4
    if [ "$failed" -gt 0 ]; then
        printf '  %s✘%s %-10s %s%s blocking issue%s%s\n' "$RED" "$RESET" "$label" "$RED" "$failed" "$([ "$failed" -eq 1 ] || printf 's')" "$RESET"
    elif [ "$COMMON_FAILED" -gt 0 ]; then
        printf '  %s✘%s %-10s %sblocked by the common issues above%s\n' "$RED" "$RESET" "$label" "$RED" "$RESET"
    elif [ "$warned" -gt 0 ]; then
        printf '  %s✔%s %-10s ready %s(%s warning%s)%s %s\n' "$GREEN" "$RESET" "$label" "$YELLOW" "$warned" "$([ "$warned" -eq 1 ] || printf 's')" "$RESET" "$detail"
    else
        printf '  %s✔%s %-10s ready %s\n' "$GREEN" "$RESET" "$label" "$detail"
    fi
}

TARGET=${1:-all}
case "$TARGET" in
    help | -h | --help)
        usage
        exit 0
        ;;
    all | desktop | android | ios) ;;
    *)
        usage
        printf '\n%s[error] Unknown target: %s%s\n' "$RED" "$TARGET" "$RESET" >&2
        exit 2
        ;;
esac

[ -d "$TAURI_DIR" ] || {
    printf '%s[error] frontend/src-tauri not found next to this script.%s\n' "$RED" "$RESET" >&2
    exit 2
}

detect_os
printf '%sProctorAI native build doctor%s  %s(%s, %s)%s\n' "$BOLD" "$RESET" "$DIM" "$OS" "$(uname -m 2>/dev/null)" "$RESET"

check_common
IOS_SKIPPED=0
case "$TARGET" in
    all)
        check_desktop
        check_android
        check_ios
        ;;
    desktop) check_desktop ;;
    android) check_android ;;
    ios) check_ios ;;
esac

section "Summary"
blocked=0
if [ "$COMMON_FAILED" -gt 0 ]; then
    printf '  %s✘%s %-10s %s%s blocking issue%s, fix these first%s\n' "$RED" "$RESET" "Common" "$RED" "$COMMON_FAILED" "$([ "$COMMON_FAILED" -eq 1 ] || printf 's')" "$RESET"
    blocked=1
fi
if [ "$TARGET" = all ] || [ "$TARGET" = desktop ]; then
    status_line "Desktop" "$DESKTOP_FAILED" "$((DESKTOP_WARNED + COMMON_WARNED))" "${DESKTOP_FORMATS:+→ npm run tauri:build ($DESKTOP_FORMATS)}"
    [ $((DESKTOP_FAILED + COMMON_FAILED)) -gt 0 ] && blocked=1
fi
if [ "$TARGET" = all ] || [ "$TARGET" = android ]; then
    status_line "Android" "$ANDROID_FAILED" "$((ANDROID_WARNED + COMMON_WARNED))" "→ npm run tauri:android:build"
    [ $((ANDROID_FAILED + COMMON_FAILED)) -gt 0 ] && blocked=1
fi
if [ "$TARGET" = all ] || [ "$TARGET" = ios ]; then
    if [ "$IOS_SKIPPED" -eq 1 ]; then
        printf '  %s·%s %-10s needs a Mac with Xcode\n' "$DIM" "$RESET" "iOS"
        [ "$TARGET" = ios ] && blocked=1
    else
        status_line "iOS" "$IOS_FAILED" "$((IOS_WARNED + COMMON_WARNED))" "→ npm run tauri:ios:build"
        [ $((IOS_FAILED + COMMON_FAILED)) -gt 0 ] && blocked=1
    fi
fi
printf '\n'
exit "$blocked"
