# Plain VLESS compatibility and icon adjustment

The reported URI uses TCP, an empty `security`, and `encryption=none`. Import
preserves these values correctly. The rejection happens when Xray builds the
outbound, before a network connection is attempted.

Reference: `mtpali/PattNG` main at
`3d78f994f63ba90e430820edcca1d8029ce14308`, AndroidLibXrayLite
`161a86dc18a97adac49bcf4a817768d7715fdfde`. Its go.mod replaces XTLS core with
`patterniha/xray-core` at `80febe90cfce644fb969973a286421ebceea5617`.
That fork comments out the entire outbound transport-security policy call.

These two editions keep the pinned upstream core and their AmneziaWG/recovery
patches. A separate compatibility patch removes only the public-address
plaintext VLESS rejection. It does not change VLESS encryption/flow validation,
TLS or REALITY settings, Trojan policy, DNS, the server address, or credentials.
Plain VLESS retains exactly the transport protection requested by its profile.

The native build first reproduces the original error on the unmodified core,
then checks empty/omitted/none security, flat and legacy JSON, public IPv4/IPv6,
TLS, invalid fields and the preserved Trojan policy. A real local VLESS client
and server exchange a payload through the patched core. The client uses a
public-shaped hostname mapped to loopback by static test DNS. No actual user
credentials or public server are used in tests.

The AAR cache includes test reports bound to its source hashes and native-library
hashes. A changed patch or test invalidates the cache. Both ARM libraries must
omit the removed VLESS rejection and retain the Trojan rejection string.

MobileTina's adaptive foreground inset changes from 18% to 16%, one 2-percentage-
point step larger. The supplied artwork pixels and all AliMTP artwork remain
unchanged. App version remains 2.3.13.

Phone acceptance, including the user's real endpoint, remains separate from the
automated core round-trip and build checks. PRs remain draft and unmerged.
