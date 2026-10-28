package dispatcher

import (
	"bytes"
	"context"
	"math"
	"sync"
	"testing"
	"github.com/xtls/xray-core/common/buf"
	"github.com/xtls/xray-core/common/net"
	"github.com/xtls/xray-core/common/serial"
	"github.com/xtls/xray-core/common/session"
	"github.com/xtls/xray-core/features/outbound"
	"github.com/xtls/xray-core/transport"
)
type trafficTestHandler struct { outbound.Handler; kind string }
func (h trafficTestHandler) ProxySettings() *serial.TypedMessage { return &serial.TypedMessage{Type:h.kind} }
func trafficTestContext(network net.Network) context.Context {
	ctx:=session.ContextWithInbound(context.Background(),&session.Inbound{Source:net.Destination{Network:network,Address:net.ParseAddress("10.0.0.2"),Port:51000},CanSpliceCopy:1})
	return session.ContextWithOutbounds(ctx,[]*session.Outbound{{OriginalTarget:net.Destination{Network:network,Address:net.ParseAddress("1.1.1.1"),Port:443}}})
}
func trafficTestLink() *transport.Link { return &transport.Link{Reader:buf.NewReader(bytes.NewReader([]byte("request"))),Writer:buf.NewWriter(new(bytes.Buffer))} }
func TestAppTrafficPayloadBothDirectionsTCPAndUDP(t *testing.T) {
	for _,network:=range []net.Network{net.Network_TCP,net.Network_UDP} {
		r:=NewAppTrafficRecorder(func(src,dst net.Destination)int { if src.Address.String()!="10.0.0.2" || src.Port!=51000 || dst.Address.String()!="1.1.1.1" || dst.Network!=network { t.Fatal("lost original flow") }; return 10123 })
		d:=new(DefaultDispatcher); d.SetAppTrafficRecorder(r)
		ctx,link:=d.wrapAppTraffic(trafficTestContext(network),trafficTestHandler{kind:"xray.proxy.vless.outbound.Config"},trafficTestLink())
		mb,_:=link.Reader.ReadMultiBuffer(); buf.ReleaseMulti(mb)
		if err:=link.Writer.WriteMultiBuffer(buf.MultiBuffer{buf.FromBytes([]byte("response123"))});err!=nil { t.Fatal(err) }
		if got:=r.Snapshot();got!="10123,7,11;" { t.Fatal(got) }
		if session.InboundFromContext(ctx).CanSpliceCopy!=3 { t.Fatal("raw splice could bypass live accounting") }
	}
}
func TestAppTrafficDirectBlockedDNSAndLoopbackExcludedRegardlessOfTag(t *testing.T) {
	r:=NewAppTrafficRecorder(func(net.Destination,net.Destination)int { t.Fatal("direct traffic resolved"); return 10123 }); d:=new(DefaultDispatcher); d.SetAppTrafficRecorder(r)
	for _,kind:=range []string{"freedom","blackhole","dns","loopback"} { link:=trafficTestLink(); _,got:=d.wrapAppTraffic(trafficTestContext(net.Network_TCP),trafficTestHandler{kind:"xray.proxy."+kind+".Config"},link); if got!=link { t.Fatal("non-proxy link modified",kind) } }
	if r.Snapshot()!="" { t.Fatal("direct traffic counted") }
}
func TestAppTrafficUnknownUIDAndNestedDetoursFailClosed(t *testing.T) {
	r:=NewAppTrafficRecorder(func(net.Destination,net.Destination)int{return -1}); d:=new(DefaultDispatcher); d.SetAppTrafficRecorder(r); link:=trafficTestLink(); handler:=trafficTestHandler{kind:"xray.proxy.wireguard.Config"}
	_,got:=d.wrapAppTraffic(trafficTestContext(net.Network_TCP),handler,link); if got!=link {t.Fatal("unknown UID attributed")}
	r=NewAppTrafficRecorder(func(net.Destination,net.Destination)int {t.Fatal("nested resolution");return 10123});d.SetAppTrafficRecorder(r)
	ctx:=session.ContextWithOutbounds(trafficTestContext(net.Network_TCP),[]*session.Outbound{{},{}}); _,got=d.wrapAppTraffic(ctx,handler,link)
	if got!=link || r.Snapshot()!="" {t.Fatal("nested detour counted")}
}
func TestAppTrafficResetContinuesExistingFlowAndStopFreezesIt(t *testing.T) {
	r:=NewAppTrafficRecorder(func(net.Destination,net.Destination)int{return 10123});d:=new(DefaultDispatcher);d.SetAppTrafficRecorder(r)
	_,link:=d.wrapAppTraffic(trafficTestContext(net.Network_TCP),trafficTestHandler{kind:"xray.proxy.vless.outbound.Config"},trafficTestLink())
	write:=func(s string){t.Helper();if err:=link.Writer.WriteMultiBuffer(buf.MultiBuffer{buf.FromBytes([]byte(s))});err!=nil {t.Fatal(err)}}
	write("old");r.Reset();write("new-data");if got:=r.Stop();got!="10123,0,8;" {t.Fatal(got)};write("after-disconnect");if got:=r.Snapshot();got!="10123,0,8;" {t.Fatal("disconnected bytes counted",got)}
}
func TestAppTrafficRecorderIsolationAcrossReconnectAndProbes(t *testing.T) {
	old:=NewAppTrafficRecorder(nil);old.apps[10123]=new(appTrafficCounter);old.add(&old.apps[10123].up,123);old.Stop()
	next:=NewAppTrafficRecorder(nil);next.apps[10123]=new(appTrafficCounter);next.add(&next.apps[10123].up,7);old.add(&old.apps[10123].up,9999)
	if old.Snapshot()!="10123,123,0;" || next.Snapshot()!="10123,7,0;" {t.Fatal("mixed core sessions")}
	probe:=new(DefaultDispatcher);link:=trafficTestLink();_,got:=probe.wrapAppTraffic(trafficTestContext(net.Network_TCP),trafficTestHandler{kind:"xray.proxy.vless.outbound.Config"},link);if got!=link {t.Fatal("probe was instrumented")}
}
func TestAppTrafficConcurrentCountersAndSaturation(t *testing.T) {
	r:=NewAppTrafficRecorder(nil);c:=new(appTrafficCounter);r.apps[10001]=c;var workers sync.WaitGroup
	for i:=0;i<16;i++ {workers.Add(1);go func(){defer workers.Done();for j:=0;j<1000;j++ {r.add(&c.up,10);_=r.Snapshot()}}()};workers.Wait()
	if r.Snapshot()!="10001,160000,0;" {t.Fatal(r.Snapshot())};r.add(&c.up,math.MaxInt64);if c.up.Load()!=math.MaxInt64 {t.Fatal("overflow")};r.Reset();if r.Snapshot()!="" {t.Fatal("reset failed")}
}
