package com.blah.selectivejsonfetch.util;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.blah.selectivejsonfetch.enums.Enums.JPAOperators;

import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Transient;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

@Component
public class JPAUtil {

	@Autowired
	private ClsUtil clsUtil;

	@Autowired
	private Map<Class<?>, Object> defaultValueProvider;

	public Predicate buildPredicate(String path, String operator, Object value, CriteriaBuilder cb, Root<?> root,
			Predicate existingPredicate, String logicalOperator, Map<String, Path<?>> joined) {
		String[] fields = path.split("\\.");
		Path<?> pathExpression = getPath(root, fields, joined);
		String fieldName = fields[fields.length - 1];
		Predicate predicate = null;
		List<Predicate> extractFilterPredicates = addWhereConditionForExtractFilter(cb, pathExpression, fieldName,
				operator, (String) value);
		if (!extractFilterPredicates.isEmpty()) {
			predicate = extractFilterPredicates.get(0);
		}
		if (StringUtils.isBlank(logicalOperator) || logicalOperator.equalsIgnoreCase("AND")) {
			return cb.and(existingPredicate, predicate);
		} else if (logicalOperator.equalsIgnoreCase("OR")) {
			return cb.or(existingPredicate, predicate);
		} else {
			throw new IllegalArgumentException("Unsupported logical operator: " + logicalOperator);
		}
	}

	public List<Predicate> addWhereConditionForExtractFilter(CriteriaBuilder cb, Path<?> path, String fieldName,
			String operator, String values) {
		List<String> valueList = Arrays.asList(values.split(",")).stream().filter(StringUtils::isNotBlank)
				.map(String::trim).toList();
		if (valueList.isEmpty())
			return Collections.emptyList();

		List<Predicate> predicates = new ArrayList<>();
		Class<?> fieldType = getProperPath(path, fieldName).getJavaType();
		Object firstValue = clsUtil.convertValueToCorrectType(valueList.get(0), fieldType);

		Expression<?> comparePath = cb.coalesce(getProperPath(path, fieldName), defaultValueProvider.get(fieldType));

		if (operator.equals(JPAOperators.EQUALS.getOperator())) {
			predicates.add(cb.equal(comparePath, firstValue));
		} else if (operator.equals(JPAOperators.NOT_EQUAL.getOperator())) {
			predicates.add(cb.notEqual(comparePath, firstValue));
		} else if (operator.equals(JPAOperators.GREATER.getOperator())) {
			predicates.add(cb.greaterThan((Expression) comparePath, (Comparable) firstValue));
		} else if (operator.equals(JPAOperators.GREATER_EQUALS.getOperator())) {
			predicates.add(cb.greaterThanOrEqualTo((Expression<Comparable>) comparePath, (Comparable) firstValue));
		} else if (operator.equals(JPAOperators.SMALLER.getOperator())) {
			predicates.add(cb.lessThan((Expression<Comparable>) comparePath, (Comparable) firstValue));
		} else if (operator.equals(JPAOperators.SMALLER_EQUALS.getOperator())) {
			predicates.add(cb.lessThanOrEqualTo((Expression<Comparable>) comparePath, (Comparable) firstValue));
		} else if (operator.equals(JPAOperators.BETWEEN.getOperator())) {
			Object secondValue = valueList.size() > 1 ? clsUtil.convertValueToCorrectType(valueList.get(1), fieldType)
					: firstValue;
			predicates.add(cb.between((Expression<Comparable>) comparePath, (Comparable) firstValue,
					(Comparable) secondValue));
		} else if (operator.equals(JPAOperators.IS_NULL.getOperator())) {
			predicates.add(cb.isNull(comparePath));
		} else if (operator.equals(JPAOperators.IS_NOT_NULL.getOperator())) {
			predicates.add(cb.isNotNull(comparePath));
		} else if (operator.equals(JPAOperators.INCLUDES.getOperator())) {
			List<Object> castedValues = valueList.stream().map(v -> clsUtil.convertValueToCorrectType(v, fieldType))
					.toList();
			predicates.add(comparePath.in(castedValues));
		} else if (operator.equals(JPAOperators.EXCLUDES.getOperator())) {
			List<Object> castedValues = valueList.stream().map(v -> clsUtil.convertValueToCorrectType(v, fieldType))
					.toList();
			predicates.add(cb.not(comparePath.in(castedValues)));
		} else if (operator.equals(JPAOperators.LIKE.getOperator())) {
			if (fieldType.equals(String.class)) {
				predicates.add(cb.like((Expression<String>) comparePath, "%" + firstValue + "%"));
			} else {
				predicates.add(cb.like(comparePath.as(String.class), "%" + firstValue + "%"));
			}
		} else if (operator.equals(JPAOperators.IS_TRUE.getOperator())) {
			if (fieldType.equals(Boolean.class) || fieldType.equals(boolean.class)) {
				predicates.add(cb.isTrue((Expression<Boolean>) comparePath));
			}
		} else if (operator.equals(JPAOperators.IS_NOT_TRUE.getOperator())) {
			if (fieldType.equals(Boolean.class) || fieldType.equals(boolean.class)) {
				predicates.add(cb.isFalse((Expression<Boolean>) comparePath));
			}
		}
		return predicates;
	}

