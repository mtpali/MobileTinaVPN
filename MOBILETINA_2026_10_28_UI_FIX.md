# Subscription, manual dock and notification revision — 2026-10-28

Applies to both MobileTina and AliMTP on the existing v2rayNG 2.3.14 branches.
Starting heads: MobileTina `3d0893df31fb778f73fc36a6262aebf840f9d55c`,
AliMTP `ecc1d3fc0a073326be19f41117d74eff6e98c7da`.
The user reported successful use of the previous APKs and supplied three screenshots
for this revision; that feedback does not certify every device acceptance case.

## Subscription card

The dark dot at the far end of the traffic bar is Material's default stop
indicator. The product View bar explicitly sets `trackStopIndicatorSize=0dp` and
`indicatorTrackGapSize=0dp`. The Compose counterpart also removes the stop indicator
and gap. The result is one continuous quota bar without a separate endpoint dot.

`SubscriptionUsageInfo.trafficExhausted` is a derived property: positive quota and
upload + download greater than or equal to that quota. It is not a persisted field.
Both the legacy card and Compose information card use this rule. Once exhausted,
the time/status figure reads **اتمام حجم**, even when the time limit is still in
the future or absent. Zero/negative/missing quota means unlimited/unknown and does
not trigger this label. Otherwise the existing time display stays in effect.
This is display state; it does not delete profiles or add a new VPN-stop policy.

Two UI-model regression tests cover exact-limit usage before time expiry and
exhaustion without a time limit versus unlimited/unknown quota. Existing over-quota
and below-limit/unlimited tests also check the exhaustion state.

## Manual mode controls

The existing 154dp dock gets an ID, `manual_dock`. Smart Connect's top margin moves
72dp to 64dp, and the selected server/ping row moves 128dp to 120dp: both rise 8dp.
The 92dp VPN image, -28dp offset and 145x52dp Smart Connect image are unchanged.

An ordinary tap on the blank dock while the manual screen has an active VPN calls
the existing `handlePingClick()` and current-server ping path. The dock is clickable
and focusable only while running and not Smart Connecting; the callback checks the
mode and running state again. Child stop/connect buttons retain their own click
handlers, and the existing selected-server row handler remains. No parent touch
interception, additional ping service or foreground notification is introduced.

## Notification

Both RemoteViews use an explicit LTR horizontal row and center the title/details
column and stop control vertically. Expanded traffic remains below the title in
the weighted text column. The stop button no longer sits on a separate bottom row.
Title overflow is bounded and the two-language text directions are explicit.

The white 140x32dp button, black bold 11sp text, 12dp corners and 148x48dp touch
target remain. Centered text uses sans-serif-medium. Existing stop PendingIntent,
ongoing foreground-service behavior, traffic updates and compact/expanded controls
remain intact; system/OEM notification chrome is outside the custom layout.

## Preservation and validation

No artwork, icon inset, package ID, versionCode, native input, signing policy,
subscription restriction/vault record or widget ownership changes in this revision.
The version remains 2.3.14/754 (ARM release versionCode 4000754). Source/XML/artwork
and personal-edition audits run locally. Existing CI runs both distribution unit
suites, debug builds, hardened ARM release and signature/native/GeoData gates.
Archive the exact successful head/run/APK/hash/mapping/public certificate and
updated complete handoff for the UI-fix outputs. Fresh device checks for the
reported visuals, tap areas and OEM notification remain separate from CI results.
