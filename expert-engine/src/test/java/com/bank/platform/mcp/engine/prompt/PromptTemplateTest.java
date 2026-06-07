package com.bank.platform.mcp.engine.prompt;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromptTemplateTest {

    @Test
    void substitutesPlainVariables() {
        var out = PromptTemplate.compile("Hello {{name}}, you are {{role}}.")
                .render(PromptModel.create().put("name", "Sam").put("role", "reviewer"));
        assertThat(out).isEqualTo("Hello Sam, you are reviewer.");
    }

    @Test
    void missingVariableRendersEmpty() {
        var out = PromptTemplate.compile("[{{missing}}]").render(PromptModel.create());
        assertThat(out).isEqualTo("[]");
    }

    @Test
    void tripleBracesInsertLiterallyJustLikeDouble() {
        var out = PromptTemplate.compile("{{a}} {{{a}}}")
                .render(PromptModel.create().put("a", "<x> & y"));
        // No HTML escaping — prompts are not HTML.
        assertThat(out).isEqualTo("<x> & y <x> & y");
    }

    @Test
    void substitutedValuesAreNotReparsedAsTemplate() {
        // Injection safety: a value that looks like a tag is emitted verbatim.
        var out = PromptTemplate.compile("{{a}}")
                .render(PromptModel.create().put("a", "{{b}}").put("b", "SECRET"));
        assertThat(out).isEqualTo("{{b}}");
    }

    @Test
    void booleanSectionAndInverted() {
        var t = PromptTemplate.compile("{{#on}}YES{{/on}}{{^on}}NO{{/on}}");
        assertThat(t.render(PromptModel.create().put("on", true))).isEqualTo("YES");
        assertThat(t.render(PromptModel.create().put("on", false))).isEqualTo("NO");
        assertThat(t.render(PromptModel.create())).isEqualTo("NO"); // missing == falsy
    }

    @Test
    void iteratesScalarListWithDot() {
        var out = PromptTemplate.compile("{{#items}}- {{.}}\n{{/items}}")
                .render(PromptModel.create().strings("items", List.of("a", "b", "c")));
        assertThat(out).isEqualTo("- a\n- b\n- c\n");
    }

    @Test
    void emptyListRendersNothingAndInvertedFires() {
        var t = PromptTemplate.compile("{{#items}}x{{/items}}{{^items}}empty{{/items}}");
        assertThat(t.render(PromptModel.create().strings("items", List.of()))).isEqualTo("empty");
    }

    @Test
    void iteratesObjectListWithFieldsAndParentFallback() {
        var out = PromptTemplate.compile("{{#users}}{{prefix}}{{name}}:{{role}};{{/users}}")
                .render(PromptModel.create()
                        .put("prefix", ">") // resolved from parent scope inside the section
                        .objects("users", List.of(
                                PromptModel.create().put("name", "a").put("role", "x"),
                                PromptModel.create().put("name", "b").put("role", "y"))));
        assertThat(out).isEqualTo(">a:x;>b:y;");
    }

    @Test
    void commentsAreDropped() {
        var out = PromptTemplate.compile("a{{! hidden }}b").render(PromptModel.create());
        assertThat(out).isEqualTo("ab");
    }

    @Test
    void standaloneBlockTagsDoNotLeaveBlankLines() {
        String tpl = "start\n{{#s}}\nin\n{{/s}}\nend\n";
        var out = PromptTemplate.compile(tpl).render(PromptModel.create().put("s", true));
        assertThat(out).isEqualTo("start\nin\nend\n");
    }

    @Test
    void nestedSections() {
        String tpl = "{{#outer}}[{{#inner}}{{.}}{{/inner}}]{{/outer}}";
        var out = PromptTemplate.compile(tpl).render(PromptModel.create()
                .put("outer", true)
                .strings("inner", List.of("1", "2")));
        assertThat(out).isEqualTo("[12]");
    }

    @Test
    void malformedTemplatesAreRejected() {
        assertThatThrownBy(() -> PromptTemplate.compile("{{unclosed"))
                .isInstanceOf(PromptTemplateException.class);
        assertThatThrownBy(() -> PromptTemplate.compile("{{#a}}no close"))
                .isInstanceOf(PromptTemplateException.class)
                .hasMessageContaining("unclosed section");
        assertThatThrownBy(() -> PromptTemplate.compile("{{#a}}{{/b}}"))
                .isInstanceOf(PromptTemplateException.class)
                .hasMessageContaining("mismatched");
        assertThatThrownBy(() -> PromptTemplate.compile("{{> partial }}"))
                .isInstanceOf(PromptTemplateException.class)
                .hasMessageContaining("partials");
    }
}
