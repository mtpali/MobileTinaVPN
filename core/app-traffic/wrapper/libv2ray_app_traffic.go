package libv2ray

import (
	"github.com/xtls/xray-core/app/dispatcher"
	"github.com/xtls/xray-core/common/net"
	"github.com/xtls/xray-core/features/routing"
)

// A stable handle for one VPN core; reload/stop cannot query a later connection.
type AppTrafficLog struct { recorder *dispatcher.AppTrafficRecorder }
func (x *CoreController) StartAppTrafficLog(finder ProcessFinder) *AppTrafficLog {
	if x.coreInstance==nil || finder==nil { return nil }
	d,ok:=x.coreInstance.GetFeature(routing.DispatcherType()).(*dispatcher.DefaultDispatcher); if !ok { return nil }
	r:=dispatcher.NewAppTrafficRecorder(func(src,dst net.Destination)(uid int) {
		uid=-1; defer func(){ if recover()!=nil { uid=-1 } }()
		return finder.FindProcessByConnection(dst.Network.SystemString(),src.Address.String(),int(src.Port),dst.Address.String(),int(dst.Port))
	})
	d.SetAppTrafficRecorder(r); return &AppTrafficLog{recorder:r}
}
func (h *AppTrafficLog) Snapshot() string { return h.recorder.Snapshot() }
func (h *AppTrafficLog) Reset() { h.recorder.Reset() }
func (h *AppTrafficLog) Stop() string { return h.recorder.Stop() }
