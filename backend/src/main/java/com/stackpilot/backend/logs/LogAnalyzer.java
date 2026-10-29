package com.stackpilot.backend.logs;

import com.stackpilot.backend.logs.model.LogModels;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a raw container log tail into failure signatures grouped by category.
 *
 * <p>This is the log-analysis stage of the troubleshooting loop: it does not try to
 * explain a failure, it only extracts and ranks the signals so rules (and the UI)
 * can reason about them.
 */
@Component
public class LogAnalyzer {

    private static final int MAX_LINES = 4000;
    private static final int MAX_SIGNATURES = 30;
    private static final int SAMPLE_LIMIT = 400;

    /** Docker prepends an RFC3339 timestamp when timestamps are requested. */
    private static final Pattern DOCKER_TS = Pattern.compile(
            "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?(?:Z|[+-]\\d{2}:?\\d{2})\\s+"
    );

    private static final Pattern LEVEL = Pattern.compile(
            "\\b(TRACE|DEBUG|INFO|WARN|WARNING|ERROR|FATAL|SEVERE)\\b"
    );

    private static final Pattern STACK_FRAME = Pattern.compile("^\\s*at\\s+[\\w$.]+\\(.*\\)?");
    private static final Pattern CAUSED_BY = Pattern.compile("^\\s*Caused by:\\s+(.+)$");
    private static final Pattern EXCEPTION_TYPE = Pattern.compile(
            "\\b([A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)*[A-Za-z_$][\\w$]*(?:Exception|Error|Throwable))\\b"
    );

    /** Category → patterns that identify it. Order matters: first hit wins. */
    private record Category(String name, String severity, List<Pattern> patterns) {
    }

    private static List<Category> categories() {
        return List.of(
                category("OOM", "ERROR",
                        "outofmemoryerror|oomkilled|java heap space|gc overhead limit|killed process|signal 9|exit code 137|memory limit"),
                category("PORT", "ERROR",
                        "port \\d+ was already in use|address already in use|port is already allocated|bind: address already in use|eaddrinuse|address not available"),
                category("AUTH", "ERROR",
                        "password authentication failed|access denied|authentication failed|invalid credentials|unauthorized|noauth|wrongpass|invalid username|bad credentials"),
                category("DNS", "ERROR",
                        "unknownhostexception|no such host|name or service not known|temporary failure in name resolution|getaddrinfo|servfail|nxdomain|name resolution"),
                category("CONNECTION", "ERROR",
                        "connection refused|econnrefused|unable to connect|error connecting|could not connect|connect exception|connection reset|broken pipe|no route to host|connection to node"),
                category("TIMEOUT", "ERROR",
                        "timeout|timed out|deadline exceeded|read timed out|socket timeout"),
                category("MIGRATION", "ERROR",
                        "flyway|liquibase|migration failed|failed to migrate|schema .* does not exist"),
                category("CONFIG", "ERROR",
                        "could not resolve placeholder|nosuchbeandefinition|missingproperty|configurationproperties|failed to bind properties|unrecognized field|invalid configuration|beancreationexception"),
                category("CRASH", "ERROR",
                        "application failed to start|fatal error|panic:|segmentation fault|core dumped|application context.*failed"),
                category("DEPENDENCY", "WARN",
                        "deprecated|retry|circuit breaker|fallback|degraded|unhealthy|health check failed")
        );
    }

    private static Category category(String name, String severity, String regex) {
        return new Category(name, severity, List.of(Pattern.compile(regex)));
    }

    private static final List<Category> CATEGORIES = categories();

