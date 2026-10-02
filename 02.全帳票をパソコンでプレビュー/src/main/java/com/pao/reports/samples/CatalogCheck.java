package com.pao.reports.samples;

/** Headless acceptance check for the SQLite catalog; it does not open JavaFX. */
public final class CatalogCheck {
    private CatalogCheck() {}
    public static void main(String[] args) {
        var samples=SampleCatalog.all();
        require(samples.size()==8,"帳票数: "+samples.size());
        var postal=samples.stream().filter(value->value.name().equals("郵便番号一覧")).findFirst().orElseThrow();
        var estimate=samples.stream().filter(value->value.name().equals("見積書")).findFirst().orElseThrow();
        var invoice=samples.stream().filter(value->value.name().equals("請求書")).findFirst().orElseThrow();
        require(postal.pageCount()==81,"郵便番号ページ数: "+postal.pageCount());
        require(estimate.pageCount()==4,"見積書ページ数: "+estimate.pageCount());
        require("5,630,184".equals(invoice.data().values.get("txtTotal")),"請求合計: "+invoice.data().values.get("txtTotal"));
        require("10000001".equals(String.valueOf(invoice.data().values.get("txtNo"))),"請求番号: "+invoice.data().values.get("txtNo"));
        System.out.println("SQLite catalog OK: reports="+samples.size()+", postal=81, estimate=4, invoiceTotal=5,630,184");
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
