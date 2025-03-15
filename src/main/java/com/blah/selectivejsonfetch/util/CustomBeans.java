package com.blah.selectivejsonfetch.util;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
public class CustomBeans {

	@Bean
	public ObjectMapper objectMapper() {
		ObjectMapper mapper = new ObjectMapper();
		mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		return mapper;
	}

	@Bean
	public Map<Class<?>, Object> defaultValueProvider() {
		Map<Class<?>, Object> defaultValues = new HashMap<>();
		defaultValues.put(boolean.class, false);
		defaultValues.put(byte.class, (byte) 0);
		defaultValues.put(short.class, (short) 0);
		defaultValues.put(int.class, 0);
		defaultValues.put(long.class, 0L);
		defaultValues.put(float.class, 0.0f);
		defaultValues.put(double.class, 0.0d);
		defaultValues.put(char.class, '\u0000');
		defaultValues.put(Boolean.class, false);
		defaultValues.put(Byte.class, (byte) 0);
		defaultValues.put(Short.class, (short) 0);
		defaultValues.put(Integer.class, 0);
		defaultValues.put(Long.class, 0L);
		defaultValues.put(Float.class, 0.0f);
		defaultValues.put(Double.class, 0.0d);
		defaultValues.put(Character.class, '\u0000');
		defaultValues.put(String.class, null);
		defaultValues.put(BigDecimal.class, BigDecimal.ZERO);
		defaultValues.put(BigInteger.class, BigInteger.ZERO);
		defaultValues.put(Date.class, new Date(0)); // Epoch start: January 1, 1970
		defaultValues.put(LocalDate.class, LocalDate.of(1970, 1, 1)); // Epoch start
		defaultValues.put(LocalDateTime.class, LocalDateTime.of(1970, 1, 1, 0, 0)); // Epoch start
		defaultValues.put(LocalTime.class, LocalTime.of(0, 0)); // Midnight
		defaultValues.put(ZoneId.class, ZoneId.systemDefault());
		defaultValues.put(Object.class, new Object());
		return defaultValues;
	}

}
