package com.zalaris.codebot.data;

import com.zalaris.codebot.model.Template;

import java.util.Arrays;
import java.util.List;

public class TemplateRepository {

    private static final List<Template> TEMPLATES = Arrays.asList(

        // Singleton class
        new Template(
            "abap.template.singleton_class",
            "Singleton class template",
            "ABAP OO singleton pattern.",
            Arrays.asList("singleton class", "create singleton"),
            "CLASS zcl_singleton_demo DEFINITION PUBLIC CREATE PRIVATE.\n" +
            "  PUBLIC SECTION.\n" +
            "    CLASS-METHODS get_instance\n" +
            "      RETURNING VALUE(ro_instance) TYPE REF TO zcl_singleton_demo.\n" +
            "  PRIVATE SECTION.\n" +
            "    CLASS-DATA go_instance TYPE REF TO zcl_singleton_demo.\n" +
            "    METHODS constructor.\n" +
            "ENDCLASS.\n\n" +
            "CLASS zcl_singleton_demo IMPLEMENTATION.\n" +
            "  METHOD get_instance.\n" +
            "    IF go_instance IS INITIAL.\n" +
            "      CREATE OBJECT go_instance.\n" +
            "    ENDIF.\n" +
            "    ro_instance = go_instance.\n" +
            "  ENDMETHOD.\n\n" +
            "  METHOD constructor.\n" +
            "  ENDMETHOD.\n" +
            "ENDCLASS.\n"
        ),

        // AMDP class
        new Template(
            "abap.template.amdp_class",
            "HR AMDP class template",
            "AMDP class example working with PA0001.",
            Arrays.asList("create amdp", "amdp class"),
            "CLASS zcl_hr_amdp_demo DEFINITION PUBLIC FINAL CREATE PUBLIC.\n" +
            "  PUBLIC SECTION.\n" +
            "    INTERFACES if_amdp_marker_hdb.\n\n" +
            "    CLASS-METHODS read_employees\n" +
            "      IMPORTING iv_pernr TYPE pernr_d\n" +
            "      EXPORTING et_p0001 TYPE STANDARD TABLE.\n" +
            "ENDCLASS.\n\n" +
            "CLASS zcl_hr_amdp_demo IMPLEMENTATION.\n" +
            "  METHOD read_employees BY DATABASE PROCEDURE FOR HDB LANGUAGE SQLSCRIPT USING pa0001.\n" +
            "    et_p0001 =\n" +
            "      SELECT pernr, bukrs, persg, plans\n" +
            "      FROM pa0001\n" +
            "      WHERE pernr = :iv_pernr;\n" +
            "  ENDMETHOD.\n" +
            "ENDCLASS.\n"
        ),

        // Personnel area text
        new Template(
            "abap.template.personnel_area_text",
            "Personnel area text lookup",
            "Get personnel area text from T500P.",
            Arrays.asList("personnel area text", "get text for personnel area"),
            "DATA: lv_persa TYPE persa,\n" +
            "      lv_name1 TYPE t500p-name1.\n\n" +
            "lv_persa = '1000'.\n\n" +
            "SELECT SINGLE name1\n" +
            "  FROM t500p\n" +
            "  INTO @lv_name1\n" +
            "  WHERE persa = @lv_persa.\n\n" +
            "IF sy-subrc = 0.\n" +
            "  \" Use lv_name1\n" +
            "ENDIF.\n"
        ),

        // Employees for manager
        new Template(
            "abap.template.get_employees_for_manager",
            "Get employees for manager",
            "Utility method to retrieve direct reports in HR org model.",
            Arrays.asList("get employees for manager", "employee list under manager"),
            "CLASS zcl_hr_org_utils DEFINITION PUBLIC CREATE PUBLIC.\n" +
            "  PUBLIC SECTION.\n" +
            "    CLASS-METHODS get_direct_reports\n" +
            "      IMPORTING iv_manager_pernr TYPE pernr_d\n" +
            "      RETURNING VALUE(rt_employees) TYPE STANDARD TABLE.\n" +
            "ENDCLASS.\n\n" +
            "CLASS zcl_hr_org_utils IMPLEMENTATION.\n" +
            "  METHOD get_direct_reports.\n" +
            "    SELECT stext AS emp_pernr\n" +
            "      FROM hrp1001\n" +
            "      WHERE objid = @iv_manager_pernr\n" +
            "        AND rsign = 'A'\n" +
            "        AND relat = '002'\n" +
            "      INTO TABLE @rt_employees.\n" +
            "  ENDMETHOD.\n" +
            "ENDCLASS.\n"
        )
    );

    public static List<Template> getAllTemplates() {
        return TEMPLATES;
    }
}

