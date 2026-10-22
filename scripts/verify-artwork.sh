#!/usr/bin/env bash
set -euo pipefail

# These checks guarantee the exact WebP files supplied for MobileTina are committed
# byte-for-byte. No conversion, resize, or recompression is allowed for app artwork.
sha256sum -c <<'EOF'
0556978fd6eb7a20b1e23d355ad70ceeb5011600dab7ee13cad05a5204a2ed7e  V2rayNG/app/src/main/res/drawable-nodpi/auto.webp
cd534167f656231001e6afcfdf9fa945a25421ad26fb1200fed89b9549e300a1  V2rayNG/app/src/main/res/drawable-nodpi/blue.webp
dd42c60232c45e87cb009b1f41959f785958e0f27e180b89be46ff86359fe494  V2rayNG/app/src/main/res/drawable-nodpi/fab.webp
b93ea494eb517d9fe940e7780dd2eab58ed5fd3994f6dd876114b8a4db4039c8  V2rayNG/app/src/main/res/drawable-nodpi/nav.webp
fe71582a07b55e25e825eb8016662433afc10cfdbc5a3b5c27565cf6602a41f3  V2rayNG/app/src/main/res/drawable-nodpi/red.webp
87ec504cdc3c7ac3287e32140f3a5649df8d2162e7312e752a8748cc50831c2e  V2rayNG/app/src/main/res/drawable-nodpi/stop.webp
cd00d42eed21c4790e94ce7dbd62740304dbce31c25bc8cb2ac492bd4a95d3b3  V2rayNG/app/src/main/res/drawable-nodpi/white.webp
579e3de49a56f29c36f50b0e3f5ebe15df6c05ddad670e100c914e03916c6a5b  V2rayNG/app/src/main/res/drawable-nodpi/yellow.webp
4530725a0c14798ec265738d35e8b0409187377b695e716eaff2575d35cde1c2  V2rayNG/app/src/main/res/mipmap-nodpi/icon.webp
4530725a0c14798ec265738d35e8b0409187377b695e716eaff2575d35cde1c2  V2rayNG/app/src/main/res/mipmap-nodpi/ic_launcher_foreground.webp
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
