package com.pao.reports.samples;

import com.pao.reports.engine.model.ReportData;
import java.io.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.sql.*;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Reads the shared installation-free SQLite database and maps its original
 * Japanese columns to the field names consumed by the legacy PREPD definitions. */
final class LegacySampleData {
    private static final NumberFormat NUMBER = NumberFormat.getIntegerInstance(Locale.JAPAN);
    /** ACE OLE DB result order for the legacy query (the SQL does not order by product code). */
    private static final List<String> LEGACY_PRODUCT_ORDER=List.of("1234567890125","1234567890124","1234567890123","1234567890146","1234567890139","1234567890142","1234567890147","1234567890149","1234567890148","1234567890127","1234567890133","1234567890129","1234567890132","1234567890137","1234567890143","1234567890128","1234567890134","1234567890144","1234567890135","1234567890136","1234567890126","1234567890131","1234567890130","1234567890138","1234567890141","1234567890145","1234567890140");
    private LegacySampleData() {}

    static ReportData load(String resource, String sample) throws IOException {
        try {
            var sheets = sheets();
            return switch (sample) {
                case "estimate" -> estimate(sheets);
                case "invoice" -> invoice(sheets);
                case "postal" -> postal(sheets);
                case "products" -> products(sheets);
                default -> throw new IOException("Unknown legacy sample: " + sample);
            };
        } catch (SQLException ex) { throw new IOException("SQLiteサンプルDBを読み込めません", ex); }
    }

    static List<ReportData> loadEstimates() throws IOException {
        try {
            var sheets = sheets();
            var result = new ArrayList<ReportData>();
            for (var header : rows(sheets, "見積ヘッダ")) result.add(estimate(sheets, header));
            return result;
        } catch (SQLException ex) { throw new IOException("SQLiteサンプルDBを読み込めません", ex); }
    }

    private static Map<String,Object> sheets() throws SQLException, IOException {
        var result = new LinkedHashMap<String,Object>();
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database())) {
            for (var table : List.of("見積ヘッダ","見積明細","請求ヘッダ","請求明細","郵便番号テーブル","M_大分類","M_小分類","M_商品"))
                result.put(table, query(connection, table));
        }
        return result;
    }

    private static List<Map<String,Object>> query(Connection connection, String table) throws SQLException {
        var result = new ArrayList<Map<String,Object>>();
        try (var statement=connection.createStatement(); var rows=statement.executeQuery("SELECT * FROM \""+table+"\"")) {
            var metadata=rows.getMetaData();
            while(rows.next()) { var row=new LinkedHashMap<String,Object>(); for(int i=1;i<=metadata.getColumnCount();i++) row.put(metadata.getColumnLabel(i),rows.getObject(i)); result.add(row); }
        }
        return result;
    }

    private static Path database() throws IOException {
        var configured=System.getProperty("reports.sample.db",System.getenv("REPORTS_SAMPLE_DB"));
        if(configured!=null&&!configured.isBlank()) return Path.of(configured).toAbsolutePath();
        for(var candidate:List.of(Path.of("../common/database/sample.db"),Path.of("samples/common/database/sample.db"),Path.of("../../common/database/sample.db")))
            if(Files.isRegularFile(candidate)) return candidate.toAbsolutePath().normalize();
        throw new FileNotFoundException("samples/common/database/sample.db が見つかりません");
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
            row.put("金額", integer(source.get("金額"))); result.rows.add(row);
        }
        return result;
    }

    private static ReportData invoice(Map<String,Object> sheets) {
        return invoice(sheets,rows(sheets, "請求ヘッダ").getFirst());
    }
    static List<ReportData> loadInvoices() throws IOException {
        try { var sheets=sheets();var result=new ArrayList<ReportData>();
            for(var header:rows(sheets,"請求ヘッダ"))result.add(invoice(sheets,header));
            return List.copyOf(result);
        } catch(SQLException ex){throw new IOException("SQLiteサンプルDBを読み込めません",ex);}
    }
    private static ReportData invoice(Map<String,Object> sheets,Map<String,Object> header) {
        var result = new ReportData();
        var number = text(header.get("請求番号"));
        result.values.put("txtNo", number); result.values.put("txtCustomer", header.get("お客様名"));
        result.values.put("txtDate", LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy年M月d日")));
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
        productRows.sort(Comparator.comparing((Map<String,Object> r)->new BigDecimal(text(r.get("大分類コード"))))
                .thenComparing(r->new BigDecimal(text(r.get("小分類コード")))));
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
    private static LinkedHashMap<String,Object> blankProductRow(){var row=new LinkedHashMap<String,Object>();row.put("大分類","");row.put("小分類","");row.put("品番","");row.put("品名","");return row;}
    private static LinkedHashMap<String,Object> subtotal(String label,int count){var row=blankProductRow();row.put("小分類",label);row.put("品番",count+" 冊");var color=label.startsWith("\u5927\u5206\u985e(")?"LightPink":"LightYellow";for(var frame:new String[]{"\u67a0_\u5927\u5206\u985e","\u67a0_\u5c0f\u5206\u985e","\u67a0_\u54c1\u756a","\u67a0_\u54c1\u540d"})row.put("@"+frame+".fillColor",color);return row;}
    private static String text(Object value){return value==null?"":String.valueOf(value);}
    private static long whole(Object value){return value instanceof Number n?n.longValue():new BigDecimal(text(value)).longValue();}
    private static String integer(Object value){return NUMBER.format(whole(value));}
    private static String excelDate(Object value){if(value instanceof Number)return LocalDate.of(1899,12,30).plusDays(whole(value)).format(DateTimeFormatter.ofPattern("yyyy年M月d日"));var text=text(value);try{return LocalDate.parse(text.substring(0,10)).format(DateTimeFormatter.ofPattern("yyyy年M月d日"));}catch(Exception ignored){return text;}}
}
