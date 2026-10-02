#!/bin/sh
set -eu
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
mkdir -p "$ROOT/build"
clang -fobjc-arc -framework Cocoa -framework PDFKit "$ROOT/installer/native/reports-print-helper.m" -o "$ROOT/build/reports-print-helper"
