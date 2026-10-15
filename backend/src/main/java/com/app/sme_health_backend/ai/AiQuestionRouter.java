package com.app.sme_health_backend.ai;

import org.springframework.stereotype.Component;
import java.util.Locale;
import java.util.LinkedHashSet;
import java.time.YearMonth;
import java.util.regex.Pattern;

/** Conservative MVP topic routing. No tools, IDs, URLs or instructions are model-authorized. */
@Component
public class AiQuestionRouter {
    private static final Pattern EXPLICIT_MONTH = Pattern.compile("(?<![\\p{L}\\d])([0-9]{4}-[0-9]{2})(?![\\p{L}\\d])");
    private static final Pattern BLOCKED = Pattern.compile(
            "(?iu)(https?://|www\\.|\\b(sql|select|drop table|api key|system prompt|ignore|override|pretend|guarantee|transfer|delete|create invoice|post transaction|other business|another business|other tenant|all businesses|inventory|invoice|receivable|customer balance|project financial|payroll|competitor)\\b|پاس ورڈ|دوسرے کاروبار|رقم منتقل|انوائس|اسٹاک|خفیہ|ہدایات نظر انداز)"
    );
    /** A specific requested month must never silently turn into a latest-data answer. */
    public AiTypes.Request normalize(AiTypes.Request request) {
        if (!"ask".equals(request.type()) || request.question() == null) return request;
        String question = request.question();
        if (isFollowUp(question.toLowerCase(Locale.ROOT))) {
            var history = request.conversation();
            if (history != null) for (int i = history.size() - 1; i >= 0; i--) {
                if ("user".equals(history.get(i).role())) { question = history.get(i).content(); break; }
            }
        }
        var matcher = EXPLICIT_MONTH.matcher(question);
        var months = new LinkedHashSet<String>();
        while (matcher.find()) {
            String month = matcher.group(1);
            try { YearMonth.parse(month); }
            catch (RuntimeException invalid) { throw new IllegalArgumentException("Use a valid YYYY-MM month"); }
            months.add(month);
        }
        if (months.size() > 1) throw new IllegalArgumentException("Ask about one financial month at a time");
        if (months.isEmpty()) return request;
        String month = months.iterator().next();
        if (request.month() != null && !request.month().equals(month))
            throw new IllegalArgumentException("The question month must match the selected month");
        return new AiTypes.Request(request.type(), request.language(), request.question(), request.sourceId(), month, request.conversation());
    }
    public String topic(AiTypes.Request request) {
        if (!"ask".equals(request.type())) return request.type();
        String question = request.question();
        if (question == null || question.isBlank()) throw new IllegalArgumentException("A financial question is required");
        if (BLOCKED.matcher(question).find() || Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F-]{27,}").matcher(question).find())
            return "unsupported";
        String value = question.toLowerCase(Locale.ROOT);
        // History is not financial evidence. Only the immediately prior USER topic may resolve a short follow-up.
        if (isFollowUp(value)) {
            var history = request.conversation();
            if (history != null) for (int i = history.size() - 1; i >= 0; i--) {
                var turn = history.get(i);
                if ("user".equals(turn.role())) return topic(new AiTypes.Request("ask", request.language(),
                        turn.content(), null, request.month(), null));
            }
        }
        if (contains(value, "forecast", "projection", "projected", "پیش گوئی", "تخمینہ")) return "projection";
        if (contains(value, "recommend", "action", "advice", "مشور", "تجویز")) return "recommendation";
        if (contains(value, "insight", "بصیرت")) return "insight";
        // Explicit score intent takes precedence over supporting words such as "recorded amounts".
        if (contains(value, "score", "health", "component", "اسکور", "صحت")) return "score";
        if (contains(value, "cash", "buffer", "liquidity", "نقد", "کیش")) return "cashflow";
        if (contains(value, "revenue", "expense", "monthly", "record", "sales", "آمدن", "اخراجات", "ماہانہ", "ریکارڈ")) return "records";
        if (contains(value, "trend", "improv", "weaken", "deterior", "declin", "missing", "data", "رجحان", "کمی", "بہتر", "اعداد")) return "score";
        return "unsupported";
    }
    private static boolean contains(String value, String... words) {
        for (String word : words) if (value.contains(word)) return true;
        return false;
    }
    private static boolean isFollowUp(String value) {
        return value.strip().matches("(?iu)(why\\??|explain( more)?\\??|what next\\??|کیوں\\??|مزید بتائیں\\??)");
    }
}
