#!/usr/bin/env bash
set -euo pipefail

# These checks guarantee the exact WebP files supplied for MobileTina are committed
# byte-for-byte. No conversion, resize, or recompression is allowed for app artwork.
sha256sum -c <<'EOF'
0556978fd6eb7a20b1e23d355ad70ceeb5011600dab7ee13cad05a5204a2ed7e  V2rayNG/app/src/main/res/drawable-nodpi/auto.webp
63ed57f28a66e361daa747135253d8d07ab6129f46c67b23d14a8fd397f4adb8  V2rayNG/app/src/main/res/drawable-nodpi/blue.webp
dd42c60232c45e87cb009b1f41959f785958e0f27e180b89be46ff86359fe494  V2rayNG/app/src/main/res/drawable-nodpi/fab.webp
b93ea494eb517d9fe940e7780dd2eab58ed5fd3994f6dd876114b8a4db4039c8  V2rayNG/app/src/main/res/drawable-nodpi/nav.webp
b93250c6c425ba0722f8307d135eb530f5d54ddafb8260e06f3c2a247cef1c54  V2rayNG/app/src/main/res/drawable-nodpi/red.webp
87ec504cdc3c7ac3287e32140f3a5649df8d2162e7312e752a8748cc50831c2e  V2rayNG/app/src/main/res/drawable-nodpi/stop.webp
cd00d42eed21c4790e94ce7dbd62740304dbce31c25bc8cb2ac492bd4a95d3b3  V2rayNG/app/src/main/res/drawable-nodpi/white.webp
2139988a7e22839298f67991c66bf4dffe0e7e39bf276af339de7f7aca3edd67  V2rayNG/app/src/main/res/drawable-nodpi/yellow.webp
1109076e611de82121448502e62eabeadec836f730eac1bf95c3ad5e81112d25  V2rayNG/app/src/main/res/mipmap-nodpi/icon.webp
1109076e611de82121448502e62eabeadec836f730eac1bf95c3ad5e81112d25  V2rayNG/app/src/main/res/mipmap-nodpi/ic_launcher_foreground.webp
EOF

if find V2rayNG/app/src/main/res -type f \( \
    -name 'mt_auto_*' -o \
    -name 'mt_manual_*' -o \
    -name 'mt_nav.*' -o \
    -name 'ic_launcher.png' -o \
    -name 'ic_launcher_round.png' -o \
    -name 'ic_launcher_foreground.png' \
\) -print -quit | grep -q .; then
    echo 'Legacy MobileTina artwork still exists in Android resources' >&2
    exit 1
fi
