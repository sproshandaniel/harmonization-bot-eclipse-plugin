package com.zalaris.codebot.bot;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.zalaris.codebot.bot.BotResponse.Kind;
import com.zalaris.codebot.bot.BotResponse.RuleViolation;
import com.zalaris.codebot.adt.AbapEditorUtil;

/**
 * Simple rule-based bot for demo: - Validates the current ABAP object using
 * local rules - Provides a couple of hard-coded templates - Generates unit test
 * class or manual test cases on demand
 */
public class SimpleRuleBot {

	private static final String PROJECT_NAME = "DEMO_PROJECT";
	private static final String RULEPACK_NAME = "ABAP_CORE_STANDARDS";

	// --- Regexes for rules ----------------------------------------------------

	// SELECT * FROM <table>
	private static final Pattern SELECT_STAR_PATTERN = Pattern.compile("(?i)^\\s*SELECT\\s+\\*\\s+FROM\\s+\\S+");

	// Arithmetic assignment: lv_x = lv_y * lv_z (no SQL)
	private static final Pattern ARITH_ASSIGNMENT = Pattern.compile(
			"^[ \\t]*[A-Za-z0-9_\\->]+[ \\t]*=[ \\t]*[A-Za-z0-9_\\->]+[ \\t]*[+\\-*/][ \\t]*[A-Za-z0-9_\\->]+",
			Pattern.CASE_INSENSITIVE);

	// LOOP-like statements (LOOP, DO, WHILE)
	private static final Pattern LOOP_START_PATTERN = Pattern.compile("(?i)^\\s*(LOOP\\b|DO\\b|WHILE\\b)");

	private static final Pattern LOOP_END_PATTERN = Pattern
			.compile("(?i)^\\s*ENDLOOP\\b|^\\s*ENDDO\\b|^\\s*ENDWHILE\\b");

	// Simple TRY / CATCH boundaries
	private static final Pattern TRY_PATTERN = Pattern.compile("(?i)^\\s*TRY\\.");
	private static final Pattern ENDTRY_PATTERN = Pattern.compile("(?i)^\\s*ENDTRY\\.");
	private static final Pattern CATCH_PATTERN = Pattern.compile("(?i)^\\s*CATCH\\b");

	// --- Public entry point ---------------------------------------------------

	/**
	 * Entry point called from BotView: interpret the question and either: -
	 * validate current object - return template suggestions - generate hard coded
	 * unit test class - generate high-level manual test cases
	 */
	public BotResponse reply(String question) {
		if (question == null || question.trim().isEmpty()) {
			return new BotResponse(Kind.INFO,
					"Ask me to 'validate current object', 'Generate unit test class', or 'Generate test cases', or request a template, e.g. 'template for select', 'template for class'.");
		}

		String q = question.trim().toLowerCase();

		if (q.contains("validate")) {
			return validateCurrentObject();
		} else if (q.contains("template") && q.contains("select")) {
			return selectTemplate();
		} else if (q.contains("template") && q.contains("class")) {
			return classTemplate();
		} else if (q.contains("unit test class")) {
			// Keep existing behavior: returns ABAP Unit test class code
			return testCaseTemplate();
		} else if ((q.contains("generate") || q.contains("code"))
		        && q.contains("employees")
		        && q.contains("manager")) {
		    return employeesForManagerTemplate();	
		} else if (q.contains("test case")) {
			// NEW: returns manual test steps / scenarios
			return testStepsTemplate();
		}

		return new BotResponse(Kind.INFO,
				"I can help with:\n" + " - 'validate current object'\n" + " - 'Generate unit test class'\n"
						+ " - 'Generate test cases'\n" + " - 'template for select'\n" + " - 'template for class'");
	}

	// --- Validation pipeline --------------------------------------------------

