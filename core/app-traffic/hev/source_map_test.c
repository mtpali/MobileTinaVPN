#include "hev-app-traffic.h"
#include <assert.h>
#include <arpa/inet.h>
#include <pthread.h>
#include <stdio.h>
#include <string.h>
#include <sys/socket.h>
#include <unistd.h>
static int local_socket(int type) {
    struct sockaddr_in a={.sin_family=AF_INET,.sin_addr.s_addr=htonl(INADDR_LOOPBACK)};
    int fd=socket(AF_INET,type,0);assert(fd>=0);assert(bind(fd,(struct sockaddr*)&a,sizeof(a))==0);return fd;
}
static int socket_port(int fd) {struct sockaddr_in a;socklen_t n=sizeof(a);assert(getsockname(fd,(struct sockaddr*)&a,&n)==0);return ntohs(a.sin_port);}
static void verify(int proto,int proxy,const char*expected,int expected_port) {
    char a[46];int p;assert(hev_app_traffic_resolve(proto,proxy,a,sizeof(a),&p));assert(strcmp(a,expected)==0&&p==expected_port);
}
struct worker {HevAppTrafficSource source;int fd,port;};
static void *concurrent_source(void *arg) {
    struct worker*w=arg;
    for(int i=0;i<10000;i++){hev_app_traffic_register(&w->source,IPPROTO_UDP,w->fd);verify(IPPROTO_UDP,w->port,"fd00::2",53000);hev_app_traffic_unregister(&w->source);}return NULL;
}
int main(void) {
    HevAppTrafficSource tcp,udp,replacement;char a[46];int p;
    int tf=local_socket(SOCK_STREAM),uf=local_socket(SOCK_DGRAM);
    hev_app_traffic_source_init(&tcp,"10.0.0.2",51000);hev_app_traffic_source_init(&udp,"fd00::2",52000);
    assert(!hev_app_traffic_resolve(IPPROTO_TCP,socket_port(tf),a,sizeof(a),&p));
    hev_app_traffic_register(&tcp,IPPROTO_TCP,tf);hev_app_traffic_register(&udp,IPPROTO_UDP,uf);
    verify(IPPROTO_TCP,socket_port(tf),"10.0.0.2",51000);verify(IPPROTO_UDP,socket_port(uf),"fd00::2",52000);
    assert(!hev_app_traffic_resolve(IPPROTO_UDP,socket_port(tf),a,sizeof(a),&p)||socket_port(tf)==socket_port(uf));
    hev_app_traffic_source_init(&replacement,"10.0.0.3",54000);hev_app_traffic_register(&replacement,IPPROTO_TCP,tf);hev_app_traffic_unregister(&tcp);
    verify(IPPROTO_TCP,socket_port(tf),"10.0.0.3",54000);hev_app_traffic_unregister(&replacement);hev_app_traffic_unregister(&udp);
    assert(!hev_app_traffic_resolve(IPPROTO_TCP,socket_port(tf),a,sizeof(a),&p));assert(!hev_app_traffic_resolve(IPPROTO_UDP,socket_port(uf),a,sizeof(a),&p));
    assert(!hev_app_traffic_resolve(IPPROTO_TCP,-1,a,sizeof(a),&p));assert(!hev_app_traffic_resolve(IPPROTO_TCP,65536,a,sizeof(a),&p));close(tf);close(uf);
    struct worker workers[8];pthread_t threads[8];
    for(int i=0;i<8;i++){workers[i].fd=local_socket(SOCK_DGRAM);workers[i].port=socket_port(workers[i].fd);hev_app_traffic_source_init(&workers[i].source,"fd00::2",53000);assert(pthread_create(&threads[i],NULL,concurrent_source,&workers[i])==0);}
    for(int i=0;i<8;i++){assert(pthread_join(threads[i],NULL)==0);close(workers[i].fd);}
    puts("HEV source attribution: IPv4, IPv6, TCP/UDP, missing/reused tuple, teardown, 8 concurrent workers passed");return 0;
}
