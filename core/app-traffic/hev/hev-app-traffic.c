#include "hev-app-traffic.h"
#if defined(ANDROID) && defined(ENABLE_LIBRARY)
#include <arpa/inet.h>
#include <pthread.h>
#include <string.h>
#include <sys/socket.h>

static pthread_mutex_t source_mutex = PTHREAD_MUTEX_INITIALIZER;
static HevAppTrafficSource *buckets[256];
static unsigned int source_bucket (int protocol, int port) { return ((unsigned int)port*31u+(unsigned int)protocol)&255u; }
void hev_app_traffic_source_init (HevAppTrafficSource *s, const char *address, int port) {
    memset (s,0,sizeof (*s));
    if (address && strlen(address)<sizeof(s->address)) memcpy(s->address,address,strlen(address)+1);
    s->source_port=port;
}
static void unregister_locked (HevAppTrafficSource *s) {
    if (!s->registered) return;
    HevAppTrafficSource **p=&buckets[source_bucket(s->protocol,s->proxy_port)];
    while (*p) { if (*p==s) { *p=s->next; break; } p=&(*p)->next; }
    s->registered=0; s->next=NULL;
}
void hev_app_traffic_register (HevAppTrafficSource *s, int protocol, int fd) {
    struct sockaddr_storage local; socklen_t size=sizeof(local); int port;
    if (!s->address[0] || s->source_port<=0 || getsockname(fd,(struct sockaddr *)&local,&size)<0) return;
    if (local.ss_family==AF_INET) port=ntohs(((struct sockaddr_in *)&local)->sin_port);
    else if (local.ss_family==AF_INET6) port=ntohs(((struct sockaddr_in6 *)&local)->sin6_port);
    else return;
    if (port<=0) return;
    pthread_mutex_lock(&source_mutex); unregister_locked(s);
    unsigned int bucket=source_bucket(protocol,port);
    HevAppTrafficSource *item=buckets[bucket],*next;
    while (item) { next=item->next; if (item->protocol==protocol && item->proxy_port==port) unregister_locked(item); item=next; }
    s->protocol=protocol; s->proxy_port=port; s->registered=1; s->next=buckets[bucket]; buckets[bucket]=s;
    pthread_mutex_unlock(&source_mutex);
}
void hev_app_traffic_unregister (HevAppTrafficSource *s) { pthread_mutex_lock(&source_mutex); unregister_locked(s); pthread_mutex_unlock(&source_mutex); }
int hev_app_traffic_resolve (int protocol,int port,char *address,int capacity,int *source_port) {
    int found=0; if (port<=0 || port>65535 || capacity<46) return 0;
    pthread_mutex_lock(&source_mutex);
    for (HevAppTrafficSource *s=buckets[source_bucket(protocol,port)];s;s=s->next) {
        if (s->protocol==protocol && s->proxy_port==port) { memcpy(address,s->address,sizeof(s->address)); *source_port=s->source_port; found=1; break; }
    }
    pthread_mutex_unlock(&source_mutex); return found;
}
#endif
