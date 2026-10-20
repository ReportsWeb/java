package com.pao.reports.samples;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pao.reports.engine.model.PreviewJob;
import com.pao.reports.engine.model.ReportData;
import com.pao.reports.engine.model.ReportDocument;
import com.pao.reports.engine.model.ReportObject;
import com.pao.reports.engine.model.ReportObjectType;
import java.io.IOException;
import java.util.Base64;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * サーバーが利用者から受け取った印刷データの画像の規則（BUG-24。Web の server/imagePolicy.ts と同じ）。
 * <ul>
 * <li>使えるのは埋め込みデータ（PNG・JPEG・GIF のバイト列）だけ。</li>
 * <li>パス・file: URL・http(s) URL・UNC・{@code ..}・絶対パス・シンボリックリンクは、ファイルを開く・接続する前に拒否する。</li>
 * <li>パスと埋め込みの両方があれば埋め込みを使う（パスは捨て、読まない）。</li>
 * <li>拒否の文言に、送られたパスや URL を含めない。</li>
 * </ul>
 * エンジン（{@code com.pao.reports.engine.io.UntrustedImagePolicy}）と同じ規則を、このサンプルの中に持つ。
 * 配布の {@code SDK/lib} の engine の版がこのクラスを持たない場合でも、サンプルのサーバーが同じ保護で動くようにするため。
 */
final class UntrustedImagePolicy {
    /** 送られたパスや URL を含めない、固定の文言。 */
    static final String MESSAGE = "サーバーで描画する画像は、埋め込みの PNG・JPEG・GIF のデータだけを受け付けます。画像のパスや URL は使えません。";

    /** 拒否。メッセージは常に {@link #MESSAGE}（入力の値を含めない）。 */
    static final class RejectedImageException extends IOException {
        RejectedImageException() { super(MESSAGE); }
    }