	private BotResponse validateCurrentObject() {
		String code = AbapEditorUtil.getActiveEditorContentOrEmpty();

		List<RuleViolation> violations = new ArrayList<>();

		if (code == null || code.trim().isEmpty()) {
			return new BotResponse(Kind.INFO,
					"No ABAP source detected in the active editor. Open a program or class and try again.");
		}

		String[] lines = code.split("\\r?\\n");
		String upperFull = code.toUpperCase();

		// Run individual rule checks
		checkClassNamingRule(lines, violations);
		checkSelectStarRule(lines, violations);
		checkSelectInsideLoopRule(lines, violations);
		checkNestedLoopRule(lines, violations);
		checkTryCatchArithmeticRule(lines, upperFull, violations);

		if (violations.isEmpty()) {
			return new BotResponse(Kind.VALIDATION_RESULT,
					"Validation passed. No harmonization rule violations detected.", null, violations);
		} else {
			String msg = String.format("Validation failed. %d harmonization rule(s) were violated.", violations.size());
			return new BotResponse(Kind.VALIDATION_RESULT, msg, null, violations);
		}
	}

	// --- Rule 1: Class naming (ZCL_...) --------------------------------------

	private void checkClassNamingRule(String[] lines, List<RuleViolation> violations) {
		for (int i = 0; i < lines.length; i++) {
			String original = lines[i];
			String upper = original.trim().toUpperCase();

			// Look for "CLASS <name> DEFINITION"
			if (upper.startsWith("CLASS ") && upper.contains(" DEFINITION")) {
				String afterClass = upper.substring("CLASS ".length()).trim();
				String[] parts = afterClass.split("\\s+");
				if (parts.length > 0) {
					String className = parts[0];
					if (!className.startsWith("ZCL_")) {
						violations.add(new RuleViolation(PROJECT_NAME, RULEPACK_NAME, "ABAP.NAMING.CLASS",
								"Class names must start with ZCL_",
								"Class '" + className + "' does not follow the ZCL_ naming convention.", i + 1,
								getCorrectClassExample()));
					}
				}
			}
		}
	}

	// --- Rule 2: No SELECT * --------------------------------------------------

	private void checkSelectStarRule(String[] lines, List<RuleViolation> violations) {
		for (int i = 0; i < lines.length; i++) {
			String original = lines[i];
			String trimmed = original.trim();

			// Ignore pure comments
			if (trimmed.startsWith("*") || trimmed.startsWith("\"")) {
				continue;
			}

			Matcher m = SELECT_STAR_PATTERN.matcher(original);
			if (m.find()) {
				System.out.println("[CodeBot Debug] SELECT * violation on line " + (i + 1) + ": " + original);

				violations.add(new RuleViolation(PROJECT_NAME, RULEPACK_NAME, "ABAP.PERF.SELECT_STAR", "Avoid SELECT *",
						"Use an explicit field list instead of SELECT * for performance and stability.", i + 1,
						getCorrectSelectExample()));
				// Report first occurrence only (for demo)
				return;
			}
		}
	}

	// --- Rule 3: No SELECT inside loops --------------------------------------

	private void checkSelectInsideLoopRule(String[] lines, List<RuleViolation> violations) {
		int loopDepth = 0;

		for (int i = 0; i < lines.length; i++) {
			String original = lines[i];
			String trimmed = original.trim();

			if (trimmed.startsWith("*") || trimmed.startsWith("\"")) {
				continue; // ignore comments
			}

			// Detect loop starts
			if (LOOP_START_PATTERN.matcher(original).find()) {
				loopDepth++;
			}

			// If we are inside any loop, check for SELECT (but not comments)
			if (loopDepth > 0) {
				String upper = original.toUpperCase();
				if (upper.contains("SELECT ") && !upper.contains("SELECT-OPTIONS")) { // ignore SELECT-OPTIONS
					violations.add(new RuleViolation(PROJECT_NAME, RULEPACK_NAME, "ABAP.PERF.SELECT_IN_LOOP",
							"Avoid SELECT inside loop",
							"Database SELECT inside LOOP/DO/WHILE can cause severe performance issues. Buffer records or use JOINs where possible.",
							i + 1, getCorrectSelectInLoopExample()));
					// Report first occurrence only (for demo)
					return;
				}
			}

			// Detect loop ends
			if (LOOP_END_PATTERN.matcher(original).find()) {
				loopDepth = Math.max(0, loopDepth - 1);
			}
		}
	}

	// --- Rule 4: No nested loops ---------------------------------------------

