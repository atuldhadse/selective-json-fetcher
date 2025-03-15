package com.blah.selectivejsonfetch.serviceImpl;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.blah.selectivejsonfetch.responsehandler.CustomException;
import com.blah.selectivejsonfetch.util.ClsUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import net.bytebuddy.jar.asm.ClassReader;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;

@Component
public class FetchHelper {

	@Autowired
	private ClsUtil clsUtil;

	@Autowired
	private ObjectMapper objectMapper;

	private static final Map<String, Map<String, List<String>>> cache = new HashMap<>();

	public void checkFilters(Object filters) {
		if (ObjectUtils.isNotEmpty(filters)) {
			if (!(filters instanceof List<?> filterList)) {
				throw CustomException.builder().message(getExampleFilterMessage()).status(HttpStatus.BAD_REQUEST)
						.build();
			}
			for (Object entryObj : filterList) {
				if (!(entryObj instanceof Map<?, ?> entry)) {
					throw CustomException.builder().message(getExampleFilterMessage()).status(HttpStatus.BAD_REQUEST)
							.build();
				}
				if (!entry.containsKey("path") || !entry.containsKey("operator") || !entry.containsKey("value")) {
					throw CustomException.builder().message(getExampleFilterMessage()).status(HttpStatus.BAD_REQUEST)
							.build();
				}
			}
		}
	}

	private String getExampleFilterMessage() {
		ArrayNode rootArray = objectMapper.createArrayNode();
		ObjectNode filterNode = objectMapper.createObjectNode();
		filterNode.put("path", "field1.field2.field (Based on entity class hierarchy)");
		filterNode.put("operator", "=");
		filterNode.put("value", "any value");
		rootArray.add(filterNode);
		return "Please provide filters like: \n" + rootArray.toPrettyString();
	}

	public String[] getUniqueFieldArray(String fields, Class<?> entityClass, String primaryField,
			Map<String, String> singleFieldPrimaryIdName, String[] fieldArray,
			Map<String, List<Class<?>>> fieldsTypeMap) {
		if (StringUtils.isBlank(fields)) {
			return Arrays.asList(primaryField).toArray(String[]::new);
		}
		List<String> fieldArrayList = new ArrayList<>(Arrays.asList(fieldArray));
		fieldArrayList.add(0, primaryField);
		Set<String> set = new LinkedHashSet<>(fieldArrayList);
		List<String> output = new ArrayList<>();
		for (String path : set) {
			String[] pathSplit = path.split("\\.");
			String field = "";
			int i = 0;
			for (; i < pathSplit.length - 1; i++) {
				if (StringUtils.isBlank(field)) {
					field += pathSplit[i];
				} else {
					field += '.' + pathSplit[i];
				}
				String primaryKeyValueOfField = checkAndAddPrimaryFieldNameToMap(singleFieldPrimaryIdName,
						fieldsTypeMap, field);
				if (StringUtils.isNotBlank(primaryKeyValueOfField))
					output.add(field + '.' + primaryKeyValueOfField);
				if (set.contains(field)) {
					break;
				}
			}
			if (i == pathSplit.length - 1) {
				checkAndAddPrimaryFieldNameToMap(singleFieldPrimaryIdName, fieldsTypeMap, path);
				output.add(path);
			}
		}
		return output.stream().distinct().toArray(String[]::new);
	}

	public String checkAndAddPrimaryFieldNameToMap(Map<String, String> singleFieldPrimaryIdName,
			Map<String, List<Class<?>>> fieldsTypeMap, String field) {
		try {
			List<Class<?>> clazz = fieldsTypeMap.getOrDefault(field, null);
			String primaryKeyField1 = clsUtil.getPrimaryKeyField(clazz.get(0));
			singleFieldPrimaryIdName.put(field, primaryKeyField1);
			return primaryKeyField1;
		} catch (Exception e) {
		}
		try {
			List<Class<?>> clazz = fieldsTypeMap.getOrDefault(field, null);
			String primaryKeyField2 = clsUtil.getPrimaryKeyField(clazz.get(1));
			singleFieldPrimaryIdName.put(field, primaryKeyField2);
			return primaryKeyField2;
		} catch (Exception e) {
		}
		return null;
	}

