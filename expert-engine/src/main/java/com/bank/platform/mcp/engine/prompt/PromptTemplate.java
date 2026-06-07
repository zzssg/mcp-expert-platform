package com.bank.platform.mcp.engine.prompt;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A deterministic, logic-light prompt templater (Part 2.7) — a small, audited subset
 * of Mustache, chosen because prompts must be diffable and auditable and must never
 * execute arbitrary logic. Supported syntax:
 * <ul>
 *   <li><code>{{name}}</code> / <code>{{{name}}}</code> — insert a value <em>literally</em>;</li>
 *   <li><code>{{#name}}…{{/name}}</code> — section: render once for a truthy value, once
 *       per element for a list (pushing each element as scope), skipped if falsy/empty;</li>
 *   <li><code>{{^name}}…{{/name}}</code> — inverted section: render only when falsy/empty;</li>
 *   <li><code>{{.}}</code> — the current element inside a scalar-list section;</li>
 *   <li><code>{{! comment }}</code> — dropped.</li>
 * </ul>
 *
 * <p><b>Injection-safe by construction.</b> The template is parsed once into a node
 * tree; at render time each variable is replaced exactly once by its value, and that
 * value is <em>never re-parsed</em> as template syntax. A user-supplied value
 * containing <code>{{evil}}</code> is emitted verbatim. There is deliberately no
 * HTML escaping — prompts are not HTML, and escaping would corrupt code/diffs.
 *
 * <p>Compiled templates are immutable and thread-safe; compile/load once and reuse.
 */
public final class PromptTemplate {

    private static final ConcurrentHashMap<String, PromptTemplate> CACHE = new ConcurrentHashMap<>();

    private final List<Node> nodes;

    private PromptTemplate(List<Node> nodes) {
        this.nodes = nodes;
    }

    /** Compile a template from source text. */
    public static PromptTemplate compile(String source) {
        if (source == null) throw new PromptTemplateException("template source is null");
        return new PromptTemplate(buildTree(standaloneTrim(tokenize(source))));
    }

    /** Compile (once, cached) a template loaded from a classpath asset. */
    public static PromptTemplate load(String classpathRef) {
        return CACHE.computeIfAbsent(classpathRef, ref -> compile(PromptAsset.load(ref)));
    }

    public String render(PromptModel model) {
        Map<String, Object> root = model == null ? Map.of() : model.values();
        StringBuilder out = new StringBuilder();
        render(nodes, new Scope(root, null), out);
        return out.toString();
    }

    // --- rendering --------------------------------------------------------------

    private static void render(List<Node> nodes, Scope scope, StringBuilder out) {
        for (Node n : nodes) {
            if (n instanceof Text t) {
                out.append(t.value);
            } else if (n instanceof Var v) {
                out.append(stringify(scope.lookup(v.name)));
            } else if (n instanceof Section s) {
                renderSection(s, scope, out);
            }
        }
    }

    private static void renderSection(Section s, Scope scope, StringBuilder out) {
        Object value = scope.lookup(s.name);
        if (s.inverted) {
            if (isFalsy(value)) render(s.children, scope, out);
            return;
        }
        if (isFalsy(value)) return;
        if (value instanceof List<?> list) {
            for (Object element : list) {
                render(s.children, new Scope(element, scope), out);
            }
        } else if (asMap(value) != null) {
            render(s.children, new Scope(value, scope), out);
        } else {
            // truthy boolean / number / non-empty string: render once, scope unchanged
            render(s.children, scope, out);
        }
    }

    private static boolean isFalsy(Object v) {
        if (v == null || Boolean.FALSE.equals(v)) return true;
        if (v instanceof String s) return s.isEmpty();
        if (v instanceof List<?> l) return l.isEmpty();
        Map<String, Object> m = asMap(v);
        if (m != null) return m.isEmpty();
        return false; // numbers and TRUE are truthy
    }

    private static String stringify(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object v) {
        if (v instanceof PromptModel pm) return pm.values();
        if (v instanceof Map<?, ?> m) return (Map<String, Object>) m;
        return null;
    }

    /** A variable-resolution scope; element scopes fall back to their parent (Mustache-style). */
    private static final class Scope {
        private final Object data;
        private final Scope parent;

        Scope(Object data, Scope parent) {
            this.data = data;
            this.parent = parent;
        }

        Object lookup(String name) {
            if (".".equals(name)) return data;
            Map<String, Object> map = asMap(data);
            if (map != null && map.containsKey(name)) return map.get(name);
            return parent == null ? null : parent.lookup(name);
        }
    }

    // --- node tree --------------------------------------------------------------

    private sealed interface Node permits Text, Var, Section {}

    private record Text(String value) implements Node {}

    private record Var(String name) implements Node {}

    private record Section(String name, boolean inverted, List<Node> children) implements Node {}

    private static List<Node> buildTree(List<Object> tokens) {
        List<Node> root = new ArrayList<>();
        Deque<Frame> stack = new ArrayDeque<>();
        List<Node> current = root;

        for (Object tok : tokens) {
            if (tok instanceof TextTok t) {
                if (!t.text.isEmpty()) current.add(new Text(t.text));
                continue;
            }
            TagTok tag = (TagTok) tok;
            switch (tag.sigil) {
                case 'v', '&' -> current.add(new Var(tag.name));
                case '!' -> { /* comment: drop */ }
                case '#', '^' -> {
                    Frame frame = new Frame(tag.name, tag.sigil == '^', current);
                    stack.push(frame);
                    current = frame.children;
                }
                case '/' -> {
                    if (stack.isEmpty()) {
                        throw new PromptTemplateException("unexpected {{/" + tag.name + "}} with no open section");
                    }
                    Frame frame = stack.pop();
                    if (!frame.name.equals(tag.name)) {
                        throw new PromptTemplateException(
                                "mismatched section: opened {{#" + frame.name + "}} but closed {{/" + tag.name + "}}");
                    }
                    frame.parent.add(new Section(frame.name, frame.inverted, frame.children));
                    current = frame.parent;
                }
                default -> throw new PromptTemplateException("unsupported tag sigil: " + tag.sigil);
            }
        }
        if (!stack.isEmpty()) {
            throw new PromptTemplateException("unclosed section {{#" + stack.peek().name + "}}");
        }
        return root;
    }

    private static final class Frame {
        final String name;
        final boolean inverted;
        final List<Node> parent;
        final List<Node> children = new ArrayList<>();

        Frame(String name, boolean inverted, List<Node> parent) {
            this.name = name;
            this.inverted = inverted;
            this.parent = parent;
        }
    }

    // --- tokenizer --------------------------------------------------------------

    private static final class TextTok {
        String text;
        TextTok(String text) { this.text = text; }
    }

    private record TagTok(char sigil, String name) {}

    private static List<Object> tokenize(String src) {
        List<Object> toks = new ArrayList<>();
        int pos = 0;
        int len = src.length();
        while (pos < len) {
            int open = src.indexOf("{{", pos);
            if (open < 0) {
                toks.add(new TextTok(src.substring(pos)));
                break;
            }
            if (open > pos) toks.add(new TextTok(src.substring(pos, open)));

            boolean triple = src.startsWith("{{{", open);
            String closer = triple ? "}}}" : "}}";
            int contentStart = open + (triple ? 3 : 2);
            int close = src.indexOf(closer, contentStart);
            if (close < 0) {
                throw new PromptTemplateException("unclosed tag starting at index " + open);
            }
            String raw = src.substring(contentStart, close).trim();
            pos = close + closer.length();

            if (triple) {
                toks.add(new TagTok('&', raw));
                continue;
            }
            if (raw.isEmpty()) continue; // {{}} → nothing
            char first = raw.charAt(0);
            switch (first) {
                case '#', '^', '/', '!', '&' -> toks.add(new TagTok(first, raw.substring(1).trim()));
                case '>' -> throw new PromptTemplateException("partials ({{>...}}) are not supported");
                default -> toks.add(new TagTok('v', raw));
            }
        }
        return toks;
    }

    /**
     * Mustache standalone-line handling: when a section/comment tag is alone on its
     * line (only whitespace around it), remove that whitespace and the line's newline
     * so block tags don't leave blank lines in the output. Variable tags are never
     * standalone.
     */
    private static List<Object> standaloneTrim(List<Object> toks) {
        for (int i = 0; i < toks.size(); i++) {
            if (!(toks.get(i) instanceof TagTok tag)) continue;
            if (tag.sigil == 'v' || tag.sigil == '&') continue; // variables stay inline

            TextTok prev = (i > 0 && toks.get(i - 1) instanceof TextTok p) ? p : null;
            TextTok next = (i + 1 < toks.size() && toks.get(i + 1) instanceof TextTok n) ? n : null;
            boolean prevOk = (i == 0) || (prev != null && blankToLineStart(prev.text));
            boolean nextOk = (i == toks.size() - 1) || (next != null && blankToLineEnd(next.text));
            if (!prevOk || !nextOk) continue;

            if (prev != null) prev.text = trimTrailingIndent(prev.text);
            if (next != null) next.text = trimLeadingLine(next.text);
        }
        return toks;
    }

    /** True if everything after the last newline of {@code s} is whitespace. */
    private static boolean blankToLineStart(String s) {
        int nl = s.lastIndexOf('\n');
        for (int i = nl + 1; i < s.length(); i++) {
            if (s.charAt(i) != ' ' && s.charAt(i) != '\t') return false;
        }
        return true;
    }

    /** True if {@code s} begins with optional spaces/tabs then a newline (or is all blank). */
    private static boolean blankToLineEnd(String s) {
        int i = 0;
        while (i < s.length() && (s.charAt(i) == ' ' || s.charAt(i) == '\t')) i++;
        return i == s.length() || s.charAt(i) == '\n' || s.charAt(i) == '\r';
    }

    private static String trimTrailingIndent(String s) {
        int nl = s.lastIndexOf('\n');
        return nl < 0 ? "" : s.substring(0, nl + 1);
    }

    private static String trimLeadingLine(String s) {
        int i = 0;
        while (i < s.length() && (s.charAt(i) == ' ' || s.charAt(i) == '\t')) i++;
        if (i < s.length() && s.charAt(i) == '\r') i++;
        if (i < s.length() && s.charAt(i) == '\n') i++;
        else if (i == s.length()) return ""; // trailing blank line at end of template
        return s.substring(i);
    }
}