	private void checkNestedLoopRule(String[] lines, List<RuleViolation> violations) {
		int loopDepth = 0;

		for (int i = 0; i < lines.length; i++) {
			String original = lines[i];
			String trimmed = original.trim();

			if (trimmed.startsWith("*") || trimmed.startsWith("\"")) {
				continue; // ignore comments
			}

			if (LOOP_START_PATTERN.matcher(original).find()) {
				loopDepth++;
				if (loopDepth > 1) {
					// We are entering a nested loop
					violations.add(new RuleViolation(PROJECT_NAME, RULEPACK_NAME, "ABAP.PERF.NESTED_LOOP",
							"Avoid nested loops over large datasets",
							"Nested loops can lead to quadratic runtime. Consider hashing, buffering into internal tables, or using JOINs.",
							i + 1, getCorrectNestedLoopExample()));
					return;
				}
			}

			if (LOOP_END_PATTERN.matcher(original).find()) {
				loopDepth = Math.max(0, loopDepth - 1);
			}
		}
	}

	// --- Rule 5: Arithmetic must be in TRY...CATCH ----------------------------

	private void checkTryCatchArithmeticRule(String[] lines, String upperFull, // kept for signature compatibility
			List<RuleViolation> violations) {

// Track whether we are currently inside a TRY block
		boolean insideTryBlock = false;

		for (int i = 0; i < lines.length; i++) {
			String original = lines[i];
			String trimmed = original.trim();

// Ignore comments
			if (trimmed.startsWith("*") || trimmed.startsWith("\"")) {
				continue;
			}

// TRY / CATCH / ENDTRY tracking
			if (TRY_PATTERN.matcher(original).find()) {
				insideTryBlock = true;
			} else if (CATCH_PATTERN.matcher(original).find() || ENDTRY_PATTERN.matcher(original).find()) {
				insideTryBlock = false;
			}

// Skip SQL lines entirely
			String upper = original.toUpperCase();
			if (upper.contains("SELECT ") || upper.contains("UPDATE ") || upper.contains("INSERT ")
					|| upper.contains("DELETE ")) {
				continue;
			}

// Detect arithmetic assignment
			if (ARITH_ASSIGNMENT.matcher(original).find()) {
				System.out.println("[CodeBot Debug] Arithmetic candidate on line " + (i + 1) + ": " + original);

// NEW: always require TRY...CATCH around arithmetic
				if (!insideTryBlock) {
					violations.add(new RuleViolation(PROJECT_NAME, RULEPACK_NAME, "ABAP.ARITH.MUST_USE_TRY_CATCH",
							"Use TRY...CATCH for risky arithmetic",
							"Arithmetic operations can overflow or divide by zero. Wrap them in TRY...CATCH.", i + 1,
							getCorrectArithmeticExample()));
					return; // report first occurrence only (for demo)
				}
			}
		}
	}

	// --- Templates ------------------------------------------------------------

	private BotResponse employeesForManagerTemplate() {
	    return new BotResponse(
	            Kind.TEMPLATE_SUGGESTION,
	            "Here is code to get employees for a manager using ZCL_UI5_REUSE=>GET_EMP_12_ACTIVE.",
	            getEmployeesForManagerExample());
	}
	
	private BotResponse selectTemplate() {
		return new BotResponse(Kind.TEMPLATE_SUGGESTION,
				"Here is a safe SELECT template using explicit field list (PA0001 as example).",
				getCorrectSelectExample());
	}

	private BotResponse classTemplate() {
		return new BotResponse(Kind.TEMPLATE_SUGGESTION, "Here is a harmonized class template (ZCL_ + public method).",
				getCorrectClassExample());
	}

	// Existing: ABAP Unit test class generator (keep as is)
	private BotResponse testCaseTemplate() {
		return new BotResponse(Kind.TEMPLATE_SUGGESTION,
				"Here are example ABAP Unit test cases for class ZCL_MFP_OPENAI_API (CONVERT_DATE_TO_INTERNAL).",
				getTestCaseExample());
	}

	// NEW: Manual test steps / scenarios
	private BotResponse testStepsTemplate() {
		return new BotResponse(Kind.TEMPLATE_SUGGESTION,
				"Here are high-level manual test cases for class ZCL_MFP_OPENAI_API.", getTestStepsExample());
	}