	Object getTransientFieldValue(Map<String, List<Class<?>>> fieldsTypeMap,
			Map<String, Object[]> dependentFieldsForJsonGetterFields, String jsonGetterField, Object[] rs,
			Class<?> entityClass) {
		try {
			String basePath = getBasePath(jsonGetterField);
			Class<?> jsonGetterFieldClass = fieldsTypeMap.get(basePath).get(1);
			if (basePath.equals(jsonGetterField)) {
				jsonGetterFieldClass = entityClass;
			}
			String methodName = "get" + StringUtils.capitalize(getLastSegment(jsonGetterField));
			Object instance = jsonGetterFieldClass.getDeclaredConstructor().newInstance();
			Object[] objects = dependentFieldsForJsonGetterFields.get(jsonGetterField);
			List<String> fieldNames = (List<String>) objects[0];
			List<Integer> fieldValuesIdxs = (List<Integer>) objects[1];
			for (int i = 0; i < fieldNames.size(); i++) {
				Field field = jsonGetterFieldClass.getDeclaredField(getLastSegment(fieldNames.get(i)));
				field.setAccessible(true);
				field.set(instance, rs[fieldValuesIdxs.get(i)]);
			}
			Method method = jsonGetterFieldClass.getDeclaredMethod(methodName);
			method.setAccessible(true);
			return method.invoke(instance);
		} catch (Exception e) {
			return null;
		}
	}

	private String getBasePath(String input) {
		if (input == null || input.isEmpty()) {
			return input;
		}
		int lastDotIndex = input.lastIndexOf('.');
		if (lastDotIndex == -1) {
			return input;
		}
		return input.substring(0, lastDotIndex);
	}

	private static String getLastSegment(String input) {
		if (input == null || input.isEmpty()) {
			return input;
		}
		int lastDotIndex = input.lastIndexOf('.');
		if (lastDotIndex == -1) {
			return input;
		}
		return input.substring(lastDotIndex + 1);
	}

	Map<String, Object[]> getDependentFieldsForJsonGetterFields(List<String> jsonGetterFields,
			Map<String, List<Class<?>>> fieldsTypeMap, Class<?> entityClass) throws Exception {
		Map<String, Object[]> outputMap = new LinkedHashMap<>();
		for (String fieldStr : jsonGetterFields) {
			if (StringUtils.isBlank(fieldStr))
				continue;
			String basePath = getBasePath(fieldStr);
			Class<?> class1 = fieldsTypeMap.get(basePath).get(1);
			if (!basePath.equals(fieldStr)) {
				entityClass = class1;
			}
			String classNameWithPackage = "com.content.delivery.model." + entityClass.getSimpleName();
			String methodName = "get" + StringUtils.capitalize(getLastSegment(fieldStr));
			List<String> fieldsNameUsedInMethod = getFieldsNameUsedInMethod(classNameWithPackage, methodName);
			List<String> output = (!fieldStr.contains(".") ? fieldsNameUsedInMethod
					: fieldsNameUsedInMethod.stream().map(s -> basePath + '.' + s).collect(Collectors.toList()));
			Object[] computeIfAbsent = outputMap.computeIfAbsent(fieldStr,
					k -> new Object[] { new ArrayList<String>(), new ArrayList<Integer>() });
			if (computeIfAbsent[0] == null) {
				computeIfAbsent[0] = new ArrayList<String>();
			}
			((List<String>) computeIfAbsent[0]).addAll(output);
			if (computeIfAbsent[1] == null) {
				computeIfAbsent[1] = new ArrayList<Integer>();
			}
		}
		return outputMap;
	}

