package com.pao.reports.samples;

import com.fasterxml.jackson.databind.JsonNode;
import com.pao.reports.engine.model.ReportData;
import com.pao.reports.engine.model.ReportObjectType;

/**
 * 受け取った印刷データを描く前に調べる、描画の重さの上限（T-H1・BUG-28）。超えたら 413 で断る。
 *
 * <p>受け取りの大きさ（64 MiB）の中でも、描くと何倍にも膨らむ入力がある。
 * <ul>
 * <li>埋め込み画像の画素数：1 枚 4,000 万画素、1 回の要求の合計 1 億 2,000 万画素まで。
 *     30000×30000 の PNG は 1〜2.5 MB でも、展開すると 1〜3.6 GB になる。</li>
 * <li>繰返しで広がるオブジェクトの数：1 ページ 100,000 個まで（エンジンの RepeatLimits.PAGE_OBJECT_LIMIT と同じ）。</li>
 * </ul>
 * 画像の大きさは、ファイルの先頭（PNG の IHDR、GIF の画面の大きさ、JPEG の SOF）だけを読んで調べる（展開しない）。
 * 上限の値は .NET（Wpf {@code NativeDemoPrintDataPolicy.CheckRenderBudget}）とそろえている。
 *
 * <p>{@link #checkJson} は、エンジンが印刷データを読む前に、JSON の中の埋め込み画像の大きさを調べる。
 * 配布の {@code SDK/lib} の engine の版によっては、読み込みの途中で値の画像を展開するため、その前に断る。
 */
final class RenderBudget {
    static final long MAX_IMAGE_PIXELS = 40_000_000L;
    static final long MAX_TOTAL_IMAGE_PIXELS = 120_000_000L;
    static final int MAX_OBJECTS_PER_PAGE = 100_000;
    /** これより短い文字列は画像の大きさを調べない（PNG・GIF・JPEG の先頭と大きさが入らない）。 */
    private static final int MIN_IMAGE_TEXT = 32;

    static final class ExceededException extends RuntimeException {
        ExceededException(String message) { super(message); }
    }

    private RenderBudget() {}

    /** 読み込む前：JSON の中のすべての埋め込み画像（定義・値・背景・拡張のどこでも）の画素数を、1 枚と合計で調べる。 */
    static void checkJson(JsonNode root) {
        long[] total = {0};
        walk(root, total);
    }

    private static void walk(JsonNode node, long[] total) {
        if (node == null) return;
        if (node.isContainerNode()) { for (var child : node) walk(child, total); return; }
        if (!node.isTextual() || node.textValue().length() < MIN_IMAGE_TEXT) return;
        total[0] = add(total[0], pixels(UntrustedImagePolicy.embeddedBytes(node.textValue())), 1);
    }

    /** {@link UntrustedImagePolicy#requireEmbeddedImages} の後（結び付いた画像の値が byte[] になった後）に呼ぶ。 */
    static void check(SampleCatalog.Sample sample) {
        long total = 0;
        for (var page : sample.pages()) {
            var document = page.definition();
            if (document == null) continue;
            long objects = 0;
            if (document.objects != null) for (var o : document.objects) {
                if (o == null) continue;
                objects += copies(o, page.data());
                if (objects > MAX_OBJECTS_PER_PAGE)
                    throw new ExceededException("1 ページのオブジェクトが " + MAX_OBJECTS_PER_PAGE + " 個を超えます（繰返しの書いた行を含む）。");
            }
            total = add(total, pixels(UntrustedImagePolicy.embeddedBytes(document.backgroundImageDataBase64)), 1);
            if (document.objects == null) continue;
            ReportData data = page.data();
            for (var o : document.objects) {
                if (o == null || o.type != ReportObjectType.IMAGE) continue;
                long copies = copies(o, page.data());
                if (o.imageDataBase64 != null && !o.imageDataBase64.isBlank())
                    total = add(total, pixels(UntrustedImagePolicy.embeddedBytes(o.imageDataBase64)), copies);
                if (data == null || o.binding == null || o.binding.isBlank()) continue;
                if (data.values != null && data.values.get(o.binding) instanceof byte[] bytes) total = add(total, pixels(bytes), copies);
                if (data.rows != null) for (var row : data.rows)
                    if (row != null && row.get(o.binding) instanceof byte[] bytes) total = add(total, pixels(bytes), 1);
            }
        }
    }

