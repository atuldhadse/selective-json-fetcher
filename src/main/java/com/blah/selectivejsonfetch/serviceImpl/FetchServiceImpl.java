package com.blah.selectivejsonfetch.serviceImpl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import com.blah.selectivejsonfetch.service.IFetchService;
import com.blah.selectivejsonfetch.util.ClsUtil;
import com.blah.selectivejsonfetch.util.JPAUtil;
import com.fasterxml.jackson.databind.JsonNode;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;

@Service
public class FetchServiceImpl implements IFetchService {

	@PersistenceContext
	private EntityManager entityManager;

	@Autowired
	private ClsUtil clsUtil;

	@Autowired
	private JPAUtil jpaUtil;

	@Autowired
	private FetchHelper helper;

	@Override
	public Page<JsonNode> fetch(Map<String, Object> input, Pageable page) throws Exception {
		helper.checkFilters(input);
		String entityName = (String) input.getOrDefault("class", "");
		Class<?> entityClass = clsUtil.getClassForName(entityName);
		Class<?> primaryKeyType = clsUtil.getPrimaryKeyType(entityClass);
		String primaryField = clsUtil.getPrimaryKeyField(entityClass);
		Page<Object> idsWithPageData = findPrimaryKeyByFilter(input, primaryField, page);
		List<?> ids = idsWithPageData.toList().stream().map(primaryKeyType::cast).toList();
		String fields;

		// Fetch only annotated fields (with @Column or relationship annotations)
		// including superclass fields
		if (ObjectUtils.isEmpty(input.get("fields"))) {
			fields = primaryField;
		} else {
			fields = (String) input.get("fields");
		}

		String[] fieldSplitArray = fields.split(",\\s*");
		Map<String, String> singleFieldPrimaryIdName = new HashMap<>();
		Map<String, List<Class<?>>> fieldsTypeMap = clsUtil.getFieldsTypeMap(entityClass, fieldSplitArray);
		String[] fieldArray = helper.getUniqueFieldArray(fields, entityClass, primaryField, singleFieldPrimaryIdName,
				fieldSplitArray, fieldsTypeMap);
		CriteriaBuilder cb = entityManager.getCriteriaBuilder();
		CriteriaQuery<Object[]> cq = cb.createQuery(Object[].class);
		Root<?> root = cq.from(entityClass);
		List<Selection<?>> selections = new ArrayList<>();
		Map<String, Path<?>> joined = new HashMap<>();
		List<String> jsonGetterFields = new ArrayList<>();

		Map<String, Integer> fieldsSelectionsListIdx = new HashMap<>();
		int idx = 0;
		List<String> newFields = new ArrayList<>();
		for (String field : fieldArray) {
			Path<?> buildPath = jpaUtil.buildPath(field.trim(), root, joined);
			if (Optional.ofNullable(buildPath).isPresent()) {
				selections.add(buildPath);
				fieldsSelectionsListIdx.put(field, idx++);
				newFields.add(field);
			} else {
				jsonGetterFields.add(field);
			}
		}
		Map<String, Object[]> dependentFieldsForJsonGetterFields = helper
				.getDependentFieldsForJsonGetterFields(jsonGetterFields, fieldsTypeMap, entityClass);
		for (Entry<String, Object[]> entry : dependentFieldsForJsonGetterFields.entrySet()) {
			Object[] value = entry.getValue();
			List<String> dependentFieldsName = (List<String>) value[0];
			List<Integer> dependentFieldsSelectionListIdx = (List<Integer>) value[1];
			for (String dependentField : dependentFieldsName) {
				if (fieldsSelectionsListIdx.getOrDefault(dependentField, -1) == -1) {
					Path<?> buildPath = jpaUtil.buildPath(dependentField.trim(), root, joined);
					if (Optional.ofNullable(buildPath).isPresent()) {
						selections.add(buildPath);
						fieldsSelectionsListIdx.put(dependentField, idx++);
					}
				}
				dependentFieldsSelectionListIdx.add(fieldsSelectionsListIdx.get(dependentField));
			}
			newFields.add(entry.getKey());
		}

		cq.multiselect(selections);
		cq.where(root.get(clsUtil.getPrimaryKeyFieldName(entityClass)).in(ids));
		List<Object[]> rs = entityManager.createQuery(cq).getResultList();
		List<Map<String, Object>> output = new ArrayList<>();
		if (rs.isEmpty()) {
			return Page.empty(page);
		}
		boolean isSingleField = !(rs.get(0) instanceof Object[]);
		for (Object result : rs) {
			Map<String, Object> mp = new LinkedHashMap<>();
			if (isSingleField) {
				mp.put(fieldArray[0], result);
			} else {
				Object[] objArray = (Object[]) result;
				for (int i = 0; i < newFields.size(); i++) {
					if (jsonGetterFields.contains(newFields.get(i))) {
						mp.put(newFields.get(i), helper.getTransientFieldValue(fieldsTypeMap,
								dependentFieldsForJsonGetterFields, newFields.get(i), objArray, entityClass));
					} else {
						mp.put(newFields.get(i), objArray[i]);
					}
				}
			}
			output.add(mp);
		}
		List<JsonNode> result = helper.prettyfy(output, primaryField, fieldsTypeMap, singleFieldPrimaryIdName);
		List<JsonNode> orderResult = helper.orderResult(ids, result, primaryField);
		return new PageImpl<>(orderResult, idsWithPageData.getPageable(), idsWithPageData.getTotalElements());
	}