	private Path<?> getProperPath(Path<?> path, String fieldName) {
		try {
			path.get(fieldName).getJavaType();
			return path.get(fieldName);
		} catch (Exception e) {
			return path;
		}
	}

	public Path<?> getPath(Root<?> root, String[] fields, Map<String, Path<?>> joined) {
		Join<?, ?> join;
		if (fields.length <= 1) {
			// Check if the field exists in the superclass or current root
			String fieldName = fields[0];
			return resolveField(root, fieldName);
		} else {
			if (!Optional.ofNullable(joined.getOrDefault(fields[0], null)).isPresent()) {
				join = root.join(fields[0], JoinType.LEFT);
				joined.put(fields[0], join);
			} else {
				join = (Join<?, ?>) joined.get(fields[0]);
			}
		}

		StringBuilder pathTillNow = new StringBuilder();
		pathTillNow.append(fields[0]);
		for (int i = 1; i < fields.length - 1; i++) {
			pathTillNow.append('.').append(fields[i]);
			if (!Optional.ofNullable(joined.getOrDefault(pathTillNow.toString(), null)).isPresent()) {
				join = join.join(fields[i], JoinType.LEFT);
				joined.put(pathTillNow.toString(), join);
			} else {
				join = (Join<?, ?>) joined.get(pathTillNow.toString());
			}
		}

		// Resolve the final field, whether in the current class or superclass
		return resolveField(join, fields[fields.length - 1]);
	}

	private Path<?> resolveField(Path<?> path, String fieldName) {
		try {
			// Attempt to get the path normally
			return path.get(fieldName);
		} catch (IllegalArgumentException e) {
			// Traverse superclass attributes if the field is not found in the current path
			Class<?> javaType = path.getJavaType();
			Field field = clsUtil.findFieldInClassHierarchy(javaType, fieldName);
			if (field != null) {
				return path.get(field.getName());
			}
			throw new IllegalArgumentException(
					"Field '" + fieldName + "' not found in entity or its superclass: " + javaType.getName(), e);
		}
	}

	public Path<?> buildPath(String field, Root<?> root, Map<String, Path<?>> joined) throws Exception {
		String[] fieldParts = field.split("\\.");
		Path<?> path = root;
		StringBuilder pathTillNow = new StringBuilder();
		for (int i = 0; i < fieldParts.length; i++) {
			String part = fieldParts[i];
			if (pathTillNow.length() > 0) {
				pathTillNow.append('.');
			}
			pathTillNow.append(part);
			if (i < fieldParts.length - 1) {
				if (!Optional.ofNullable(joined.getOrDefault(pathTillNow.toString(), null)).isPresent()) {
					path = ((From<?, ?>) path).join(part, JoinType.LEFT);
					joined.put(pathTillNow.toString(), path);
				} else {
					path = joined.get(pathTillNow.toString());
				}
			} else {
				Field f = clsUtil.findFieldInHierarchy(path.getJavaType(), part);
				if (f.isAnnotationPresent(Transient.class)
						|| f.isAnnotationPresent(org.springframework.data.annotation.Transient.class)) {
					return null;
				}
				if (f.isAnnotationPresent(OneToOne.class) || f.isAnnotationPresent(ManyToOne.class)
						|| f.isAnnotationPresent(OneToMany.class) || f.isAnnotationPresent(ManyToMany.class)) {
					if (!Optional.ofNullable(joined.getOrDefault(pathTillNow.toString(), null)).isPresent()) {
						path = ((From<?, ?>) path).join(part, JoinType.LEFT);
						joined.put(pathTillNow.toString(), path);
					} else {
						path = joined.get(pathTillNow.toString());
					}
				} else {
					path = path.get(part);
				}
			}
		}
		return path;
	}

}
