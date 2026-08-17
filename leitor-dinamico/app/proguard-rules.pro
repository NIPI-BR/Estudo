# O PdfBox carrega classes por reflexão ao processar fontes do PDF.
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**
-dontwarn org.apache.**
-dontwarn javax.**
