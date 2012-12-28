Simple Cache Interceptor
=============================
An spring AOP interceptor, which caches the return value of the intercepted method. 

Spymemcached is used as the client to Memcached backend.

Annotation is used to configure the intercepted method, providing the key generating
information.
There're three ways to generate the key:
(1) Set the key explicitly
(2) Use the properties of the first argument of the intercepted method
(3) Use the factory class which can access all arguments of the intercepted method.

Namespace is supported.

Cache eviction (on namespace level or key level) is supported.

See the testing code for usage.
