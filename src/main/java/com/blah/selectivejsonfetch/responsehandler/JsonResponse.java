package com.blah.selectivejsonfetch.responsehandler;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class JsonResponse {

	private String path;
	private LocalDateTime time;
	private String method;
	private String message;
	private Integer code;
	private Object data;
	
}