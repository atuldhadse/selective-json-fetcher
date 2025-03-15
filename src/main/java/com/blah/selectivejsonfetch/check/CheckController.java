package com.blah.selectivejsonfetch.check;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CheckController {

	@GetMapping("/checkrun")
	public String run() {
		return "<html><body><h1>Hello World.!!!</h1></body></html>";
	}

}
