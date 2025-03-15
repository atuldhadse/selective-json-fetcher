package com.blah.selectivejsonfetch.util;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Id;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.Metamodel;
import jakarta.persistence.metamodel.SingularAttribute;

@Component
public class ClsUtil {

	@Autowired
	private Environment env;

	@Autowired
	private EntityManager entityManager;

	public Class<?> getClassForName(String className) {
		try {
			return Class.forName(env.getProperty("entity.pkg.name") + "." + className);
		} catch (ClassNotFoundException e) {
			throw new IllegalArgumentException("Class not found: " + className);
		}
	}

	public Object convertValueToCorrectType(String value, Class<?> fieldType) {
		try {
			if (fieldType.equals(Integer.class) || fieldType.equals(int.class)) {
				return Integer.parseInt(value);
			} else if (fieldType.equals(Long.class) || fieldType.equals(long.class)) {
				return Long.parseLong(value);
			} else if (fieldType.equals(Double.class) || fieldType.equals(double.class)) {
				return Double.parseDouble(value);
			} else if (fieldType.equals(Float.class) || fieldType.equals(float.class)) {
				return Float.parseFloat(value);
			} else if (fieldType.equals(Boolean.class) || fieldType.equals(boolean.class)) {
				return Boolean.parseBoolean(value);
			} else if (fieldType.equals(BigDecimal.class)) {
				return new BigDecimal(value);
			} else if (fieldType.equals(BigInteger.class)) {
				return new BigInteger(value);
			} else if (fieldType.equals(Date.class)) {
				DateFormat df = new SimpleDateFormat("dd-MM-yyyy");
				return df.parse(value);
			} else if (fieldType.equals(LocalDate.class)) {
				return LocalDate.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
			} else if (fieldType.equals(LocalDateTime.class)) {
				return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
			} else if (fieldType.equals(LocalTime.class)) {
				return LocalTime.parse(value, DateTimeFormatter.ofPattern("HH:mm:ss"));
			} else if (fieldType.equals(ZoneId.class)) {
				return ZoneId.of(value);
			} else if (fieldType.equals(String.class)) {
				return value;
			} else if (fieldType.isEnum()) {
				return getEnumValue((Class<Enum>) fieldType, value);
			} else {
				return value;
			}
		} catch (Exception e) {
			return value;
		}
	}

	private Object getEnumValue(Class<Enum> fieldType, String value) {
		try {
			return Enum.valueOf((Class<Enum>) fieldType, value);
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException(
					"Invalid value for enum type: " + fieldType.getName() + ", value: " + value);
		}
	}

	public Field findFieldInClassHierarchy(Class<?> clazz, String fieldName) {
		while (clazz != null) {
			try {
				return clazz.getDeclaredField(fieldName);
			} catch (NoSuchFieldException e) {
				// Move up the class hierarchy
				clazz = clazz.getSuperclass();
			}
		}
		return null;
	}

	public Class<?> getPrimaryKeyType(Class<?> entityClass) {
		EntityType<?> entityType = entityManager.getMetamodel().entity(entityClass);
		jakarta.persistence.metamodel.Type<?> idType = entityType.getIdType();
		return idType.getJavaType();
	}

	public String getPrimaryKeyField(Class<?> clazz) {
		Metamodel metamodel = entityManager.getMetamodel();
		Class<?> currentClass = clazz;
		while (currentClass != null && !currentClass.equals(Object.class)) {
			EntityType<?> entity = metamodel.entity(currentClass);
			if (entity != null) {
				SingularAttribute<?, ?> idAttribute = entity.getId(getIdClass(currentClass));
				if (idAttribute != null) {
					return idAttribute.getName();
				}
			}
			currentClass = currentClass.getSuperclass();
		}
		return null;
	}

	private Class<?> getIdClass(Class<?> clazz) {
		Metamodel metamodel = entityManager.getMetamodel();
		Class<?> currentClass = clazz;
		while (currentClass != null && !currentClass.equals(Object.class)) {
			EntityType<?> entity = metamodel.entity(currentClass);
			if (entity != null) {
				Class<?> idType = entity.getIdType().getJavaType();
				if (idType != null) {
					return idType;
				}
			}
			currentClass = currentClass.getSuperclass();
		}
		return null;
	}

	public Map<String, List<Class<?>>> getFieldsTypeMap(Class<?> entityClass, String[] fieldArray) {
		Map<String, Object> mp = new HashMap<>();
		for (String s : fieldArray) {
			mp.put(s, null);
		}
		return getFieldsTypeFromClass(entityClass, mp);
	}

	public Map<String, List<Class<?>>> getFieldsTypeFromClass(Class<?> entityClass, Map<String, Object> entry) {
		Map<String, List<Class<?>>> fieldTypeMap = new HashMap<>();
		List<List<String>> result = entry.keySet().stream().map(s -> List.of(s.split("\\.")))
				.collect(Collectors.toList());
		for (List<String> fields : result) {
			getFieldsTypeFromClassHelper(entityClass, fieldTypeMap, fields, "", 0);
		}
		return fieldTypeMap;
	}

	public void getFieldsTypeFromClassHelper(Class<?> entityClass, Map<String, List<Class<?>>> fieldTypeMap,
			List<String> fields, String s, int idx) {
		if (idx >= fields.size())
			return;
		if (s != null && !s.isEmpty()) {
			s += '.';
		}
		s += fields.get(idx);
		Field field;
		try {
			field = findFieldInHierarchy(entityClass, fields.get(idx));
		} catch (Exception e) {
			return;
		}
		Class<?> fieldType = field.getType();
		Type fieldGenericType = field.getGenericType();
		if (fieldGenericType instanceof ParameterizedType parameterizedType) {
			Type rawType = parameterizedType.getRawType();
			Type[] typeArguments = parameterizedType.getActualTypeArguments();
			if (rawType instanceof Class<?>) {
				fieldTypeMap.put(s, Arrays.asList((Class<?>) rawType, (Class<?>) typeArguments[0]));
			}
			if (typeArguments.length > 0 && typeArguments[0] instanceof Class<?>) {
				getFieldsTypeFromClassHelper((Class<?>) typeArguments[0], fieldTypeMap, fields, s, idx + 1);
			}
		} else {
			fieldTypeMap.put(s, Arrays.asList(fieldType, fieldType));
			getFieldsTypeFromClassHelper(fieldType, fieldTypeMap, fields, s, idx + 1);
		}
	}

	public Field findFieldInHierarchy(Class<?> clazz, String fieldName) throws NoSuchFieldException {
		Class<?> currentClass = clazz;
		while (currentClass != null) {
			try {
				return currentClass.getDeclaredField(fieldName);
			} catch (NoSuchFieldException e) {
				currentClass = currentClass.getSuperclass();
			}
		}
		throw new NoSuchFieldException("Field '" + fieldName + "' not found in class hierarchy of " + clazz.getName());
	}

	public String getPrimaryKeyFieldName(Class<?> cls) {
		while (cls != null) {
			for (Field field : cls.getDeclaredFields()) {
				if (field.isAnnotationPresent(Id.class)) {
					return field.getName();
				}
			}
			cls = cls.getSuperclass();
		}
		return null;
	}

	public Field getClassDeclaredField(Class<?> clz, String fieldName) throws NoSuchFieldException {
		while (clz != null) {
			try {
				return clz.getDeclaredField(fieldName);
			} catch (NoSuchFieldException e) {
				clz = clz.getSuperclass();
			}
		}
		throw new NoSuchFieldException("Field '" + fieldName + "' not found in class hierarchy.");
	}

}
