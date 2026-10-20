package com.pao.reports.samples;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pao.reports.engine.model.ReportData;
import java.io.*;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Converts the installation-free JSON extraction of the original Excel workbooks
 * into the exact field names consumed by the legacy PREPD definitions. */
final class LegacySampleData {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final NumberFormat NUMBER = NumberFormat.getIntegerInstance(Locale.JAPAN);
    /** ACE OLE DB result order for the legacy query (the SQL does not order by product code). */
    private static final List<String> LEGACY_PRODUCT_ORDER=List.of("1234567890125","1234567890124","1234567890123","1234567890146","1234567890139","1234567890142","1234567890147","1234567890149","1234567890148","1234567890127","1234567890133","1234567890129","1234567890132","1234567890137","1234567890143","1234567890128","1234567890134","1234567890144","1234567890135","1234567890136","1234567890126","1234567890131","1234567890130","1234567890138","1234567890141","1234567890145","1234567890140");
    /** 見積書.prepd の明細の枠（Repeat=7 の四角）。 */
    private static final List<String> ESTIMATE_FRAMES=List.of("品番白","品名白","数量白","単価白","金額白","Square10","単価青","Square5","品名青","品番青","数量青","金額青");
    private LegacySampleData() {}

    static ReportData load(String resource, String sample) throws IOException {
        try (var input = LegacySampleData.class.getResourceAsStream("/data/" + resource)) {
            if (input == null) throw new FileNotFoundException(resource);
            var root = JSON.readValue(input, new TypeReference<Map<String,Object>>() {});
            @SuppressWarnings("unchecked") var sheets = (Map<String,Object>) root.get("sheets");
            return switch (sample) {
                case "estimate" -> estimate(sheets);
                case "invoice" -> invoice(sheets);
                case "postal" -> postal(sheets);
                case "products" -> products(sheets);
                default -> throw new IOException("Unknown legacy sample: " + sample);
            };
        }
    }

    static List<ReportData> loadEstimates() throws IOException {
        try (var input = LegacySampleData.class.getResourceAsStream("/data/estimate.data.json")) {
            if (input == null) throw new FileNotFoundException("estimate.data.json");
            var root = JSON.readValue(input, new TypeReference<Map<String,Object>>() {});
            @SuppressWarnings("unchecked") var sheets = (Map<String,Object>) root.get("sheets");
            var result = new ArrayList<ReportData>();
            for (var header : rows(sheets, "見積ヘッダ")) result.add(estimate(sheets, header));
            return result;
        }
    }

    private static ReportData estimate(Map<String,Object> sheets) {
        return estimate(sheets, rows(sheets, "見積ヘッダ").getFirst());
    }

    private static ReportData estimate(Map<String,Object> sheets, Map<String,Object> header) {
        var result = new ReportData();
        var number = text(header.get("見積番号"));
        result.values.put("見積番号", number);
        result.values.put("お客様名", header.get("お客様名"));
        result.values.put("担当者名", header.get("担当者名"));
        result.values.put("見積日", excelDate(header.get("見積日")));
        result.values.put("ヘッダ合計", "\\ " + integer(header.get("合計金額")));
        result.values.put("消費税額", integer(header.get("消費税額")));
        result.values.put("フッタ合計", integer(header.get("合計金額")));
        for (var source : rows(sheets, "見積明細")) if (number.equals(text(source.get("見積番号")))) {
            var row = new LinkedHashMap<String,Object>();
            row.put("品番", source.get("品番")); row.put("品名", source.get("品名"));
            row.put("数量", text(source.get("数量"))); row.put("単価", integer(source.get("単価")));
            row.put("金額", integer(source.get("金額")));
            // D-37：明細の枠（7 回の繰返しの四角）は、書いた行にだけ描く（書かなければ 1 行目に 1 回だけ。原本 Reports.net と同じ）。
            // 明細のある行の数だけ、枠に値 null を書く。
            if (result.rows.size() < 7) for (var frame : ESTIMATE_FRAMES) row.put(frame, null);
            result.rows.add(row);
        }
        return result;
    }

    private static ReportData invoice(Map<String,Object> sheets) {
        var result = new ReportData();
        var header = rows(sheets, "請求ヘッダ").getFirst();
        var number = text(header.get("請求番号"));
        result.values.put("txtNo", number); result.values.put("txtCustomer", header.get("お客様名"));
        result.values.put("txtDate", "2003年5月24日");
        result.values.put("Image1", "角印.png");
        long total = 0;
        result.rows.add(new LinkedHashMap<>(Map.of("field1","品番","field2","品名","field3","数量","field4","単価","field5","金額")));
        for (var source : rows(sheets, "請求明細")) if (number.equals(text(source.get("請求番号")))) {
            long quantity = whole(source.get("数量")), price = whole(source.get("単価")), amount = quantity * price;
            var row = new LinkedHashMap<String,Object>();
            row.put("field1", source.get("品番")); row.put("field2", source.get("品名"));
            row.put("field3", Long.toString(quantity)); row.put("field4", integer(price)); row.put("field5", integer(amount));
            result.rows.add(row); total += amount;
        }
        // The definition reserves ten detail rows; totals start at row 11
        // after the heading, even when an invoice has fewer detail records.
        while (result.rows.size() < 11) result.rows.add(new LinkedHashMap<>());
        result.rows.add(new LinkedHashMap<>(Map.of("field4","小計","field5",integer(total))));
        result.rows.add(new LinkedHashMap<>(Map.of("field4","消費税","field5",integer(Math.round(total * .05d)))));
        result.rows.add(new LinkedHashMap<>(Map.of("field4","合計","field5",integer(Math.round(total * 1.05d)))));
        result.values.put("txtTotal", integer(Math.round(total * 1.05d)));
        return result;
    }