    private static final Pattern DATA_URI = Pattern.compile("(?is)^data:image/(png|jpeg|jpg|gif);base64,(.*)$");
    private static final Pattern BASE64 = Pattern.compile("^[A-Za-z0-9+/=\\s]+$");
    private static final Set<String> IMAGE_VALUE_TYPES = Set.of("image", "dynamicimage", "pmdefimage", "pmdefdynamicimage");
    private static final long MAX_ENCODED_CHARS = 64L * 1024 * 1024;
    // 本文は受け取りの上限（64 MiB）までなので、埋め込み画像の長い文字列も読めるようにする。
    private static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder()
            .streamReadConstraints(StreamReadConstraints.builder().maxStringLength(Integer.MAX_VALUE).build()).build());

    private UntrustedImagePolicy() {}

    /**
     * 読み込む前の検査。受け取った JSON 全体を見て、画像のパス（埋め込みのないもの）と、
     * 画像の Value に埋め込みでない値があれば拒否する。読み込み側がファイルの有無を調べる前に断るため。
     * 読んだ JSON を返す（{@link RenderBudget#checkJson} で使う）。JSON として読めないものは通常の {@link IOException}。
     */
    static JsonNode checkPrintDataJson(byte[] json) throws IOException {
        JsonNode root = JSON.readTree(json);
        if (root == null || root.isMissingNode()) throw new IOException("empty");
        checkNode(root);
        return root;
    }

    private static void checkNode(JsonNode node) throws IOException {
        if (node.isArray()) { for (var child : node) checkNode(child); return; }
        if (!node.isObject()) return;
        String type = textIgnoreCase(node, "Type");
        if (type != null && IMAGE_VALUE_TYPES.contains(type.toLowerCase(Locale.ROOT))) {
            String value = textIgnoreCase(node, "Value");
            if (value != null && !value.isBlank() && embeddedBytes(value) == null) throw new RejectedImageException();
        }
        for (Iterator<Map.Entry<String, JsonNode>> it = node.fields(); it.hasNext();) {
            var entry = it.next();
            String key = entry.getKey(), lower = key.toLowerCase(Locale.ROOT);
            // 他の版の拡張は描画を決めない。Java 自身の拡張から戻る値は、読み込み後の検査で見る。
            if (lower.equals("extensiondata")) continue;
            if ((lower.equals("imagepath") || lower.equals("backgroundimagepath")) && entry.getValue().isTextual()
                    && !entry.getValue().textValue().isBlank()) {
                String embedded = textIgnoreCase(node, key.substring(0, key.length() - 4) + "DataBase64");
                if (embedded == null || embedded.isBlank()) throw new RejectedImageException();
            }
            // 埋め込みの欄にパスや URL を書いたもの（読み込み側がパスとして扱わないよう、ここで断る）。
            if ((lower.equals("imagedatabase64") || lower.equals("backgroundimagedatabase64")) && entry.getValue().isTextual()
                    && !entry.getValue().textValue().isBlank() && embeddedBytes(entry.getValue().textValue()) == null)
                throw new RejectedImageException();
            checkNode(entry.getValue());
        }
    }

    /**
     * 読み込んだ後の検査と正規化。すべてのページの定義（背景を含む）と画像オブジェクトについて、
     * 埋め込みが PNG・JPEG・GIF であることを確かめ、パスを捨てる。埋め込みがなくパスがあれば拒否する。
     * 画像に結び付いた値（Values・行・動的属性で付いた結び付け）は、埋め込みならバイト列に置き換え、
     * それ以外は拒否する。値がない結び付けには null を置き、既定のページ番号などがパスとして使われないようにする。
     */
    static void requireEmbeddedImages(PreviewJob job) throws IOException {
        if (job == null || job.pages == null) return;
        for (var page : job.pages) {
            if (page == null) continue;
            if (page.data == null) page.data = new ReportData();
            requireEmbeddedImages(page.definition, page.data);
        }
    }

    /** 1 ページ分（定義と値）。 */
    static void requireEmbeddedImages(ReportDocument document, ReportData data) throws IOException {
        if (document == null) return;
        if (!blank(document.backgroundImageDataBase64)) {
            if (embeddedBytes(document.backgroundImageDataBase64) == null) throw new RejectedImageException();
            document.backgroundImagePath = "";
        } else if (!blank(document.backgroundImagePath)) throw new RejectedImageException();
        if (document.objects == null) return;
        for (ReportObject o : document.objects) {
            if (o == null) continue;
            if (!blank(o.imageDataBase64)) {
                if (embeddedBytes(o.imageDataBase64) == null) throw new RejectedImageException();
                o.imagePath = "";
            } else if (!blank(o.imagePath)) throw new RejectedImageException();
            if (o.type != ReportObjectType.IMAGE || blank(o.binding) || data == null) continue;
            if (data.values == null) data.values = new java.util.LinkedHashMap<>();
            if (!data.values.containsKey(o.binding)) data.values.put(o.binding, null);
            else data.values.put(o.binding, boundImage(data.values.get(o.binding)));
            if (data.rows != null) for (var row : data.rows) if (row != null && row.containsKey(o.binding)) row.put(o.binding, boundImage(row.get(o.binding)));
        }
    }

    private static Object boundImage(Object value) throws IOException {
        if (value == null) return null;
        if (value instanceof byte[] bytes) { if (!signature(bytes)) throw new RejectedImageException(); return bytes; }
        if (value instanceof String text) {
            if (text.isBlank()) return null;
            byte[] bytes = embeddedBytes(text);
            if (bytes == null) throw new RejectedImageException();
            return bytes;
        }
        throw new RejectedImageException();
    }

    /**
     * 埋め込みの画像（{@code data:image/png|jpeg|gif;base64,...} か、生の Base64）を復号する。
     * PNG・JPEG・GIF の先頭でなければ null。パスや URL として解釈することはない。
     */
    static byte[] embeddedBytes(String value) {
        if (value == null || value.length() > MAX_ENCODED_CHARS) return null;
        String base64 = value.strip();
        var match = DATA_URI.matcher(base64);
        if (match.matches()) base64 = match.group(2);
        else if (base64.regionMatches(true, 0, "data:", 0, 5)) return null;
        if (base64.isEmpty() || !BASE64.matcher(base64).matches()) return null;
        try {
            byte[] bytes = Base64.getMimeDecoder().decode(base64);
            return signature(bytes) ? bytes : null;
        } catch (IllegalArgumentException invalid) { return null; }
    }

    private static boolean signature(byte[] b) {
        if (b.length >= 8 && (b[0] & 0xff) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G' && b[4] == 0x0d && b[5] == 0x0a && b[6] == 0x1a && b[7] == 0x0a) return true;
        if (b.length >= 3 && (b[0] & 0xff) == 0xff && (b[1] & 0xff) == 0xd8 && (b[2] & 0xff) == 0xff) return true;
        return b.length >= 6 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8' && (b[4] == '7' || b[4] == '9') && b[5] == 'a';
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }

    private static String textIgnoreCase(JsonNode node, String name) {
        for (Iterator<Map.Entry<String, JsonNode>> it = node.fields(); it.hasNext();) {
            var entry = it.next();
            if (entry.getKey().equalsIgnoreCase(name)) return entry.getValue().isTextual() ? entry.getValue().textValue() : null;
        }
        return null;
    }
}
