package com.lms.www.leadmanagement.util;

import com.lms.www.leadmanagement.dto.OcrResponseDTO;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@lombok.extern.slf4j.Slf4j
public class PaymentOcrParser {

    /**
     * Parses raw OCR text into structured payment data using Regex.
     */
    public static OcrResponseDTO parse(String text) {
        OcrResponseDTO dto = new OcrResponseDTO();
        dto.setSuccess(true);
        dto.setRawText(text);

        // 1. Detect Payment App
        String app = detectApp(text);
        dto.setPaymentApp(app);

        // 2. Extract UTR / Transaction ID
        String utr12 = findMatch(text, "(?i)(?:UTR|Ref No|UPI Ref No|UPI Transaction ID)[:\\s]*(\\d{12})", 1);
        String txnId = findMatch(text, "(?i)(?:Transaction ID|Txn ID|Ref ID)[:\\s]*([A-Z\\d]{10,25})", 1);
        
        // If no labels found, try standalone 12-digit number (common in UPI)
        if (utr12 == null && txnId == null) {
            utr12 = findMatch(text, "(?:^|\\s)(\\d{12})(?:$|\\s)", 1);
        }
        
        dto.setUtrNumber(utr12 != null ? utr12 : txnId);

        // 3. Extract Amount using scoring rank algorithm
        String amount = extractAmount(text);
        dto.setAmount(amount);

        // 4. Extract Payer Name
        String payerName = extractPayerName(text);
        dto.setPayerName(payerName);

        // 5. App-specific overrides
        if ("PhonePe".equals(app)) {
            parsePhonePe(text, dto);
        } else if ("Paytm".equals(app)) {
            parsePaytm(text, dto);
        } else if ("GPay".equals(app)) {
            parseGPay(text, dto);
        }

        // 6. Extract Date and Time as fallbacks
        String dateStr = findMatch(text, "(?i)(?:on\\s)?(\\d{1,2}(?:st|nd|rd|th)?\\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[\\w]*[\\s,]*\\d{4}|\\d{2}/\\d{2}/\\d{4}|\\d{2}-\\d{2}-\\d{4}|(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[\\w]*\\s+\\d{1,2}[\\s,]*\\d{4})", 1);
        dto.setPaymentDate(dateStr);

        dto.setPaymentTime(findMatch(text, "(\\d{1,2}:\\d{2}(?::\\d{2})?\\s?(?:AM|PM|am|pm))", 1));
        if (dto.getPaymentTime() == null) {
            dto.setPaymentTime(findMatch(text, "(\\d{1,2}:\\d{2}(?::\\d{2})?)", 1));
        }

        // 7. Check if crucial fields are missing or doubtful, and flag warning
        if (dto.getUtrNumber() == null || dto.getAmount() == null || dto.getPayerName() == null) {
            StringBuilder warning = new StringBuilder("Doubtful fields: ");
            if (dto.getUtrNumber() == null) warning.append("UTR/Transaction ID missing. ");
            if (dto.getAmount() == null) warning.append("Amount missing. ");
            if (dto.getPayerName() == null) warning.append("Payer Name missing. ");
            dto.setErrorMessage(warning.toString().trim());
        }

        return dto;
    }

    private static String detectApp(String text) {
        String lowerText = text.toLowerCase();
        if (lowerText.contains("phonepe")) return "PhonePe";
        if (lowerText.contains("gpay") || lowerText.contains("google pay")) return "GPay";
        if (lowerText.contains("paytm")) return "Paytm";
        if (lowerText.contains("bhim") || lowerText.contains("upi")) return "UPI / BHIM";
        return "Generic UPI";
    }

    private static void parsePhonePe(String text, OcrResponseDTO dto) {
        String utr = findMatch(text, "(?i)(?:UTR|UPI Ref No|Txn ID|Ref No)[:\\s]*(\\d{12})", 1);
        if (utr != null) dto.setUtrNumber(utr);
        
        String payer = findMatch(text, "(?i)(?:From|Paid by|Sender)[:\\s]+([A-Za-z][A-Za-z \\.]{2,30})", 1);
        if (payer != null) {
            String cleaned = cleanPayerName(payer);
            if (cleaned != null) dto.setPayerName(cleaned);
        }
    }

    private static void parsePaytm(String text, OcrResponseDTO dto) {
        String utr = findMatch(text, "(?i)(?:UPI Ref No|Ref No|UTR|Txn ID)[:\\s\\.]*(\\d{12})", 1);
        if (utr != null) dto.setUtrNumber(utr);
        
        String payer = findMatch(text, "(?i)(?:From|Paid by|Sender|To)[:\\s]+([A-Za-z][A-Za-z \\.]{2,30})", 1);
        if (payer != null) {
            String cleaned = cleanPayerName(payer);
            if (cleaned != null) dto.setPayerName(cleaned);
        }
    }

    private static void parseGPay(String text, OcrResponseDTO dto) {
        String utr = findMatch(text, "(?i)(?:UPI transaction ID|UPI Ref No|UTR)[:\\s]*(\\d{12})", 1);
        if (utr != null) dto.setUtrNumber(utr);
        
        String payer = findMatch(text, "(?i)(?:From|Paid by|Sender)[:\\s]+([A-Za-z][A-Za-z \\.]{2,30})", 1);
        if (payer != null) {
            String cleaned = cleanPayerName(payer);
            if (cleaned != null) dto.setPayerName(cleaned);
        }
    }

