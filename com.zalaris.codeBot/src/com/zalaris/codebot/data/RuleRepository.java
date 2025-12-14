package com.zalaris.codebot.data;

import com.zalaris.codebot.model.Rule;
import com.zalaris.codebot.model.RuleType;
import com.zalaris.codebot.model.Severity;

import java.util.Arrays;
import java.util.List;

public class RuleRepository {

    // Hardcoded list for now – later you can replace this with backend calls.
    private static final List<Rule> RULES = Arrays.asList(

        // 1) DB no SELECT *
        new Rule(
            "abap.db.no_select_star",
            "Avoid SELECT * on HR tables",
            "SELECT * on PAxxxx tables loads unnecessary columns and impacts performance.",
            RuleType.CODE,
            Severity.ERROR,
            "(?i)SELECT\\s+\\*\\s+FROM\\s+PA\\d{4}",   // simple regex for demo
            "SELECT * FROM pa0001 INTO TABLE @DATA(lt_pa0001).",
            "SELECT pernr, persg FROM pa0001 INTO TABLE @DATA(lt_pa0001)."
        ),

        // 2) TRY/CATCH for arithmetic
        new Rule(
            "abap.arith.must_use_try_catch",
            "Use TRY...CATCH for risky arithmetic",
            "Arithmetic with payroll amounts may overflow. Wrap in TRY...CATCH.",
            RuleType.CODE,
            Severity.WARNING,
            "=",   // for demo: you’ll combine this with context (line contains = and p-type, etc.)
            "lv_total = lv_amount * lv_rate.",
            "TRY.\n  lv_total = lv_amount * lv_rate.\nCATCH cx_sy_arithmetic_error.\n  lv_total = 0.\nENDTRY."
        ),

        // 3) No SELECT inside LOOP
        new Rule(
            "abap.perf.no_select_inside_loop",
            "Avoid SELECT inside LOOP",
            "Do not perform SELECT on HR infotypes inside LOOP. Use bulk SELECT.",
            RuleType.PERFORMANCE,
            Severity.ERROR,
            "(?i)LOOP AT|SELECT\\s+.*FROM\\s+PA\\d{4}", // you can refine
            "",
            ""
        ),

        // 4) No nested loops
        new Rule(
            "abap.perf.no_nested_loops_large_tables",
            "Avoid nested loops on large HR tables",
            "Nested loops on PAxxxx tables cause O(n^2) behavior. Use hashed tables.",
            RuleType.PERFORMANCE,
            Severity.WARNING,
            "(?i)LOOP AT.*\\n.*LOOP AT",   // very simplified
            "",
            ""
        ),

        // 5) Design: do not copy standard program
        new Rule(
            "abap.design.no_copy_standard_programs",
            "Do not copy standard HR programs",
            "Copying SAP standard HR reports to Z/Y namespace causes upgrade issues.",
            RuleType.DESIGN,
            Severity.WARNING,
            "(?i)Copied from SAP",   // trigger phrase in comment
            "\" Copied from SAPFALHR",
            ""
        )
    );

    public static List<Rule> getAllRules() {
        return RULES;
    }
}
