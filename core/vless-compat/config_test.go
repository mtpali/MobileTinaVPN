package mobiletina_compat_test

import (
	"encoding/json"
	"fmt"
	"strings"
	"testing"

	"github.com/xtls/xray-core/infra/conf"
)

// These fixtures use synthetic credentials and never contact a public endpoint.
const compatID = "00000000-0000-4000-8000-000000000001"
const compatHost = "vless-compat.example.org"

func TestMobileTinaVlessCompatibility(t *testing.T) {
	flat := fmt.Sprintf(`{"address":%q,"port":30382,"id":%q,"encryption":"none"}`, compatHost, compatID)
	legacy := fmt.Sprintf(`{"vnext":[{"address":%q,"port":30382,"users":[{"id":%q,"encryption":"none"}]}]}`, compatHost, compatID)
	tests := []struct {
		name, protocol, settings, stream, wantError string
	}{
		{"empty-security", "vless", flat, `{"network":"tcp","security":"","tcpSettings":{"header":{"type":"none"}}}`, ""},
		{"omitted-security", "vless", flat, `{"network":"tcp"}`, ""},
		{"explicit-none", "vless", flat, `{"network":"tcp","security":"none"}`, ""},
		{"legacy-vnext", "vless", legacy, `{"network":"tcp"}`, ""},
		{"public-ipv4", "vless", strings.Replace(flat, compatHost, "1.1.1.1", 1), `{"network":"tcp"}`, ""},
		{"public-ipv6", "vless", strings.Replace(flat, compatHost, "2606:4700:4700::1111", 1), `{"network":"tcp"}`, ""},
		{"tls-still-accepted", "vless", flat, `{"network":"tcp","security":"tls","tlsSettings":{"serverName":"vless-compat.example.org"}}`, ""},
		{"invalid-encryption-still-rejected", "vless", strings.Replace(flat, `"encryption":"none"`, `"encryption":"invalid"`, 1), `{"network":"tcp"}`, `unsupported "encryption"`},
		{"invalid-flow-still-rejected", "vless", strings.TrimSuffix(flat, "}")+`,"flow":"invalid"}`, `{"network":"tcp"}`, `"flow" doesn't support`},
		{"trojan-restriction-preserved", "trojan", fmt.Sprintf(`{"address":%q,"port":443,"password":"synthetic-test"}`, compatHost), `{"network":"tcp"}`, "trojan without TLS is prohibited"},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			input := fmt.Sprintf(`{"protocol":%q,"tag":"proxy","settings":%s,"streamSettings":%s}`, tc.protocol, tc.settings, tc.stream)
			var outbound conf.OutboundDetourConfig
			if err := json.Unmarshal([]byte(input), &outbound); err != nil {
				t.Fatal(err)
			}
			built, err := outbound.Build()
			if tc.wantError != "" {
				if err == nil || !strings.Contains(err.Error(), tc.wantError) {
					t.Fatalf("expected %q, got %v", tc.wantError, err)
				}
				return
			}
			if err != nil {
				t.Fatal(err)
			}
			if built == nil || built.Tag != "proxy" {
				t.Fatal("outbound missing or tag changed")
			}
		})
	}
}
