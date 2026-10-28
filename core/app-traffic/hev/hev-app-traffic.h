#ifndef HEV_APP_TRAFFIC_H
#define HEV_APP_TRAFFIC_H
#if defined(ANDROID) && defined(ENABLE_LIBRARY)
/* In-memory original-source metadata; never a persisted packet/endpoint log. */
typedef struct _HevAppTrafficSource HevAppTrafficSource;
struct _HevAppTrafficSource {
    HevAppTrafficSource *next;
    char address[46];
    int source_port, proxy_port, protocol, registered;
};
void hev_app_traffic_source_init (HevAppTrafficSource *, const char *, int);
void hev_app_traffic_register (HevAppTrafficSource *, int, int);
void hev_app_traffic_unregister (HevAppTrafficSource *);
int hev_app_traffic_resolve (int, int, char *, int, int *);
#else
typedef struct { int unused; } HevAppTrafficSource;
static inline void hev_app_traffic_source_init (HevAppTrafficSource *s, const char *a, int p) { }
static inline void hev_app_traffic_register (HevAppTrafficSource *s, int p, int fd) { }
static inline void hev_app_traffic_unregister (HevAppTrafficSource *s) { }
#endif
#endif
