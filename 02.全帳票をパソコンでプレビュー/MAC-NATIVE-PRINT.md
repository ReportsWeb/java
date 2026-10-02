# Macでの印刷

更新済みMacプレビュアーには専用印刷部品を同梱しています。サンプルをソースから実行する開発環境では、Xcode Command Line Toolsを用意し、このフォルダーで `sh installer/build-native-print-helper.sh` を一度実行してください。利用者向け配布アプリはコンパイラー不要です。

部品が見つからない場合は明示的なエラーにし、総ページ数を誤表示する旧ダイアログへ戻しません。ソース試験ではJavaの `-Dreports.mac.print.helper=/絶対パス/reports-print-helper`、Pythonの `REPORTS_MAC_PRINT_HELPER` でも部品を指定できます。