	public List<String> getFieldsNameUsedInMethod(String classNameWithPackage, String methodName) throws Exception {
		if (cache.containsKey(classNameWithPackage) && cache.get(classNameWithPackage).containsKey(methodName)) {
			return cache.get(classNameWithPackage).get(methodName);
		}
		Set<String> output = new LinkedHashSet<>();
		InputStream classInputStream = getClass().getClassLoader()
				.getResourceAsStream(classNameWithPackage.replace('.', '/') + ".class");
		if (classInputStream == null) {
			throw new RuntimeException("Class not found: " + classNameWithPackage);
		}
		ClassReader classReader = new ClassReader(classInputStream);

		classReader.accept(new ClassVisitor(Opcodes.ASM9) {
			@Override
			public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
					String[] exceptions) {
				if (name.equals(methodName)) {
					return new MethodVisitor(Opcodes.ASM9) {
						@Override
						public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
							output.add(name);
						}
					};
				}
				return super.visitMethod(access, name, descriptor, signature, exceptions);
			}
		}, 0);
		ArrayList<String> fields = new ArrayList<>(output);
		cache.computeIfAbsent(classNameWithPackage, k -> new HashMap<>()).put(methodName, fields);
		return fields;
	}

	public List<JsonNode> prettyfy(List<Map<String, Object>> output, String primaryIdName,
			Map<String, List<Class<?>>> fieldsType, Map<String, String> singleFieldPrimaryIdName) {
		Map<Object, List<Map<String, Object>>> groupedByPrimaryId = new LinkedHashMap<>();
		groupedByPrimaryId = output.stream().collect(
				Collectors.groupingBy(entry -> entry.get(primaryIdName), LinkedHashMap::new, Collectors.toList()));
		List<JsonNode> result = new ArrayList<>();
		for (Map.Entry<Object, List<Map<String, Object>>> entry : groupedByPrimaryId.entrySet()) {
			List<Map<String, Object>> oneEntryData = entry.getValue();
			JsonNode prettySingleEntry = prettySingleEntry(oneEntryData, fieldsType, singleFieldPrimaryIdName);
			result.add(prettySingleEntry);
		}
		return result;
	}

	private void replaceEmptyObjectsWithNull(JsonNode node) {
		if (node.isObject()) {
			ObjectNode objectNode = (ObjectNode) node;
			Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();
			while (fields.hasNext()) {
				Map.Entry<String, JsonNode> field = fields.next();
				JsonNode value = field.getValue();
				if (value == null || value.isNull()) {
					continue;
				}
				if (value.isObject() && value.size() == 0) {
					field.setValue(JsonNodeFactory.instance.nullNode());
				} else if (value.isArray()) {
					ArrayNode arrayNode = (ArrayNode) value;
					boolean allNull = true;
					for (int i = 0; i < arrayNode.size(); i++) {
						JsonNode arrayItem = arrayNode.get(i);
						if (arrayItem.isObject() && arrayItem.size() == 0) {
							arrayNode.set(i, JsonNodeFactory.instance.nullNode());
						} else {
							allNull = false;
							replaceEmptyObjectsWithNull(arrayItem);
						}
					}
					if (allNull) {
						field.setValue(JsonNodeFactory.instance.nullNode());
					}
				} else {
					replaceEmptyObjectsWithNull(value);
				}
			}
		}
	}

	public JsonNode prettySingleEntry(List<Map<String, Object>> oneEntryData, Map<String, List<Class<?>>> fieldsType,
			Map<String, String> singleFieldPrimaryIdName) {
		ObjectNode root = objectMapper.createObjectNode();
		for (Map<String, Object> flatMap : oneEntryData) {
			flatMap.forEach((key, value) -> {
				String[] parts = key.split("\\.");
				ObjectNode currentNode = root;
				String path = "";
				currentNode = handleNMinueOneElements(parts, fieldsType, currentNode, singleFieldPrimaryIdName, flatMap,
						path);
				handleLastElement(parts, fieldsType, currentNode, singleFieldPrimaryIdName, flatMap, key, value);
			});
		}
		replaceEmptyObjectsWithNull(root);
		return root;
	}

	private ObjectNode handleNMinueOneElements(String[] parts, Map<String, List<Class<?>>> fieldsType,
			ObjectNode currentNode, Map<String, String> singleFieldPrimaryIdName, Map<String, Object> flatMap,
			String path) {
		for (int i = 0; i < parts.length - 1; i++) {
			String part = parts[i];
			if (!StringUtils.isBlank(path)) {
				path += '.';
			}
			path += part;
			Class<?> fieldType = fieldsType.getOrDefault(path, Arrays.asList(Object.class)).get(0);
			if (fieldType != null && Iterable.class.isAssignableFrom(fieldType)) {
				ArrayNode arrayNode;
				if (currentNode.has(part) && currentNode.get(part).isArray()) {
					arrayNode = (ArrayNode) currentNode.get(part);
				} else {
					arrayNode = currentNode.putArray(part);
				}
				ObjectNode listItemNode = null;
				if (arrayNode.size() > 0) {
					boolean found = false;
					for (JsonNode itemNode : arrayNode) {
						String primaryIdKey = singleFieldPrimaryIdName.getOrDefault(path, null);
						if (primaryIdKey != null && itemNode.has(primaryIdKey)) {
							Object idValue = itemNode.get(primaryIdKey);
							Object flatMapValue = getValueFromFlatMap(flatMap, path, primaryIdKey);
							if (idValue != null && flatMapValue != null
									&& idValue.toString().equals(flatMapValue.toString())) {
								found = true;
								listItemNode = (ObjectNode) itemNode;
								break;
							}
						} else {
							found = true;
							listItemNode = objectMapper.createObjectNode();
						}
					}
					if (!found) {
						listItemNode = objectMapper.createObjectNode();
						arrayNode.add(listItemNode);
					}
				} else {
					listItemNode = objectMapper.createObjectNode();
					arrayNode.add(listItemNode);
				}
				currentNode = listItemNode;
			} else {
				if (!currentNode.has(part)) {
					currentNode = currentNode.putObject(part);
				} else {
					currentNode = (ObjectNode) currentNode.get(part);
				}
			}
		}
		return currentNode;
	}

	private void handleLastElement(String[] parts, Map<String, List<Class<?>>> fieldsType, ObjectNode currentNode,
			Map<String, String> singleFieldPrimaryIdName, Map<String, Object> flatMap, String key, Object value) {
		String lastPart = parts[parts.length - 1];
		Class<?> lastFieldType = fieldsType.getOrDefault(lastPart, Arrays.asList(Object.class)).get(0);
		if (lastFieldType != null && Iterable.class.isAssignableFrom(lastFieldType)) {
			ArrayNode arrayNode;
			if (currentNode.has(lastPart) && currentNode.get(lastPart).isArray()) {
				arrayNode = (ArrayNode) currentNode.get(lastPart);
			} else {
				arrayNode = currentNode.putArray(lastPart);
			}
			boolean found = false;
			for (JsonNode itemNode : arrayNode) {
				String primaryIdKey = singleFieldPrimaryIdName.getOrDefault(key, null);
				if (primaryIdKey != null && itemNode.has(primaryIdKey)) {
					Object idValue = itemNode.get(primaryIdKey);
					Object flatMapValue = getValueFromFlatMap(flatMap, key, primaryIdKey);
					if (idValue != null && flatMapValue != null && idValue.toString().equals(flatMapValue.toString())) {
						found = true;
						break;
					}
				}
			}
			if (!found && (ObjectUtils.isNotEmpty(value))) {
				ObjectNode valueNode = objectMapper.valueToTree(value);
				arrayNode.add(valueNode);
			}
		} else {
			if (ObjectUtils.isNotEmpty(value))
				currentNode.putPOJO(lastPart, value);
		}
	}

	private Object getValueFromFlatMap(Map<String, Object> flatMap, String path, String primaryIdKey) {
		Object flatMapValue = flatMap.getOrDefault(path + '.' + primaryIdKey, null);
		if (ObjectUtils.isEmpty(flatMapValue)) {
			try {
				Object instance = flatMap.getOrDefault(path, Object.class);
				Class<? extends Object> clz = instance.getClass();
				Field declaredField = clsUtil.getClassDeclaredField(clz, primaryIdKey);
				declaredField.setAccessible(true);
				flatMapValue = declaredField.get(instance);
			} catch (Exception e) {
				flatMapValue = null;
			}
		}
		return flatMapValue;
	}

	public List<JsonNode> orderResult(List<?> ids, List<JsonNode> result, String primaryField) {
		Map<String, JsonNode> mp = new HashMap<>();
		for (JsonNode node : result) {
			Object idValue = node.get(primaryField);
			mp.put(idValue.toString(), node);
		}
		List<JsonNode> output = new ArrayList<>();
		for (Object id : ids) {
			output.add(mp.get(id.toString()));
		}
		return output;
	}

}
