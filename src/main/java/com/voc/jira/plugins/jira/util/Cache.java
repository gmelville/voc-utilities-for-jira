package com.voc.jira.plugins.jira.util;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import net.spy.memcached.MemcachedClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Cache {
	private static final Logger log = LoggerFactory.getLogger(Cache.class);
	private static final String LOG_PREFIX = "+++++++++++++ Memcached ++++++";

	private static void logExceptionAndEatIt(Exception e,ICacheRequest r) {
		e.printStackTrace();
		final String msg = String.format("%s exception [%s] key [%s]", LOG_PREFIX, e.getMessage(), r.key());
		log.error(msg);
	}
	
	/**
	 * Create MemcachedClient with a pool of cache threads if "Enable memcached client for VOC defect chart gadgets" 
	 * is set in the VOC Volume administration console.
	 * @param r
	 * @return
	 */
	public static Object get(ICacheRequest r) {
		//System.out.println("--In Cache.get(ICacheRequest r), r.isMemcached() == " + r.isMemcached());
		MemcachedClient c = null;
		if(r.isMemcached()) {
			c = CachePool.getInstance(r.host(), r.port()).getClient();
		}
		//System.out.println("MemcachedClient Available Servers: " + c.getAvailableServers());
		//System.out.println("MemcachedClient Unavailable Servers: " + c.getUnavailableServers());
		return get(r,c);
	}
	
	/**
	 * Get cached issues, unless memcached is disabled in the VOC Utilities administration console 
	 * or the memcached client is null.
	 * @param r
	 * @param c
	 * @return
	 */
	static Object get(ICacheRequest r, MemcachedClient c) {
		if (!r.isMemcached() || null == c) {
			return r.transform(r.get());
		}
		final String key = r.key().replaceAll(" ", "_");
		try {
			if (r.getLatest()) {
				throw new Exception();
			}
			Future<Object> future = c.asyncGet(key);
			Object result = future.get(1, TimeUnit.SECONDS);
			if (null == result) {
				throw new Exception();
			}
			return result;
		} catch (Exception e) {
			log.error(String.format("ERROR - Cache.get() - client future Exception() thrown (%s)", e.getMessage()));
			Object val = r.transform(r.get());
			try {
				c.set(key, getTtl(r), val);
			} catch (Exception e1) {
				logExceptionAndEatIt(e1,r);
			}
			return val;
		}
	}
	
	public void shutdown(ICacheRequest r) {
		MemcachedClient c = null;
		if(r.isMemcached()) {
			c = CachePool.getInstance(r.host(), r.port()).getClient();
		}
		/*
		final String key = r.key().replaceAll(" ", "_");
		Future<Object> future = c.asyncGet(key);
		Object result = future.cancel(false);
		*/
		if (c != null) { c.shutdown(); }
	}
	
	private static int getTtl(ICacheRequest r) {
		return r.ttlSecs() > 0 ? r.ttlSecs() : Time.secondsRemainingTillMidnight();
	}
	
	public void connectionLost(java.net.SocketAddress sa) {
		System.out.println("Cache.connectionLost SocketAddress: " + sa);
	}
}