    /** Normalisation noise that should not split otherwise identical signatures. */
    private static final Pattern HEX = Pattern.compile("\\b[0-9a-f]{8,}\\b");
    private static final Pattern UUID = Pattern.compile(
            "\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b");
    private static final Pattern NUMBERS = Pattern.compile("\\b\\d+\\b");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    public LogModels.Summary analyze(String raw) {
        if (raw == null || raw.isBlank()) {
            return LogModels.Summary.empty();
        }

        String[] allLines = raw.split("\\R", -1);
        int limit = Math.min(allLines.length, MAX_LINES);

        int errorLines = 0;
        int warnLines = 0;
        int infoLines = 0;
        int stackLines = 0;

        Map<String, SignatureBuilder> groups = new LinkedHashMap<>();
        Set<String> exceptionTypes = new LinkedHashSet<>();

        for (int i = 0; i < limit; i++) {
            String line = stripTimestamp(allLines[i]);
            if (line.isBlank()) {
                continue;
            }

            boolean stack = STACK_FRAME.matcher(line).find();
            boolean caused = CAUSED_BY.matcher(line).find();
            String level = detectLevel(line);

            if (stack) {
                stackLines++;
            }
            if ("ERROR".equals(level) || "FATAL".equals(level)) {
                errorLines++;
            } else if ("WARN".equals(level)) {
                warnLines++;
            } else if ("INFO".equals(level)) {
                infoLines++;
            }

            Matcher typeMatcher = EXCEPTION_TYPE.matcher(line);
            while (typeMatcher.find()) {
                exceptionTypes.add(typeMatcher.group(1));
            }

            String category = categorize(line, stack || caused);
            boolean interesting = stack || caused
                    || "ERROR".equals(level) || "FATAL".equals(level)
                    || "WARN".equals(level)
                    || !"OTHER".equals(category);
            if (!interesting) {
                continue;
            }
            if ("OTHER".equals(category) && stack) {
                // Bare stack frames add noise without identifying a cause.
                continue;
            }

            String signature = normalize(line);
            if (signature.isBlank()) {
                continue;
            }
            String key = category + "|" + signature;
            SignatureBuilder builder = groups.get(key);
            if (builder == null) {
                builder = new SignatureBuilder(signature, category, severityFor(line, level, category), i + 1, line);
                groups.put(key, builder);
            }
            builder.count++;
            if (builder.severity.equals("WARN") && severityFor(line, level, category).equals("ERROR")) {
                builder.severity = "ERROR";
            }
        }

        List<LogModels.Signature> signatures = new ArrayList<>();
        for (SignatureBuilder builder : groups.values()) {
            signatures.add(builder.build());
        }
        signatures.sort(Comparator
                .comparing((LogModels.Signature s) -> "ERROR".equals(s.severity()) ? 0 : 1)
                .thenComparing(Comparator.comparingInt(LogModels.Signature::count).reversed())
                .thenComparing(LogModels.Signature::line));

        if (signatures.size() > MAX_SIGNATURES) {
            signatures = signatures.subList(0, MAX_SIGNATURES);
        }

        return new LogModels.Summary(
                limit,
                errorLines,
                warnLines,
                infoLines,
                stackLines,
                List.copyOf(signatures),
                List.copyOf(exceptionTypes)
        );
    }

    /** Strips the Docker timestamp prefix so level detection sees the real message. */
    public String stripTimestamp(String line) {
        if (line == null) {
            return "";
        }
        String withoutControl = line.replace("\u0000", "");
        return DOCKER_TS.matcher(withoutControl).replaceFirst("").strip();
    }

    public String categorize(String line) {
        return categorize(line, false);
    }

    private String categorize(String line, boolean stackFrame) {
        if (line == null) {
            return "OTHER";
        }
        String lower = line.toLowerCase(Locale.ROOT);
        for (Category category : CATEGORIES) {
            for (Pattern pattern : category.patterns()) {
                if (pattern.matcher(lower).find()) {
                    return category.name();
                }
            }
        }
        return stackFrame ? "OTHER" : "OTHER";
    }

    private static String severityFor(String line, String level, String category) {
        if ("ERROR".equals(level) || "FATAL".equals(level)) {
            return "ERROR";
        }
        for (Category c : CATEGORIES) {
            if (c.name().equals(category)) {
                return c.severity();
            }
        }
        return "WARN";
    }

    private static String detectLevel(String line) {
        Matcher matcher = LEVEL.matcher(line);
        while (matcher.find()) {
            String token = matcher.group(1).toUpperCase(Locale.ROOT);
            if ("WARNING".equals(token)) {
                return "WARN";
            }
            if ("SEVERE".equals(token)) {
                return "ERROR";
            }
            return token;
        }
        return "";
    }

    private static String normalize(String line) {
        String value = line.strip();
        value = UUID.matcher(value).replaceAll("<id>");
        value = HEX.matcher(value).replaceAll("<hex>");
        value = NUMBERS.matcher(value).replaceAll("<n>");
        value = WHITESPACE.matcher(value).replaceAll(" ");
        value = value.strip();
        if (value.length() > 180) {
            value = value.substring(0, 180);
        }
        return value;
    }

    private static String sample(String line) {
        String value = line.strip();
        return value.length() > SAMPLE_LIMIT ? value.substring(0, SAMPLE_LIMIT) : value;
    }

    private static final class SignatureBuilder {
        private final String signature;
        private final String category;
        private String severity;
        private final int line;
        private final String sample;
        private int count;

        private SignatureBuilder(String signature, String category, String severity, int line, String rawLine) {
            this.signature = signature;
            this.category = category;
            this.severity = severity;
            this.line = line;
            this.sample = sample(rawLine);
        }

        private LogModels.Signature build() {
            return new LogModels.Signature(signature, category, severity, count, sample, line);
        }
    }
}