    private static String extractPayerName(String text) {
        String[] labels = {
            "Received from", "Paid by", "From", "Sender Name", "Sender", "Banking Name", "Paid to"
        };
        for (String label : labels) {
            Pattern p = Pattern.compile("(?i)" + Pattern.quote(label) + "[:\\s\\n]+([A-Za-z][A-Za-z \\.]{2,30})");
            Matcher m = p.matcher(text);
            if (m.find()) {
                String candidate = m.group(1).trim();
                String cleaned = cleanPayerName(candidate);
                if (cleaned != null && cleaned.length() >= 2) {
                    return cleaned;
                }
            }
        }
        return null;
    }

    private static String cleanPayerName(String name) {
        if (name == null) return null;
        String cleaned = name.trim();
        cleaned = cleaned.split("(?i)\\b(?:to|txn|transaction|ref|upi|date|id|amount|debit|credit|at|on|from|banking|name|rupees|rs|inr)\\b")[0].trim();
        cleaned = cleaned.replaceAll("\\s+", " ");
        if (cleaned.length() < 2 || cleaned.matches(".*\\d.*")) return null;
        return cleaned;
    }

    private static boolean isValidAmount(String candidate) {
        String cleanVal = candidate.replace(",", "");
        double val;
        try {
            val = Double.parseDouble(cleanVal);
        } catch (NumberFormatException e) {
            return false;
        }
        if (cleanVal.length() < 2 || val < 1.0 || cleanVal.length() >= 10) return false;
        if (val >= 2020.0 && val <= 2035.0 && !candidate.contains(".")) return false; // Ignore year values completely
        return true;
    }

    private static String extractAmount(String text) {
        // Pass 1: Strict currency prefix
        Pattern strictP = Pattern.compile("(?i)(?:₹|Rs\\.?|INR)\\s*([0-9,]{2,10}(?:\\.\\d{1,2})?)");
        Matcher strictM = strictP.matcher(text);
        while (strictM.find()) {
            String candidate = strictM.group(1).trim();
            if (isValidAmount(candidate)) return candidate;
        }
        
        // Pass 2: Label based (Received, Paid, Amount, Total)
        Pattern labelP = Pattern.compile("(?i)(?:Amount|Received|Paid|Total)[:\\s]+(?:₹|Rs\\.?|INR)?\\s*([0-9,]{2,10}(?:\\.\\d{1,2})?)");
        Matcher labelM = labelP.matcher(text);
        while (labelM.find()) {
             String candidate = labelM.group(1).trim();
             if (isValidAmount(candidate)) return candidate;
        }

        // Pass 3: Scoring algorithm
        Pattern p = Pattern.compile("([0-9,]+(?:\\.\\d{1,2})?)");
        Matcher m = p.matcher(text);
        String bestAmount = null;
        int highestScore = -100;
        
        while (m.find()) {
            String candidate = m.group(1);
            int start = m.start();
            if (!isValidAmount(candidate)) continue;
            if (isDateOrTimeArtifact(text, start, candidate.length())) continue;
            
            int score = 0;
            int startPrefix = Math.max(0, start - 15);
            String prefix = text.substring(startPrefix, start).toLowerCase();
            int endSuffix = Math.min(text.length(), start + candidate.length() + 15);
            String suffix = text.substring(start + candidate.length(), endSuffix).toLowerCase();
            
            if (prefix.contains("₹") || prefix.contains("rs") || prefix.contains("inr")) score += 100;
            if (prefix.contains("received") || suffix.contains("received")) score += 60;
            if (prefix.contains("paid") || suffix.contains("paid")) score += 60;
            if (prefix.contains("amount") || suffix.contains("amount")) score += 60;
            if (prefix.contains("sent") || suffix.contains("sent")) score += 40;
            if (candidate.contains(".") || candidate.contains(",")) score += 10;
            
            if (score > highestScore) {
                highestScore = score;
                bestAmount = candidate;
            }
        }
        
        return bestAmount;
    }

    private static boolean isDateOrTimeArtifact(String text, int start, int length) {
        int textLen = text.length();
        if (start > 0) {
            char before = text.charAt(start - 1);
            if (before == ':' || before == '/' || before == '-') return true;
        }
        if (start + length < textLen) {
            char after = text.charAt(start + length);
            if (after == ':' || after == '/' || after == '-') return true;
        }
        
        int searchStart = Math.max(0, start - 15);
        int searchEnd = Math.min(textLen, start + length + 15);
        String surrounding = text.substring(searchStart, searchEnd).toLowerCase();
        String[] months = {"jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec"};
        for (String m : months) {
            if (surrounding.contains(m)) return true;
        }
        
        return false;
    }

    private static String findMatch(String text, String regex, int group) {
        try {
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                return matcher.group(group).trim();
            }
        } catch (Exception e) {
            // Ignore regex errors
        }
        return null;
    }
}
