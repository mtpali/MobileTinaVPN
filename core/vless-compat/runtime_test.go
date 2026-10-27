package mobiletina_compat_test

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net"
	"testing"
	"time"

	xnet "github.com/xtls/xray-core/common/net"
	"github.com/xtls/xray-core/core"
	"github.com/xtls/xray-core/infra/conf"
	_ "github.com/xtls/xray-core/main/distro/all"
)

func startCompatCore(t *testing.T, input string) *core.Instance {
	t.Helper()
	var config conf.Config
	if err := json.Unmarshal([]byte(input), &config); err != nil {
		t.Fatal(err)
	}
	built, err := config.Build()
	if err != nil {
		t.Fatal(err)
	}
	instance, err := core.New(built)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = instance.Close() })
	if err := instance.Start(); err != nil {
		t.Fatal(err)
	}
	return instance
}

func TestMobileTinaPlainVlessTCPRoundTrip(t *testing.T) {
	echo, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = echo.Close() })
	go func() {
		conn, acceptErr := echo.Accept()
		if acceptErr != nil {
			return
		}
		defer conn.Close()
		_ = conn.SetDeadline(time.Now().Add(10 * time.Second))
		_, _ = io.Copy(conn, conn)
	}()

	reservation, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	serverPort := reservation.Addr().(*net.TCPAddr).Port
	_ = reservation.Close()
	startCompatCore(t, fmt.Sprintf(`{
		"log":{"loglevel":"error"},
		"inbounds":[{"listen":"127.0.0.1","port":%d,"protocol":"vless",
			"settings":{"clients":[{"id":%q}],"decryption":"none"},
			"streamSettings":{"network":"tcp","security":"none"}}],
		"outbounds":[{"protocol":"freedom","settings":{"finalRules":[{"action":"allow"}]}}]
	}`, serverPort, compatID))

	// A public-shaped hostname exercises the original validator, while a static
	// DNS host maps it to our local server. No public DNS or proxy server is used.
	client := startCompatCore(t, fmt.Sprintf(`{
		"log":{"loglevel":"error"},
		"dns":{"hosts":{%q:"127.0.0.1"}},
		"outbounds":[{"tag":"proxy","protocol":"vless",
			"settings":{"address":%q,"port":%d,"id":%q,"encryption":"none"},
			"streamSettings":{"network":"tcp","security":"",
				"tcpSettings":{"header":{"type":"none"}},
				"sockopt":{"domainStrategy":"UseIP"}},"mux":{"enabled":false}}]
	}`, compatHost, compatHost, serverPort, compatID))

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	conn, err := core.Dial(ctx, client, xnet.TCPDestination(xnet.LocalHostIP, xnet.Port(echo.Addr().(*net.TCPAddr).Port)))
	if err != nil {
		t.Fatal(err)
	}
	defer conn.Close()
	payload := bytes.Repeat([]byte("MobileTina plain VLESS round trip\n"), 2048)
	result := make(chan error, 1)
	go func() {
		if _, writeErr := conn.Write(payload); writeErr != nil {
			result <- writeErr
			return
		}
		response := make([]byte, len(payload))
		if _, readErr := io.ReadFull(conn, response); readErr != nil {
			result <- readErr
			return
		}
		if !bytes.Equal(payload, response) {
			result <- fmt.Errorf("VLESS round-trip payload changed")
			return
		}
		result <- nil
	}()
	select {
	case err := <-result:
		if err != nil {
			t.Fatal(err)
		}
	case <-ctx.Done():
		t.Fatal("local VLESS handshake/data exchange timed out")
	}
}
