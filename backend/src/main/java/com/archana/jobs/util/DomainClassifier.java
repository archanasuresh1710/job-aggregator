package com.archana.jobs.util;

import java.util.List;

public class DomainClassifier {

    private static final List<String> FINTECH_COMPANIES = List.of(
            "paypal", "paytm", "phonepe", "razorpay", "stripe", "visa", "mastercard",
            "american express", "amex", "goldman sachs", "morgan stanley", "jpmorgan",
            "jp morgan", "hdfc", "idfc", "npci", "flywire", "coinbase", "ripple",
            "barclays", "uber", "grab", "navan", "wise", "revolut", "monzo",
            "zerodha", "groww", "cred", "slice", "fi money", "open financial",
            "m2p", "cashfree", "billdesk", "pine labs", "mswipe", "instamojo",
            "lendingkart", "capital float", "kissht", "axio", "fibe", "navi",
            "jupiter", "niyo", "hyperface", "decentro", "setu", "yap", "open bank"
    );

    private static final List<String> FINTECH_KEYWORDS = List.of(
            "fintech", "payment", "payments", "banking", "finance", "financial",
            "lending", "credit", "insurance", "trading", "investment", "forex",
            "crypto", "blockchain", "treasury", "wallet", "remittance", "neobank",
            "wealth", "tax", "accounting", "bfsi", "nbfc", "upi", "swift",
            "card", "acquiring", "issuing", "settlement", "clearing", "kyc", "aml"
    );

    public static String classify(String company, String title, String description) {
        String combined = (
            (company     != null ? company     : "") + " " +
            (title       != null ? title       : "") + " " +
            (description != null ? description : "")
        ).toLowerCase();

        for (String c : FINTECH_COMPANIES) {
            if (combined.contains(c)) return "fintech";
        }
        for (String k : FINTECH_KEYWORDS) {
            if (combined.contains(k)) return "fintech";
        }
        return "other";
    }
}
