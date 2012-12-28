package com.jeex.sci.test;

import com.jeex.sci.CacheKeyGenerator;

public class BlackListQueryCacheKeyGenerator implements CacheKeyGenerator {

	public String generate(Object[] params) {
		BlackListQuery blq = (BlackListQuery) params[0];
		return blq.getInfoType() + ":" + blq.getInfo();
	}

}
