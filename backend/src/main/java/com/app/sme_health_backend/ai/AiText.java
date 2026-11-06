package com.app.sme_health_backend.ai;

import java.util.Map;

/** Owned explanation wording; providers never supply unchecked financial prose. */
final class AiText {
    private AiText() { }
    static String choose(String language, String en, String ur) { return "ur".equals(language) ? ur : en; }
    static String summary(String topic, String language) {
        return switch (topic) {
            case "unsupported" -> choose(language, "I can explain this business's recorded monthly finances, score and existing advice. This request is outside that read-only scope.", "میں اس کاروبار کے ماہانہ مالی ریکارڈ، اسکور اور موجودہ مشوروں کی وضاحت کر سکتا ہوں۔ یہ درخواست اس محدود دائرے سے باہر ہے۔");
            case "projection" -> choose(language, "This explanation uses the existing backend cash-flow projection. It is a forecast, not recorded cash.", "یہ وضاحت موجودہ بیک اینڈ کی نقد بہاؤ پیش گوئی پر مبنی ہے۔ یہ تخمینہ ہے، ریکارڈ شدہ نقد رقم نہیں۔");
            case "recommendation" -> choose(language, "These considerations explain existing FinSight recommendations. Their priority and completion status remain unchanged.", "یہ نکات موجودہ فن سائٹ تجاویز کی وضاحت کرتے ہیں۔ ان کی ترجیح اور تکمیل کی حالت تبدیل نہیں ہوئی۔");
            case "cashflow" -> choose(language, "The recorded cash history and score evidence below describe the selected financial snapshot.", "ذیل میں نقد تاریخ اور اسکور کے شواہد منتخب مالی ریکارڈ کی وضاحت کرتے ہیں۔");
            case "records" -> choose(language, "These are recorded monthly amounts. Missing values are unknown, not zero.", "یہ درج شدہ ماہانہ رقوم ہیں۔ غیر موجود اعداد نامعلوم ہیں، صفر نہیں۔");
            case "cash_position" -> choose(language, "This explanation reflects the current authoritative cash and bank position from posted account movements.", "یہ وضاحت درج شدہ اکاؤنٹ لین دین سے تصدیق شدہ موجودہ نقد اور بینک پوزیشن پر مبنی ہے۔");
            case "overdue_receivables" -> choose(language, "This explanation reflects overdue customer receivables based on canonical obligation aging.", "یہ وضاحت واجب الوصول گاہکوں کے بقایا جات کے مصدقہ ریکارڈ پر مبنی ہے۔");
            case "upcoming_obligations" -> choose(language, "This explanation reflects scheduled obligations and projected recurring commitments over the selected period.", "یہ وضاحت منتخب مدت کے دوران طے شدہ واجبات اور متوقع بار بار ہونے والے اخراجات پر مبنی ہے۔");
            default -> choose(language, "This explanation is grounded in the recorded financial score and its supporting evidence.", "یہ وضاحت ریکارڈ شدہ مالی اسکور اور اس کے معاون شواہد پر مبنی ہے۔");
        };
    }
    static String limitation(String code, String language) {
        return switch (code) {
            case "snapshot" -> choose(language, "Monthly snapshots are not a complete transaction ledger.", "ماہانہ خلاصے مکمل لین دین کا کھاتہ نہیں ہیں۔");
            case "no_causality" -> choose(language, "Recorded associations do not establish why a business event happened. Review the underlying records before acting.", "ریکارڈ کے تعلقات کسی کاروباری واقعے کی وجہ ثابت نہیں کرتے۔ اقدام سے پہلے اصل ریکارڈ کا جائزہ لیں۔");
            case "missing_data" -> choose(language, "Some evidence is missing or the history is sparse; no missing amount has been treated as zero.", "کچھ شواہد موجود نہیں یا تاریخ محدود ہے؛ کسی نامعلوم رقم کو صفر نہیں سمجھا گیا۔");
            case "projection" -> choose(language, "The backend projection depends on limited historical data and is not a promise of future cash.", "بیک اینڈ کا تخمینہ محدود تاریخی معلومات پر مبنی ہے اور مستقبل کی نقد رقم کی ضمانت نہیں۔");
            case "currency_unspecified" -> choose(language, "Amounts retain the existing records' monetary basis; this MVP evidence does not supply a currency code or FX conversion.", "رقوم موجودہ ریکارڈ کی مالی بنیاد پر ہیں؛ یہ شواہد کرنسی کوڈ یا زر مبادلہ کی تبدیلی فراہم نہیں کرتے۔");
            case "unsupported_modules" -> choose(language, "Detailed inventory, invoices, customer receivables and project finance are not available to this assistant.", "اس معاون کو تفصیلی اسٹاک، انوائس، گاہکوں کے واجبات اور منصوبوں کی مالی معلومات دستیاب نہیں۔");
            case "simulated" -> choose(language, "Simulated development/demo explanation. No live GPT request was made.", "ترقیاتی یا نمائشی وضاحت۔ حقیقی جی پی ٹی درخواست نہیں کی گئی۔");
            case "provider_unavailable" -> choose(language, "AI is unavailable. This is a deterministic FinSight explanation, not a GPT response.", "اے آئی دستیاب نہیں۔ یہ فن سائٹ کی مقررہ قواعد والی وضاحت ہے، جی پی ٹی کا جواب نہیں۔");
            case "stale_source" -> choose(language, "This advice does not match the current score snapshot. Refresh the source page before requesting an explanation.", "یہ مشورہ موجودہ اسکور سے مطابقت نہیں رکھتا۔ وضاحت کی درخواست سے پہلے اصل صفحہ تازہ کریں۔");
            default -> choose(language, "There is insufficient verified evidence for this request.", "اس درخواست کے لیے تصدیق شدہ شواہد ناکافی ہیں۔");
        };
    }
    private static final Map<String, String[]> LABELS = Map.ofEntries(
        Map.entry("score", new String[]{"Financial health score", "مالی صحت کا اسکور"}),
        Map.entry("band", new String[]{"Score band", "اسکور کا درجہ"}),
        Map.entry("weakest", new String[]{"Weakest component", "کمزور ترین جزو"}),
        Map.entry("completeness", new String[]{"Data completeness (fraction)", "معلومات کی تکمیل (تناسب)"}),
        Map.entry("cashflow", new String[]{"Cash-flow component", "نقد بہاؤ کا جزو"}),
        Map.entry("profitability", new String[]{"Profitability component", "منافع کا جزو"}),
        Map.entry("repayment", new String[]{"Repayment component", "ادائیگی کا جزو"}),
        Map.entry("trend", new String[]{"Trend component", "رجحان کا جزو"}),
        Map.entry("compliance", new String[]{"Compliance component", "تعمیل کا جزو"}),
        Map.entry("delta", new String[]{"Recorded score change", "ریکارڈ شدہ اسکور کی تبدیلی"}),
        Map.entry("cashBufferMonths", new String[]{"Recorded cash buffer", "ریکارڈ شدہ نقد ذخیرہ"}),
        Map.entry("netMargin", new String[]{"Recorded net margin (fraction)", "ریکارڈ شدہ خالص منافع (تناسب)"}),
        Map.entry("dso", new String[]{"Recorded receivables-days snapshot", "ریکارڈ شدہ وصولیوں کے دن"}),
        Map.entry("revenue", new String[]{"Revenue", "آمدنی"}),
        Map.entry("expenses", new String[]{"Operating expenses", "عملی اخراجات"}),
        Map.entry("cogs", new String[]{"Cost of goods sold", "فروخت شدہ مال کی لاگت"}),
        Map.entry("inflow", new String[]{"Cash inflow", "نقد آمد"}),
        Map.entry("outflow", new String[]{"Cash outflow", "نقد اخراج"}),
        Map.entry("net", new String[]{"Net cash flow", "خالص نقد بہاؤ"}),
        Map.entry("balance", new String[]{"Recorded month-end cash balance", "ریکارڈ شدہ ماہ کے آخر کی نقد رقم"}),
        Map.entry("projection", new String[]{"Projected net cash flow", "متوقع خالص نقد بہاؤ"}),
        Map.entry("direction", new String[]{"Backend trend direction", "بیک اینڈ رجحان کی سمت"}),
        Map.entry("confidence", new String[]{"Backend confidence", "بیک اینڈ اعتماد"}),
        Map.entry("insight", new String[]{"Existing FinSight insight", "موجودہ فن سائٹ بصیرت"}),
        Map.entry("recommendation", new String[]{"Existing FinSight recommendation", "موجودہ فن سائٹ تجویز"}),
        Map.entry("priority", new String[]{"Existing priority", "موجودہ ترجیح"}),
        Map.entry("status", new String[]{"Existing action status", "موجودہ کارروائی کی حالت"}),
        Map.entry("current_cash_balance", new String[]{"Current cash balance", "موجودہ نقد رقم"}),
        Map.entry("opening_balance", new String[]{"Opening balance", "ابتدائی رقم"}),
        Map.entry("net_movements", new String[]{"Net movements", "خالص لین دین"}),
        Map.entry("total_accounts", new String[]{"Total financial accounts", "کل مالی اکاؤنٹس"}),
        Map.entry("total_overdue", new String[]{"Total overdue amount", "کل واجب الوصول رقم"}),
        Map.entry("overdue_count", new String[]{"Overdue count", "واجب الوصول تعداد"}),
        Map.entry("upcoming_receivables", new String[]{"Upcoming receivables", "آنے والی متوقع وصولیاں"}),
        Map.entry("upcoming_payables", new String[]{"Upcoming payables", "آنے والی واجب الادا ادائیگیاں"}),
        Map.entry("recurring_projection", new String[]{"Projected recurring commitment", "متوقع بار بار ہونے والی ادائیگی"}),
        Map.entry("aging_bucket", new String[]{"Aging bucket", "میعاد کا درجہ"}),
        Map.entry("CURRENT", new String[]{"Current (not overdue)", "موجودہ (بروقت)"}),
        Map.entry("DAYS_1_TO_30", new String[]{"1-30 days overdue", "1 تا 30 دن کی تاخیر"}),
        Map.entry("DAYS_31_TO_60", new String[]{"31-60 days overdue", "31 تا 60 دن کی تاخیر"}),
        Map.entry("DAYS_61_TO_90", new String[]{"61-90 days overdue", "61 تا 90 دن کی تاخیر"}),
        Map.entry("DAYS_OVER_90", new String[]{"Over 90 days overdue", "90 دن سے زائد کی تاخیر"}),
        Map.entry("NOT_YET_DUE", new String[]{"Not yet due", "ابھی واجب الادا نہیں"}),
        Map.entry("DUE_TODAY", new String[]{"Due today", "آج واجب الادا"}),
        Map.entry("OVERDUE_1_30", new String[]{"1-30 days overdue", "1 تا 30 دن کی تاخیر"}),
        Map.entry("OVERDUE_31_60", new String[]{"31-60 days overdue", "31 تا 60 دن کی تاخیر"}),
        Map.entry("OVERDUE_61_90", new String[]{"61-90 days overdue", "61 تا 90 دن کی تاخیر"}),
        Map.entry("OVERDUE_91_PLUS", new String[]{"Over 90 days overdue", "90 دن سے زائد کی تاخیر"}),
        Map.entry("UNKNOWN_DUE_DATE", new String[]{"Unknown due date", "نامعلوم تاریخ ادائیگی"})
    );
    static String label(String key, String language) {
        String[] value = LABELS.get(key);
        return value == null ? key : value["ur".equals(language) ? 1 : 0];
    }
}
