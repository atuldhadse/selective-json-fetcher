package com.blah.selectivejsonfetch.service;

import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.fasterxml.jackson.databind.JsonNode;

public interface IFetchService {

	Page<JsonNode> fetch(Map<String, Object> input, Pageable page) throws Exception;

}
