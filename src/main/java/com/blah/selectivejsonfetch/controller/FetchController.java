package com.blah.selectivejsonfetch.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.blah.selectivejsonfetch.responsehandler.ResponseHandler;
import com.blah.selectivejsonfetch.service.IFetchService;

import jakarta.servlet.http.HttpServletRequest;

@RestController
public class FetchController {

	@Autowired
	private IFetchService service;

	@Autowired
	private ResponseHandler responseHandler;

	@PostMapping("/getEntityData")
	public ResponseEntity<?> getEntityData(HttpServletRequest request, @RequestBody Map<String, Object> requestPayload,
			Pageable pageable) throws Exception {
		return responseHandler.generateGetResponse(service.fetch(requestPayload, pageable), request);
	}

}