    /** PR-401：繰返しは Repeat の値ではなく描く行（書いた行、無ければ 1 行）で数える（エンジンの RepeatLimits.drawnCopies と同じ。原本 13.4.0 どおり）。 */
    private static long copies(com.pao.reports.engine.model.ReportObject o, ReportData data) {
        if (o.repeat <= 0) return 1;
        long perPage = (long) o.repeat * (o.repeatXYBoth ? Math.max(1, o.repeatX) : 1);
        String key = o.binding == null || o.binding.isBlank() ? o.name : o.binding;
        long count = 0;
        var rows = data == null || data.rows == null ? java.util.List.<java.util.Map<String, Object>>of() : data.rows;
        if (key != null) for (int i = 0; i < Math.min(perPage, rows.size()); i++) if (rows.get(i) != null && rows.get(i).containsKey(key)) count++;
        boolean first = key != null && !rows.isEmpty() && rows.get(0) != null && rows.get(0).containsKey(key);
        if (data != null && data.values != null && key != null && data.values.containsKey(key) && !first) count++;
        return Math.max(1, count);
    }

    private static long add(long total, long pixels, long copies) {
        if (pixels > MAX_IMAGE_PIXELS)
            throw new ExceededException("埋め込み画像が大きすぎます（1 枚 " + MAX_IMAGE_PIXELS / 1_000_000 + " 百万画素まで）。");
        if (pixels > 0 && copies > MAX_TOTAL_IMAGE_PIXELS / pixels)
            throw new ExceededException("埋め込み画像の合計が大きすぎます（" + MAX_TOTAL_IMAGE_PIXELS / 1_000_000 + " 百万画素まで）。");
        total += pixels * copies;
        if (total > MAX_TOTAL_IMAGE_PIXELS)
            throw new ExceededException("埋め込み画像の合計が大きすぎます（" + MAX_TOTAL_IMAGE_PIXELS / 1_000_000 + " 百万画素まで）。");
        return total;
    }

    /** 画像の先頭だけを見て幅×高さを返す。分からなければ 0（描画側が読めずに診断を出す）。 */
    static long pixels(byte[] b) {
        if (b == null) return 0;
        if (b.length >= 24 && (b[0] & 0xff) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G')
            return big32(b, 16) * big32(b, 20);
        if (b.length >= 10 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F')
            return (long) ((b[6] & 0xff) | (b[7] & 0xff) << 8) * ((b[8] & 0xff) | (b[9] & 0xff) << 8);
        if (b.length >= 4 && (b[0] & 0xff) == 0xff && (b[1] & 0xff) == 0xd8) {
            int position = 2;
            while (position + 9 < b.length) {
                if ((b[position] & 0xff) != 0xff) { position++; continue; }
                int marker = b[position + 1] & 0xff;
                if (marker == 0xd8 || marker == 0x01 || (marker >= 0xd0 && marker <= 0xd7) || marker == 0xff) { position += marker == 0xff ? 1 : 2; continue; }
                // SOF0〜SOF15（DHT=C4・JPG=C8・DAC=CC を除く）に高さと幅がある
                if (marker >= 0xc0 && marker <= 0xcf && marker != 0xc4 && marker != 0xc8 && marker != 0xcc)
                    return (long) big16(b, position + 5) * big16(b, position + 7);
                position += 2 + big16(b, position + 2);
            }
        }
        return 0;
    }

    private static int big16(byte[] b, int i) { return (b[i] & 0xff) << 8 | (b[i + 1] & 0xff); }
    private static long big32(byte[] b, int i) { return (long) (b[i] & 0xff) << 24 | (b[i + 1] & 0xff) << 16 | (b[i + 2] & 0xff) << 8 | (b[i + 3] & 0xff); }
}