	// --- Example "correct" code snippets -------------------------------------

	private String getCorrectClassExample() {
		return "" + "CLASS ZCL_MY_CLASS DEFINITION\n" + "  PUBLIC\n" + "  FINAL\n" + "  CREATE PRIVATE.\n"
				+ "  PUBLIC SECTION.\n" + "    \" Singleton accessor\n" + "    CLASS-METHODS get_instance\n"
				+ "      RETURNING VALUE(ro_instance) TYPE REF TO zcl_my_class.\n" + "\n" + "    \" Public API\n"
				+ "    METHODS do_something.\n" + "  PROTECTED SECTION.\n" + "  PRIVATE SECTION.\n"
				+ "    \" Single shared instance\n" + "    CLASS-DATA go_instance TYPE REF TO zcl_my_class.\n"
				+ "ENDCLASS.\n" + "\n" + "CLASS ZCL_MY_CLASS IMPLEMENTATION.\n" + "  METHOD get_instance.\n"
				+ "    IF go_instance IS INITIAL.\n" + "      CREATE OBJECT go_instance.\n" + "    ENDIF.\n"
				+ "    ro_instance = go_instance.\n" + "  ENDMETHOD.\n" + "\n" + "  METHOD do_something.\n"
				+ "    \" TODO: implement logic\n" + "  ENDMETHOD.\n" + "ENDCLASS.\n";
	}

	private String getCorrectSelectExample() {
		return "" + "DATA: lt_pa0002 TYPE STANDARD TABLE OF pa0002,\n" + "      ls_pa0002 LIKE LINE OF lt_pa0002,\n"
				+ "      lv_pernr  TYPE pernr_d.\n" + "\n" + "SELECT pernr\n" + "       begda\n" + "       endda\n"
				+ "  FROM pa0002\n" + "  INTO TABLE @lt_pa0002\n" + "  WHERE pernr = @lv_pernr.\n";
	}

	private String getCorrectSelectInLoopExample() {
		return "" + "\" Bad:\n" + "\" LOOP AT lt_pernr INTO DATA(ls_pernr).\n"
				+ "\"   SELECT * FROM pa0002 INTO TABLE @DATA(lt_pa2)\n" + "\"     WHERE pernr = @ls_pernr-pernr.\n"
				+ "\" ENDLOOP.\n" + "\n" + "\" Better: buffer relevant data once, then loop in memory\n"
				+ "SELECT pernr begda endda\n" + "  FROM pa0002\n" + "  INTO TABLE @DATA(lt_pa2)\n"
				+ "  FOR ALL ENTRIES IN @lt_pernr\n" + "  WHERE pernr = @lt_pernr-pernr.\n" + "\n"
				+ "LOOP AT lt_pernr INTO DATA(ls_pernr).\n" + "  READ TABLE lt_pa2 INTO DATA(ls_pa2)\n"
				+ "    WITH KEY pernr = ls_pernr-pernr.\n" + "  IF sy-subrc = 0.\n" + "    \" process record\n"
				+ "  ENDIF.\n" + "ENDLOOP.\n";
	}

	private String getCorrectNestedLoopExample() {
		return "" + "\" Bad (nested loops):\n" + "\" LOOP AT lt_header INTO DATA(ls_header).\n"
				+ "\"   LOOP AT lt_item INTO DATA(ls_item)\n" + "\"     WHERE belnr = ls_header-belnr.\n"
				+ "\"     \" ...\n" + "\"   ENDLOOP.\n" + "\" ENDLOOP.\n" + "\n"
				+ "\" Better: use hashed table or sorted table for items\n"
				+ "DATA lt_item_by_key TYPE HASHED TABLE OF ty_item\n" + "  WITH UNIQUE KEY belnr posnr.\n" + "\n"
				+ "lt_item_by_key = lt_item.\n" + "\n" + "LOOP AT lt_header INTO DATA(ls_header).\n"
				+ "  READ TABLE lt_item_by_key INTO DATA(ls_item)\n" + "    WITH KEY belnr = ls_header-belnr.\n"
				+ "  IF sy-subrc = 0.\n" + "    \" ...\n" + "  ENDIF.\n" + "ENDLOOP.\n";
	}

