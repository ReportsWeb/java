package quickreport;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pao.reports.engine.io.PreviewJobIO;
import com.pao.reports.engine.io.ReportDefinitionIO;
import com.pao.reports.engine.model.PreviewJob;
import com.pao.reports.engine.model.ReportData;
import com.pao.reports.engine.render.PdfExporter;
import com.pao.reports.engine.render.RenderControl;
import com.pao.reports.engine.render.ReportRenderer;
import com.pao.reports.engine.render.SvgExporter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.apache.pdfbox.Loader;

public final class QuickReport {
    static final String TITLE = "あっという間に帳票出力";
    static final String CUSTOMER = "株式会社パオ";

    private QuickReport() {}

    public static void main(String[] args) throws Exception {
        Path root = args.length == 0 ? Path.of("").toAbsolutePath() : Path.of(args[0]).toAbsolutePath();
        if (java.util.Arrays.asList(args).contains("--design")) {
            QuickDesign.open(root);
            return;
        }
        Path output = root.resolve("output");
        Files.createDirectories(output);

        // 1. WPF、Python、WASMとも共有できるPREPDJを読み込みます。
        var definition = new ReportDefinitionIO().read(root.resolve("definition/quick-report.prepdj"));

        // 2. 業務入力を読み、帳票項目名へ値を設定します。
        Map<String, Object> values = new ObjectMapper().readValue(
                root.resolve("input/quick-report-values.json").toFile(),
                new TypeReference<>() {});
        // このサンプルでは、ソースで変えた2項目を入力データへ反映します。
        values.put("Title", TITLE);
        values.put("CustomerName", CUSTOMER);
        var data = new ReportData();
        data.values.putAll(values);
        for (var object : definition.objects) {
            if (values.containsKey(object.name)) object.binding = object.name;
        }

        // 3. この入力が1ページになることを確認し、ページを確定します。
        int pageCount = new ReportRenderer().pageCount(definition, data);
        require(pageCount == 1, "1ページではありません: " + pageCount);
        var job = new PreviewJob();
        job.name = TITLE;
        job.pages.add(new PreviewJob.Page(definition, data, 0));

        // 4. 再表示・再印刷できる自己完結PREPEJを保存します。
        Path prepej = output.resolve("quick-report.prepej");
        new PreviewJobIO().write(prepej, job);

        // 5. 確定ページをページ単位SVGと帳票全体PDFへ出力します。
        var page = job.pages.getFirst();
        Path svg = output.resolve("quick-report-page-001.svg");
        Files.writeString(svg,
                new SvgExporter().svg(page.definition, page.data, page.sourcePage),
                StandardCharsets.UTF_8);
        Path pdf = output.resolve("quick-report.pdf");
        new PdfExporter().exportPages(
                job.pages.stream()
                        .map(value -> new PdfExporter.Page(value.definition, value.data, value.sourcePage))
                        .toList(),
                pdf, PdfExporter.Options.vector(300, java.util.List.of()), RenderControl.none()); // PR-343：既定はベクター PDF（バーコードが読める）

        verify(prepej, svg, pdf);
        System.out.println("作成完了: " + output);
    }

    private static void verify(Path prepej, Path svg, Path pdf) throws Exception {
        var loaded = new PreviewJobIO().read(prepej);
        require(loaded.selfContained(), "PREPEJが自己完結形式ではありません");
        require(loaded.job().pages.size() == 1, "PREPEJが1ページではありません");
        String svgText = Files.readString(svg, StandardCharsets.UTF_8);
        require(svgText.contains(TITLE), "SVGにタイトルがありません");
        require(svgText.contains(CUSTOMER), "SVGに会社名がありません");
        require(Files.readString(pdf, StandardCharsets.ISO_8859_1).startsWith("%PDF-"),
                "PDFヘッダーがありません");
        try (var document = Loader.loadPDF(pdf.toFile())) {
            require(document.getNumberOfPages() == 1, "PDFが1ページではありません");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