	public Page<Object> findPrimaryKeyByFilter(Map<String, Object> filterMap, String primaryField, Pageable page) {
		try {
			CriteriaBuilder cb = entityManager.getCriteriaBuilder();
			CriteriaQuery<Object> cq = cb.createQuery();
			Object entityName = filterMap.getOrDefault("class", "");
			Class<?> entityClass = clsUtil.getClassForName(entityName.toString());
			Root<?> root = cq.from(entityClass);
			String logicalOperator;
			if (ObjectUtils.isEmpty(filterMap.get("logicalOperator"))) {
				logicalOperator = "AND";
			} else {
				logicalOperator = (String) filterMap.get("logicalOperator");
			}
			List<Map<String, Object>> filters;
			if (ObjectUtils.isEmpty(filterMap.get("filters"))) {
				filters = Collections.emptyList();
			} else {
				filters = (List<Map<String, Object>>) filterMap.get("filters");
			}
			Predicate predicate = cb.conjunction();
			Map<String, Path<?>> joined = new HashMap<>();
			for (Map<String, Object> filter : filters) {
				String path = (String) filter.get("path");
				String operator = (String) filter.get("operator");
				Object value = filter.get("value");
				predicate = jpaUtil.buildPredicate(path, operator, value, cb, root, predicate, logicalOperator, joined);
			}
			cq.select(root.get(primaryField)).where(predicate);
			if (page.getSort().isSorted()) {
				List<jakarta.persistence.criteria.Order> orders = new ArrayList<>();
				for (Sort.Order order : page.getSort()) {
					String sortingPath = order.getProperty();
					String[] fields = sortingPath.split("\\.");
					Path<?> pathExpression = jpaUtil.getPath(root, fields, joined);
					orders.add(order.isAscending() ? cb.asc(pathExpression.get(fields[fields.length - 1]))
							: cb.desc(pathExpression.get(fields[fields.length - 1])));
				}
				cq.orderBy(orders);
			}
			TypedQuery<Object> query = entityManager.createQuery(cq);
			query.setFirstResult((int) page.getOffset());
			query.setMaxResults(page.getPageSize());
			List<Object> rs = query.getResultList();

			CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
			Root<?> countRoot = countQuery.from(entityClass);
			Predicate countPredicate = cb.conjunction();
			Map<String, Path<?>> countJoined = new HashMap<>();
			for (Map<String, Object> filter : filters) {
				String path = (String) filter.get("path");
				String operator = (String) filter.get("operator");
				Object value = filter.get("value");
				countPredicate = jpaUtil.buildPredicate(path, operator, value, cb, countRoot, countPredicate,
						logicalOperator, countJoined);
			}
			countQuery.select(cb.count(countRoot)).where(countPredicate);
			Long total = entityManager.createQuery(countQuery).getSingleResult();
			return new PageImpl<>(rs, page, total);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return Page.empty(page);
	}

}