	private String getCorrectArithmeticExample() {
		return "" + "DATA: lv_result  TYPE p DECIMALS 2,\n" + "      lv_amount1 TYPE p DECIMALS 2,\n"
				+ "      lv_amount2 TYPE p DECIMALS 2.\n" + "\n" + "TRY.\n"
				+ "    lv_result = lv_amount1 / lv_amount2.\n" + "  CATCH cx_sy_zerodivide.\n"
				+ "    \" Handle division by zero\n" + "    lv_result = 0.\n" + "  CATCH cx_sy_arithmetic_error.\n"
				+ "    \" Handle other arithmetic problems\n" + "    lv_result = 0.\n" + "ENDTRY.\n";
	}

	// Existing: hard-coded ABAP Unit tests for ZCL_MFP_OPENAI_API --------------

	private String getTestCaseExample() {
		return "" + "\" Place this local test class in the same include as ZCL_MFP_OPENAI_API\n"
				+ "\" or in a dedicated test include.\n" + "CLASS ltc_zcl_mfp_openai_api DEFINITION FINAL FOR TESTING\n"
				+ "  DURATION SHORT\n" + "  RISK LEVEL HARMLESS.\n" + "  PRIVATE SECTION.\n" + "    METHODS:\n"
				+ "      setup,\n" + "      teardown,\n" + "      test_convert_date_ddmmyyyy FOR TESTING,\n"
				+ "      test_convert_date_yyyymmdd FOR TESTING,\n" + "      test_convert_date_mmddyyyy FOR TESTING.\n"
				+ "ENDCLASS.\n" + "\n" + "CLASS ltc_zcl_mfp_openai_api IMPLEMENTATION.\n" + "  METHOD setup.\n"
				+ "    \" TODO: prepare shared test data if needed\n" + "  ENDMETHOD.\n" + "\n" + "  METHOD teardown.\n"
				+ "    \" TODO: cleanup after tests if needed\n" + "  ENDMETHOD.\n" + "\n"
				+ "  METHOD test_convert_date_ddmmyyyy.\n" + "    DATA lv_result TYPE datum.\n" + "\n"
				+ "    zcl_mfp_openai_api=>convert_date_to_internal(\n" + "      EXPORTING iv_date = '31.12.2025'\n"
				+ "      IMPORTING ev_date = lv_result ).\n" + "\n" + "    cl_abap_unit_assert=>assert_equals(\n"
				+ "      act = lv_result\n" + "      exp = '20251231'\n"
				+ "      msg = 'DD.MM.YYYY should convert to YYYYMMDD' ).\n" + "  ENDMETHOD.\n" + "\n"
				+ "  METHOD test_convert_date_yyyymmdd.\n" + "    DATA lv_result TYPE datum.\n" + "\n"
				+ "    zcl_mfp_openai_api=>convert_date_to_internal(\n" + "      EXPORTING iv_date = '2025-12-31'\n"
				+ "      IMPORTING ev_date = lv_result ).\n" + "\n" + "    cl_abap_unit_assert=>assert_equals(\n"
				+ "      act = lv_result\n" + "      exp = '20251231'\n"
				+ "      msg = 'YYYY-MM-DD should convert to YYYYMMDD' ).\n" + "  ENDMETHOD.\n" + "\n"
				+ "  METHOD test_convert_date_mmddyyyy.\n" + "    DATA lv_result TYPE datum.\n" + "\n"
				+ "    zcl_mfp_openai_api=>convert_date_to_internal(\n" + "      EXPORTING iv_date = '12/31/2025'\n"
				+ "      IMPORTING ev_date = lv_result ).\n" + "\n" + "    cl_abap_unit_assert=>assert_equals(\n"
				+ "      act = lv_result\n" + "      exp = '20251231'\n"
				+ "      msg = 'MM/DD/YYYY should convert to YYYYMMDD' ).\n" + "  ENDMETHOD.\n" + "ENDCLASS.\n";
	}

	// NEW: manual high-level test cases (3 per method) -------------------------

