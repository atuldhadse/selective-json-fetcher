package com.blah.selectivejsonfetch.responsehandler;

import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

@Component
public class ResponseHandler {

	public ResponseEntity<JsonResponse> generateGetResponse(Object responseObj, HttpServletRequest request) {
		return new ResponseEntity<JsonResponse>(
				JsonResponse.builder().path(request.getRequestURI()).time(LocalDateTime.now())
						.message(HttpStatus.OK.getReasonPhrase()).code(HttpStatus.OK.value()).data(responseObj).build(),
				HttpStatus.OK);
	}

}