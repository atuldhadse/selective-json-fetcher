package com.blah.selectivejsonfetch.enums;

public class Enums {

	public enum JPAOperators {
		EQUALS("="), GREATER(">"), GREATER_EQUALS(">="), SMALLER("<"), SMALLER_EQUALS("<="), NOT_EQUAL("!="),
		LIKE("Like"), BETWEEN("Between"), IS_NULL("Is Null"), IS_NOT_NULL("Is Not Null"), INCLUDES("Includes"),
		EXCLUDES("Excludes"), IS_TRUE("Is True"), IS_NOT_TRUE("Is Not True");

		private final String operator;

		private JPAOperators(String operator) {
			this.operator = operator;
		}

		public String getOperator() {
			return operator;
		}
	}

}
