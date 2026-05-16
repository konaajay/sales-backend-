package com.lms.www.leadmanagement.util;

import com.lms.www.leadmanagement.dto.OcrResponseDTO;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PaymentOcrParser {

    /**
     * Parses raw OCR text into structured payment data using Regex.
     * 
     * Handles:
     * - UTR: 12-digit numbers or transaction references
     * - Amount: Currency symbols followed by numbers
     * - Date/Time: Various Indian and international formats
     * - App Recognition: Keywords like PhonePe, GPay, Paytm
     */
    public static OcrResponseDTO parse(String text) {
        OcrResponseDTO dto = new OcrResponseDTO();
        dto.setSuccess(true);
        dto.setRawText(text);

        // 1. Extract UTR / Transaction ID
        // Strategy: Prioritize 12-digit UTR (standard for banks) over internal app Transaction IDs
        String utr12 = findMatch(text, "(?i)(?:UTR|Ref No|UPI Ref No)[:\\s]*(\\d{12})", 1);
        String txnId = findMatch(text, "(?i)(?:Transaction ID|Txn ID|Ref ID)[:\\s]*([A-Z\\d]{10,25})", 1);
        
        // If no labels found, try to find a standalone 12-digit number (common in UPI)
        if (utr12 == null && txnId == null) {
            utr12 = findMatch(text, "(?:^|\\s)(\\d{12})(?:$|\\s)", 1);
        }
        
        dto.setUtrNumber(utr12 != null ? utr12 : txnId);
        
        // 2. Extract Amount
        // Strategy: Look for numbers with commas/decimals near currency symbols (including misreads like #, $, S)
        dto.setAmount(findMatch(text, "(?i)(?:Amount|Total|Paid|Value)[:\\s]*[₹Rs\\.#\\$S\\.\\s]*([\\d,]+\\.\\d{2})", 1));
        if (dto.getAmount() == null) {
            // High-confidence: Number immediately following a currency symbol
            dto.setAmount(findMatch(text, "[₹#\\$S]\\s?([\\d,]+\\.?\\d{0,2})", 1));
        }
        if (dto.getAmount() == null) {
            // PhonePe/GPay: Large bold amount often near the top, usually has a comma
            dto.setAmount(findMatch(text, "(?:^|\\s)([\\d]{1,3},[\\d]{2,3}(?:\\.\\d{2})?)(?:$|\\s)", 1));
        }
        if (dto.getAmount() == null) {
            // Very broad fallback: Find any number with comma/decimal that isn't a UTR (12+ digits)
            // e.g. 2,000 or 500.00
            String possibleAmount = findMatch(text, "(?:^|\\s)([\\d,]{3,}(?:\\.\\d{2})?)(?:$|\\s)", 1);
            if (possibleAmount != null && possibleAmount.length() < 12) {
                dto.setAmount(possibleAmount);
            }
        }

        // 3. Extract Date
        // Patterns: 12 May 2026, 3rd May 2026, 12/05/2026, 15-05-2026, May 15, 2026
        String dateStr = findMatch(text, "(?i)(?:on\\s)?(\\d{1,2}(?:st|nd|rd|th)?\\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[\\w]*[\\s,]*\\d{4}|\\d{2}/\\d{2}/\\d{4}|\\d{2}-\\d{2}-\\d{4}|(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[\\w]*\\s+\\d{1,2}[\\s,]*\\d{4})", 1);
        dto.setPaymentDate(dateStr);

        // 4. Extract Time
        // Prioritize time with AM/PM found in the body over the phone clock at the top
        dto.setPaymentTime(findMatch(text, "(\\d{1,2}:\\d{2}(?::\\d{2})?\\s?(?:AM|PM|am|pm))", 1));
        if (dto.getPaymentTime() == null) {
            dto.setPaymentTime(findMatch(text, "(\\d{1,2}:\\d{2}(?::\\d{2})?)", 1));
        }

        // 5. Detect Payment App
        String lowerText = text.toLowerCase();
        if (lowerText.contains("phonepe")) dto.setPaymentApp("PhonePe");
        else if (lowerText.contains("gpay") || lowerText.contains("google pay")) dto.setPaymentApp("GPay");
        else if (lowerText.contains("paytm")) dto.setPaymentApp("Paytm");
        else if (lowerText.contains("bhim") || lowerText.contains("bharat's own")) dto.setPaymentApp("UPI / BHIM");
        else if (lowerText.contains("yono") || lowerText.contains("sbi")) dto.setPaymentApp("SBI Yono / Bank");
        else dto.setPaymentApp("Detected Bank/App");

        // 6. Extract Payer Name
        // Heuristic: names near "Paid by", "From", "Sender", "Banking Name", or "Received from"
        // Ignore single character misreads like 'v' or 'o' before the name
        dto.setPayerName(findMatch(text, "(?i)(?:Paid by|From|Sender|Banking Name|Received from)[:\\s]*[vo\\.]?\\s*([A-Z][A-Z\\s]{2,25})(?=\\s*(?:Transaction|Txn|Ref|ID|\\d|$))", 1));

        System.out.println(">>> [OCR-DEBUG] Parsed Data: " + dto);
        return dto;
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
