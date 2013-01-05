package com.jeex.sci;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)  
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented

public @interface CacheEvict {
	String namespace();
	String key() default "";
	int[] keyArgs() default {};
	String[] keyProperties() default {};
	String keyGenerator() default "";
}
