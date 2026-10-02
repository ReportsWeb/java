package com.pao.reports.samples;

import java.nio.charset.StandardCharsets;

/** Exercises the same controller paths as HTTP without opening a port. */
public final class WebApiCheck {
    private WebApiCheck() {}
    public static void main(String[] args) throws Exception {
        var api=new SampleRestController();
        var catalog=SampleCatalog.all();
        require(catalog.size()==8,"全8帳票");
        var expectedPages=new int[]{1,4,1,81,4,2,2,1};
        for(int i=0;i<expectedPages.length;i++)require(catalog.get(i).pageCount()==expectedPages[i],"page count S"+String.format("%02d",i+1)+": expected="+expectedPages[i]+", actual="+catalog.get(i).pageCount());
        require(api.samples().size()==8,"公開帳票一覧");
        byte[] data=null,pdf=null;String svg=null;
        for(int i=1;i<=8;i++){var id=String.format("S%02d",i);data=api.printData(id).getBody();require(data!=null&&data.length>1000,"PREPEJ "+id);svg=api.svg(id,0).getBody();require(svg!=null&&svg.contains("<svg"),"SVG "+id);pdf=api.pdfGet(id).getBody();require(pdf!=null&&new String(pdf,0,5,StandardCharsets.ISO_8859_1).equals("%PDF-"),"PDF "+id);}
        require("PureJava".equals(api.health().getHeaders().getFirst("X-Reports-Engine")),"engine header");
        System.out.println("Native Web API OK: PREPEJ="+data.length+", SVG="+svg.length()+", PDF="+pdf.length);
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
