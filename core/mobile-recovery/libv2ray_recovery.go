package libv2ray

import "github.com/xtls/xray-core/proxy/wireguard"

// RefreshAmneziaBindings is called off the Android main thread after resume.
func RefreshAmneziaBindings() error {
    return wireguard.RefreshAmneziaBindings()
}
