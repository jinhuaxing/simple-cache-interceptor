package com.jeex.sci.test;

import net.spy.memcached.MemcachedClient;

import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;

/* It is not the best way to do unit test. It just works. */
public class TestMain {
	@Test
	public void testMain() throws Exception {
				
		ApplicationContext ctx = new FileSystemXmlApplicationContext("classpath:beans.xml");		
		MemcachedClient mc = (MemcachedClient) ctx.getBean("memcachedClient");
		
		BlackListDaoImpl dao = (BlackListDaoImpl)ctx.getBean("blackListDaoImpl");
		
		while (true) {			
			System.out.println("################################GETTING START######################");
			mc.flush();		
						
			BlackListQuery query = new BlackListQuery(1, "222.231.23.13");
			dao.searchBlackListCount(query);		
			dao.searchBlackListCount2(query);
			
			BlackListQuery query2 = new BlackListQuery(1, "123.231.23.14");
			
			dao.anotherMethond(333, 444);
			
			dao.searchBlackListCount2(query2);
			dao.searchBlackListCount3(query2);
			
			dao.evict(query);
			dao.searchBlackListCount2(query);
			
			dao.evictAll();
			dao.searchBlackListCount3(query2);
			
			Thread.sleep(300);
		}
		
	}
	
}