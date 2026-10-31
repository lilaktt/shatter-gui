package net.shattergui.fx;

import java.util.ArrayList;
import java.util.List;

/**
 * Errors and warnings found while reading an effect, each with the path of the field it is about
 * ({@code layers[2].params.strength.keys[1]}). Errors make the effect unusable; warnings only skip or clamp something.
 */
public final class FxProblems {
    public record Problem(boolean error, String path, String message) {
        @Override
        public String toString() {
            return (path.isEmpty() ? "" : path + ": ") + message;
        }
    }

    private static final int MAX_KEPT = 64;
    private final List<Problem> problems = new ArrayList<>();

    public void error(String path, String message) {
        add(new Problem(true, path, message));
    }

    public void warn(String path, String message) {
        add(new Problem(false, path, message));
    }

    private void add(Problem problem) {
        if (problems.size() < MAX_KEPT) {
            problems.add(problem);
        }
    }

    public boolean hasErrors() {
        return problems.stream().anyMatch(Problem::error);
    }

    public List<Problem> all() {
        return List.copyOf(problems);
    }

    public List<Problem> errors() {
        return problems.stream().filter(Problem::error).toList();
    }

    public List<Problem> warnings() {
        return problems.stream().filter(p -> !p.error()).toList();
    }

    /** The first errors, joined, for a one-line log message. */
    public String errorSummary() {
        List<Problem> errors = errors();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(3, errors.size()); i++) {
            if (i > 0) {
                sb.append("; ");
            }
            sb.append(errors.get(i));
        }
        if (errors.size() > 3) {
            sb.append("; … (").append(errors.size() - 3).append(" more)");
        }
        return sb.toString();
    }
}
