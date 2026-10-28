package dispatcher

import (
	"context"
	"math"
	"sort"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
	"github.com/xtls/xray-core/common/buf"
	"github.com/xtls/xray-core/common/net"
	"github.com/xtls/xray-core/common/session"
	"github.com/xtls/xray-core/features/outbound"
	"github.com/xtls/xray-core/transport"
)

// One running VPN core owns this recorder. Only UID byte totals are retained;
// Android/system usage history and endpoint/packet histories are never read.
type AppTrafficRecorder struct {
	mu sync.RWMutex
	active bool
	resolve func(net.Destination, net.Destination) int
	apps map[int]*appTrafficCounter
}
type appTrafficCounter struct { up, down atomic.Int64 }

func NewAppTrafficRecorder(resolve func(net.Destination, net.Destination) int) *AppTrafficRecorder {
	return &AppTrafficRecorder{active:true, resolve:resolve, apps:make(map[int]*appTrafficCounter)}
}
func (d *DefaultDispatcher) SetAppTrafficRecorder(r *AppTrafficRecorder) { d.appTraffic.Store(r) }
func (r *AppTrafficRecorder) Snapshot() string {
	r.mu.RLock(); defer r.mu.RUnlock(); return r.snapshotLocked()
}
func (r *AppTrafficRecorder) snapshotLocked() string {
	uids := make([]int,0,len(r.apps)); for uid := range r.apps { uids=append(uids,uid) }; sort.Ints(uids)
	var b strings.Builder
	for _,uid := range uids {
		c:=r.apps[uid]; up,down:=c.up.Load(),c.down.Load(); if up==0 && down==0 { continue }
		b.WriteString(strconv.Itoa(uid)); b.WriteByte(','); b.WriteString(strconv.FormatInt(up,10)); b.WriteByte(','); b.WriteString(strconv.FormatInt(down,10)); b.WriteByte(';')
	}
	return b.String()
}
func (r *AppTrafficRecorder) Stop() string {
	r.mu.Lock(); defer r.mu.Unlock(); r.active=false; return r.snapshotLocked()
}
// Keep the counter objects so clearing works for downloads that remain connected.
func (r *AppTrafficRecorder) Reset() {
	r.mu.Lock(); defer r.mu.Unlock(); for _,c:=range r.apps { c.up.Store(0); c.down.Store(0) }
}
func addAppTraffic(c *atomic.Int64, bytes int64) {
	if bytes<=0 { return }; for { old:=c.Load(); next:=int64(math.MaxInt64); if old<=math.MaxInt64-bytes { next=old+bytes }; if c.CompareAndSwap(old,next) { return } }
}
func (r *AppTrafficRecorder) add(c *atomic.Int64, bytes int64) {
	r.mu.RLock(); if r.active { addAppTraffic(c,bytes) }; r.mu.RUnlock()
}
func appTrafficProxy(h outbound.Handler) bool {
	s:=h.ProxySettings(); if s==nil { return false }
	switch s.Type {
	case "xray.proxy.freedom.Config","xray.proxy.blackhole.Config","xray.proxy.dns.Config","xray.proxy.loopback.Config": return false
	default: return true
	}
}
func (d *DefaultDispatcher) wrapAppTraffic(ctx context.Context,h outbound.Handler,link *transport.Link) (context.Context,*transport.Link) {
	r:=d.appTraffic.Load(); if r==nil || !appTrafficProxy(h) { return ctx,link }
	in:=session.InboundFromContext(ctx); outs:=session.OutboundsFromContext(ctx)
	if in==nil || len(outs)!=1 || r.resolve==nil { return ctx,link }
	target:=outs[0].OriginalTarget
	if !in.Source.IsValid() || !target.IsValid() || !target.Address.Family().IsIP() { return ctx,link }
	uid:=r.resolve(in.Source,target); if uid<0 { return ctx,link }
	r.mu.Lock(); if !r.active { r.mu.Unlock(); return ctx,link }; c:=r.apps[uid]; if c==nil { c=&appTrafficCounter{}; r.apps[uid]=c }; r.mu.Unlock()
	// Raw splice bypasses buf.Reader/Writer and reports bytes only at EOF. Use
	// the existing buffered core path for attributed flows to keep live counts.
	copyInbound:=*in; copyInbound.CanSpliceCopy=3; ctx=session.ContextWithInbound(ctx,&copyInbound)
	return ctx,&transport.Link{Reader:&appTrafficReader{Reader:link.Reader,recorder:r,counter:&c.up},Writer:&appTrafficWriter{Writer:link.Writer,recorder:r,counter:&c.down}}
}
type appTrafficReader struct { buf.Reader; recorder *AppTrafficRecorder; counter *atomic.Int64 }
func (r *appTrafficReader) ReadMultiBuffer() (buf.MultiBuffer,error) { mb,err:=r.Reader.ReadMultiBuffer(); r.recorder.add(r.counter,int64(mb.Len())); return mb,err }
func (r *appTrafficReader) Interrupt() { if i,ok:=r.Reader.(interface{Interrupt()});ok { i.Interrupt() } }
func (r *appTrafficReader) Close() error { if c,ok:=r.Reader.(interface{Close()error});ok { return c.Close() }; return nil }
type appTrafficWriter struct { buf.Writer; recorder *AppTrafficRecorder; counter *atomic.Int64 }
func (w *appTrafficWriter) WriteMultiBuffer(mb buf.MultiBuffer) error { n:=int64(mb.Len()); err:=w.Writer.WriteMultiBuffer(mb); if err==nil { w.recorder.add(w.counter,n) }; return err }
func (w *appTrafficWriter) Interrupt() { if i,ok:=w.Writer.(interface{Interrupt()});ok { i.Interrupt() } }
func (w *appTrafficWriter) Close() error { if c,ok:=w.Writer.(interface{Close()error});ok { return c.Close() }; return nil }
