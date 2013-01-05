package com.jeex.sci;

import net.spy.memcached.MemcachedClient;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.lang.reflect.Method;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

public class CacheInterceptor implements MethodInterceptor {
	
	private static final Log log = LogFactory.getLog(CacheInterceptor.class);
	
	private MemcachedClient memcachedClient;
	
	private boolean cacheServerUnavailable = false;
	private long cacheServerUnavailableSince = 0L;
	
	/* Timeout value in seconds when doing get from the cache system. */
	private int timeoutInSeconds = 5;
	
	/* Waiting period in minutes for retry after last timeout occur. */
	private int retryInMinutes = 1;
	
	public Object invoke(MethodInvocation invoction) throws Throwable {
				
		Method method = invoction.getMethod();
		Cacheable c = method.getAnnotation(Cacheable.class);
		if (c != null) {
			return handleCacheable(invoction, method, c);
		}
		CacheEvict ce = method.getAnnotation(CacheEvict.class);
		if (ce != null) {
			return handleCacheEvict(invoction, ce);
		}
		return invoction.proceed();
	}
		
	private Object handleCacheable(MethodInvocation invoction, Method method,
			Cacheable c) throws Throwable {
		
		String key = getKey(invoction, KeyInfo.fromCacheable(c));		
		if (key.equals("")) {
			if (log.isDebugEnabled()) {
				log.warn("Empty cache key, the method is " + method);
			}
			return invoction.proceed();
		}
		
		Long nsTag = (Long) memcachedGet(c.namespace());
		if (nsTag == null) {
			nsTag = Long.valueOf(System.currentTimeMillis());
			memcachedSet(c.namespace(), 24*3600, Long.valueOf(nsTag));
		}
		key = makeMemcachedKey(c.namespace(), nsTag, key);
				
		Object o = null;
		o = memcachedGet(key);
		if (o != null) { 
			if (log.isDebugEnabled()) {
				log.debug("CACHE HIT: Cache Key = " + key);
			}
		} else {
			if (log.isDebugEnabled()) {
				log.debug("CACHE MISS: Cache Key = " + key);
			}
			o = invoction.proceed();
			memcachedSet(key, c.expires(), o);
		}
		return o;
	}
	
	private Object handleCacheEvict(MethodInvocation invoction, 
			CacheEvict ce) throws Throwable {
		String key = getKey(invoction, KeyInfo.fromCacheEvict(ce));		
		
		if (key.equals("")) { 
			if (log.isDebugEnabled()) {
				log.debug("Evicting " + ce.namespace());
			}
			memcachedDelete(ce.namespace());
		} else {
			Long nsTag = (Long) memcachedGet(ce.namespace());
			if (nsTag != null) {
				key = makeMemcachedKey(ce.namespace(), nsTag, key);
				if (log.isDebugEnabled()) {
					log.debug("Evicting " + key);
				}
				memcachedDelete(key);				
			}
		}
		return invoction.proceed();
	}

	/* Get data from cache with timeout support */
	private Object memcachedGet(String key) {
		if (cacheServerUnavailable) {
			long now = System.currentTimeMillis();
			if (((now - cacheServerUnavailableSince) / 1000) < (retryInMinutes * 60)) {  
				return null;
			}
		}
		
		Object o = null;
		Future<Object> f = memcachedClient.asyncGet(key);;
		try {
			o = f.get(timeoutInSeconds, TimeUnit.SECONDS);
			cacheServerUnavailable = false;
		} catch (Exception e) {
			f.cancel(true);
			cacheServerUnavailable = true;
			cacheServerUnavailableSince = System.currentTimeMillis();
			log.warn("Memcached server is unavailable.");
		}
		
		return o;
	} 

	private void memcachedSet(String key, int exp, Object o) {
		if (cacheServerUnavailable) {
			return;
		}
		memcachedClient.set(key, exp, o);
	}
	
	private void memcachedDelete(String key) {
		if (cacheServerUnavailable) {
			return;
		}		
		memcachedClient.delete(key);
	}
	
