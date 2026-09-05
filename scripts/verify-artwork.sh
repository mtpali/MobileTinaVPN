#!/usr/bin/env bash
set -euo pipefail

# These checks guarantee the exact WebP files supplied for the personal edition are committed
# byte-for-byte. The launcher WebP is the uncropped icon centered on a larger black canvas.
sha256sum -c <<'EOF'
0556978fd6eb7a20b1e23d355ad70ceeb5011600dab7ee13cad05a5204a2ed7e  V2rayNG/app/src/main/res/drawable-nodpi/auto.webp
e427b8cba69fb81f27914ef7fd4b3c201008b7d0d5e5f7495f6de8122da59e96  V2rayNG/app/src/main/res/drawable-nodpi/blue.webp
35cd0c4fccc816a381d38444b8de7db6a946c874958877c19ee0bcbcbdee9246  V2rayNG/app/src/main/res/drawable-nodpi/fab.webp
3a6486519985de5dbbd4d36e30d20d42cc0fa5461476436d1cc84a96cd2626f4  V2rayNG/app/src/main/res/drawable-nodpi/nav.webp
c96116b957ccfbf8dfcc59217d7fefbab184a0771e644a14034f476d4648badf  V2rayNG/app/src/main/res/drawable-nodpi/red.webp
75bb2106f995ee54b71ef4aa4803524761d3111c11f6e1b98ba3447d4e743661  V2rayNG/app/src/main/res/drawable-nodpi/stop.webp
8d40ffcf8dbbdc2aae3bee9576199375f4a6416de9f404fc75e70c0ff7632a7f  V2rayNG/app/src/main/res/drawable-nodpi/white.webp
4be7b6b3e6d95bf855589c3fb3a7064ce78a8a0e37f760e39d541deffc1b08c0  V2rayNG/app/src/main/res/drawable-nodpi/yellow.webp
5d1c3fa178c50c4925adae689bf875afc7fc69c4936b3e59122d719af262c664  V2rayNG/app/src/main/res/mipmap-nodpi/icon.webp
5d1c3fa178c50c4925adae689bf875afc7fc69c4936b3e59122d719af262c664  V2rayNG/app/src/main/res/mipmap-nodpi/ic_launcher_foreground.webp
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