    private static ReportData postal(Map<String,Object> sheets) {
        var result = new ReportData(); result.values.put("日時", "2003/05/24 12:00:00");
        for (var source : rows(sheets, "郵便番号テーブル")) {
            var row = new LinkedHashMap<String,Object>();
            row.put("郵便番号", source.get("郵便番号")); row.put("市区町村", source.get("市区町村")); row.put("住所", source.get("住所"));
            // D-37：繰返しの罫線は、書いた行にだけ描く（書かなければ 1 行目に 1 回だけ。原本 Reports.net と同じ）。行ごとに値 null を書く。
            row.put("横罫線", null);
            result.rows.add(row);
        }
        return result;
    }

    private static ReportData products(Map<String,Object> sheets) {
        var result = new ReportData();
        var major = index(rows(sheets,"M_大分類"), "大分類コード", "大分類名称");
        var minor = new HashMap<String,String>();
        for (var row:rows(sheets,"M_小分類")) minor.put(text(row.get("大分類コード"))+":"+text(row.get("小分類コード")), text(row.get("小分類名称")));
        String previousMajor = null, previousMinor = null, previousMajorName = null, previousMinorName = null; int majorCount=0,minorCount=0;
        var productRows=new ArrayList<>(rows(sheets,"M_商品"));
        productRows.sort(Comparator.comparingInt(r->LEGACY_PRODUCT_ORDER.indexOf(text(r.get("品番")))));
        for (var source:productRows) {
            var majorKey=text(source.get("大分類コード")); var minorKey=majorKey+":"+text(source.get("小分類コード"));
            var majorName=major.get(majorKey); var minorName=minor.get(minorKey);
            if(previousMinor!=null&&!previousMinor.equals(minorKey)){result.rows.add(subtotal("小分類("+previousMinorName+")小計",minorCount));minorCount=0;}
            if(previousMajor!=null&&!previousMajor.equals(majorKey)){result.rows.add(subtotal("大分類("+previousMajorName+")小計",majorCount));majorCount=0;}
            var row=blankProductRow();
            row.put("大分類", Objects.equals(previousMajor,majorKey)?"":majorName); row.put("小分類",Objects.equals(previousMinor,minorKey)?"":minorName);
            row.put("品番",source.get("品番")); row.put("品名",source.get("品名")); result.rows.add(row);
            previousMajor=majorKey; previousMinor=minorKey; previousMajorName=majorName; previousMinorName=minorName; majorCount++; minorCount++;
        }
        if(previousMinor!=null)result.rows.add(subtotal("小分類("+previousMinorName+")小計",minorCount));
        if(previousMajor!=null)result.rows.add(subtotal("大分類("+previousMajorName+")小計",majorCount));
        return result;
    }


    @SuppressWarnings("unchecked") private static List<Map<String,Object>> rows(Map<String,Object> sheets,String name) { return (List<Map<String,Object>>)sheets.get(name); }
    private static Map<String,String> index(List<Map<String,Object>> rows,String key,String value){var result=new HashMap<String,String>();for(var row:rows)result.put(text(row.get(key)),text(row.get(value)));return result;}
    private static LinkedHashMap<String,Object> blankProductRow(){var row=new LinkedHashMap<String,Object>();row.put("大分類","");row.put("小分類","");row.put("品番","");row.put("品名","");
        // D-37：表の枠（繰返しの四角）は、書いた行にだけ描く（書かなければ 1 行目に 1 回だけ。原本 Reports.net と同じ）。行ごとに値 null を書く。
        for(var frame:new String[]{"枠_大分類","枠_小分類","枠_品番","枠_品名"})row.put(frame,null);
        return row;}
    private static LinkedHashMap<String,Object> subtotal(String label,int count){var row=blankProductRow();row.put("小分類",label);row.put("品番",count+" 冊");return row;}
    private static String text(Object value){return value==null?"":String.valueOf(value);}
    private static long whole(Object value){return value instanceof Number n?n.longValue():new BigDecimal(text(value)).longValue();}
    private static String integer(Object value){return NUMBER.format(whole(value));}
    private static String excelDate(Object value){return LocalDate.of(1899,12,30).plusDays(whole(value)).format(DateTimeFormatter.ofPattern("yyyy年M月d日"));}
}