	/* Get key for caching data */
	private String getKey(MethodInvocation invoction, KeyInfo ki) {
		String key = ki.key();
		try {
			if (key.equals("")) {
				if (ki.keyArgs().length > 0) {
					key = getKeyWithArgs(invoction.getArguments(), ki.keyArgs());					
				} else if (ki.keyProperties().length > 0) {
					Object o = invoction.getArguments()[0];
					key = getKeyWithProperties(o, ki.keyProperties());				
				} else if (!ki.keyGenerator().equals("")) {
					key = getKeyWithGenerator(invoction, ki.keyGenerator());
				} else {
					/* key should be empty*/
				}
			}
		} catch (Exception e) {
			log.warn("Get key failed", e);
		}
		return key;
	}
	
	/* Get key use the arguments of the intercepted method. */
	private String getKeyWithArgs(Object[] args, int[] argIndex) {
		StringBuilder key = new StringBuilder();
		boolean first = true;
		for (int index: argIndex) {
			if (index < 0 || index >= args.length) {
				throw new IllegalArgumentException("Index out of bound");
			}
			if (!first) {
				key.append(':');
			} else {
				first = false;
			}
			key = key.append(args[index]);
		}
		return key.toString();
	}
	
	/* Get key using the properties of the first parameter of the intercepted method */
	private String getKeyWithProperties(Object o, String props[]) 
			throws Exception {
		StringBuilder key = new StringBuilder();
		boolean first = true;
		for (String prop: props) {
			/* Convert the bean property to get method name */
			String methodName = "get" 
					+ prop.substring(0, 1).toUpperCase() 
					+ prop.substring(1);
			Method m = o.getClass().getMethod(methodName);
			Object r = m.invoke(o, (Object[]) null);
			if (!first) {
				key.append(':');
			} else {
				first = false;
			}
			key = key.append(r);
		}
		return key.toString();
	}

	/* Get key using the generator */
	private String getKeyWithGenerator(MethodInvocation invoction, String keyGenerator) 
			throws Exception {
		Class<?> ckg = Class.forName(keyGenerator);
		CacheKeyGenerator ikg = (CacheKeyGenerator)ckg.newInstance();
		return ikg.generate(invoction.getArguments());
	}
	
	/* Make the key feeding to memcached. */
	private String makeMemcachedKey(String namespace, long namespaceTag, String key) {
		return namespace + '_' + namespaceTag + ':' + key;
	}
	
	/* Helper class to hold key generating information. */
	private static class KeyInfo {
		String key;
		int[]  keyArgs;
		String keyProperties[];
		String keyGenerator;
		static KeyInfo fromCacheable(Cacheable c) {
			KeyInfo ki = new KeyInfo();
			ki.key = c.key();
			ki.keyArgs = c.keyArgs();
			ki.keyGenerator = c.keyGenerator();
			ki.keyProperties = c.keyProperties();
			return ki;
		}
		
		static KeyInfo fromCacheEvict(CacheEvict ce) {
			KeyInfo ki = new KeyInfo();
			ki.key = ce.key();
			ki.keyArgs = ce.keyArgs();
			ki.keyGenerator = ce.keyGenerator();
			ki.keyProperties = ce.keyProperties();
			return ki;			
		}

		String key() {
			return key;
		}
		
		int[] keyArgs() {
			return keyArgs;
		}

		String[] keyProperties() {
			return keyProperties;
		}

		String keyGenerator() {
			return keyGenerator;
		}
	}

	public int getRetryInMinutes() {
		return retryInMinutes;
	}

	public void setRetryInMinutes(int retryInMinutes) {
		this.retryInMinutes = retryInMinutes;
	}

	public int getTimeoutInSeconds() {
		return timeoutInSeconds;
	}

	public void setTimeoutInSeconds(int timeoutInSeconds) {
		this.timeoutInSeconds = timeoutInSeconds;
	}

	public MemcachedClient getMemcachedClient() {
		return memcachedClient;
	}

	public void setMemcachedClient(MemcachedClient memcachedClient) {
		this.memcachedClient = memcachedClient;
	}
}