	private String getTestStepsExample() {
		return "" + "Test cases for class ZCL_MFP_OPENAI_API\n" + "=======================================\n\n"
				+ "1) Method: CONVERT_DATE_TO_INTERNAL\n" + "-----------------------------------\n" + "Test Case 1:\n"
				+ "  Execute method CONVERT_DATE_TO_INTERNAL with IV_DATE = '31.12.2025'.\n"
				+ "  Check that EV_DATE = '20251231'.\n\n" + "Test Case 2:\n"
				+ "  Execute method CONVERT_DATE_TO_INTERNAL with IV_DATE = '2025-12-31'.\n"
				+ "  Check that EV_DATE = '20251231'.\n\n" + "Test Case 3:\n"
				+ "  Execute method CONVERT_DATE_TO_INTERNAL with IV_DATE = '1.2.2025'.\n"
				+ "  Check that EV_DATE = '20250201'.\n\n" + "2) Method: READ_TRAVEL_RECEIPT_DATA\n"
				+ "-----------------------------------\n" + "Test Case 1:\n"
				+ "  Execute method READ_TRAVEL_RECEIPT_DATA with a valid hotel receipt PDF and correct mimetype.\n"
				+ "  Check that ET_DATA contains INVOICEDATE, AMOUNT, and CURRENCYCODE.\n\n" + "Test Case 2:\n"
				+ "  Execute method READ_TRAVEL_RECEIPT_DATA with a flight receipt including airports and trip details.\n"
				+ "  Check that ET_DATA contains CHECKIN, CHECKOUT, DEPARTUREAIRPORT, and ARRIVALAIRPORT.\n\n"
				+ "Test Case 3:\n" + "  Execute method READ_TRAVEL_RECEIPT_DATA with an unsupported mimetype.\n"
				+ "  Check that ET_DATA remains empty and the method exits without dump.\n\n"
				+ "3) Method: VALIDATE_REASON_FROM_OPENAI\n" + "--------------------------------------\n"
				+ "Test Case 1:\n"
				+ "  Execute method VALIDATE_REASON_FROM_OPENAI for role 'IT Operations' with a relevant justification.\n"
				+ "  Check that ET_RESPONSE contains STATUS = 'VALID'.\n\n" + "Test Case 2:\n"
				+ "  Execute method VALIDATE_REASON_FROM_OPENAI for role 'Finance & Accounting' with an unrelated justification.\n"
				+ "  Check that ET_RESPONSE contains STATUS = 'INVALID'.\n\n" + "Test Case 3:\n"
				+ "  Execute method VALIDATE_REASON_FROM_OPENAI with empty justification.\n"
				+ "  Check that STATUS is 'INVALID' or 'UNKNOWN' according to the design.\n";
	}
	
	private String getEmployeesForManagerExample() {
	    return ""
	        + "DATA: gv_pernr         TYPE pernr_d,\n"
	        + "      gv_role          TYPE zrole,          \" e.g. 'MSS'\n"
	        + "      lt_employees     TYPE STANDARD TABLE OF zty_employee,\n"
	        + "      lt_emp_12        TYPE STANDARD TABLE OF zty_emp_12,\n"
	        + "      gt_reportee_date TYPE zty_reportee_date.\n"
	        + "\n"
	        + "\" Manager personnel number\n"
	        + "gv_pernr = <manager_pernr>.\n"
	        + "gv_role  = 'MSS'.\n"
	        + "\n"
	        + "zcl_ui5_reuse=>get_emp_12_active(\n"
	        + "  EXPORTING\n"
	        + "    iv_pernr        = gv_pernr\n"
	        + "    iv_role         = gv_role\n"
	        + "    iv_scenario     = zcl_mfp_oadp=>c_md_teamviewer\n"
	        + "    iv_depth        = 0\n"
	        + "    iv_all          = abap_true\n"
	        + "  IMPORTING\n"
	        + "    ev_objects      = lt_employees\n"
	        + "    ev_active_12    = lt_emp_12\n"
	        + "    ev_reportee_date = gt_reportee_date\n"
	        + ").\n"
	        + "\n"
	        + "\" lt_employees now contains all direct reportees\n"
	        + "\" lt_emp_12 contains 12-month active employees\n";
	}

}