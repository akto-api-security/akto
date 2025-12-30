package com.akto.threat.detection.utils;

import java.util.*;
import java.util.regex.*;


import java.util.function.Supplier;

import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import com.google.common.hash.BloomFilter;
import java.nio.charset.StandardCharsets;
import com.google.common.hash.Funnels;
import java.util.concurrent.*;

public class CRSEvaluator {
    private List<Rule> rules = new ArrayList<>();
    private List<Pattern> compiledRules = new ArrayList<>();
    private BloomFilter<String> benignRequestParamsBloomFilter = BloomFilter
            .create(Funnels.stringFunnel(StandardCharsets.UTF_8), 10_000_000, 0.001);
    private ExecutorService executor;
    private RuleSetLoader ruleSetLoader;

    public CRSEvaluator(RuleSetLoader ruleSetLoader) {
        this.ruleSetLoader = ruleSetLoader;
        this.executor = initExecutor();
        this.rules.addAll(ruleSetLoader.load());
        for (Rule rule : rules) {
            if (rule.regexString != null && !rule.regexString.isEmpty()) {
                this.compiledRules.add(Pattern.compile(rule.regexString));
            }
        }
    }

    private ExecutorService initExecutor() {
        int nThreads = Runtime.getRuntime().availableProcessors();
        return Executors.newFixedThreadPool(nThreads);
    }

    public boolean wasRequestAlreadyAnalysed(HttpResponseParams httpResponseParams) {

        // TODO: Optimize ? maybe we need to check for only the params that are not in
        // the bloom filter
        // Instead of the whole request.

        if (!benignRequestParamsBloomFilter.mightContain(httpResponseParams.getRequestParams().getURL())) {
            return false;
        }

        for (Map.Entry<String, List<String>> entry : httpResponseParams.getHeaders().entrySet()) {
            if (!benignRequestParamsBloomFilter.mightContain(entry.getKey())) {
                return false;
            }
            for (String value : entry.getValue()) {
                if (!benignRequestParamsBloomFilter.mightContain(value)) {
                    return false;
                }
            }
        }

        if (!benignRequestParamsBloomFilter.mightContain(httpResponseParams.getRequestParams().getPayload())) {
            return false;
        }
        return true;

    }

    private void addRequestToBloomFilter(HttpResponseParams httpResponseParams) {

        // Put URL into the bloom filter.
        // TODO: verify query params are included or not
        benignRequestParamsBloomFilter.put(httpResponseParams.getRequestParams().getURL());

        // Put header keys and values into the bloom filter
        for (Map.Entry<String, List<String>> entry : httpResponseParams.getHeaders().entrySet()) {
            benignRequestParamsBloomFilter.put(entry.getKey());
            for (String value : entry.getValue()) {
                benignRequestParamsBloomFilter.put(value);
            }
        }

        // TODO: Whole payload as string vs individual fields. What is better?
        benignRequestParamsBloomFilter.put(httpResponseParams.getRequestParams().getPayload());
    }

    public boolean evaluate(HttpResponseParams httpResponseParams) {

        if (wasRequestAlreadyAnalysed(httpResponseParams)) {
            return false;
        }

        String input = httpResponseParams.getOriginalMsg().get();

        for (int i = 0; i < compiledRules.size(); i++) {
            Pattern pattern = compiledRules.get(i);
            if (pattern.matcher(input).find()) {
                Rule rule = rules.get(i);
                System.out.println("Matched Rule ID: " + rule.id);
                System.out.println("Message: " + rule.message);
                System.out.println("Severity: " + rule.severity);
                return true;
            }
        }
        addRequestToBloomFilter(httpResponseParams);
        return false;
    }

    public static void main(String[] args) {
        RuleSetLoader loader = new RuleSetLoader("/RCE.conf");
        CRSEvaluator evaluator = new CRSEvaluator(loader);

        String test1 = "curl -X POST http://example.com -d 'id=1; rm -rf /'";
        String test2 = "GET /app?cmd=;ls %20-al HTTP/1.1";
        String test = hugeString();


        final long targetOps = 1_000;
        HttpResponseParams httpResponseParams = createHttpResponseParams();
        long start = System.nanoTime();
        for (int ops = 0; ops < targetOps; ops++) {
            boolean result = evaluator.evaluate(httpResponseParams);

        }
        long end = System.nanoTime();

        // Calculate metrics
        double durationMs = (end - start) / 1_000_000.0;
        double durationS = durationMs / 1000.0;
        double opsPerSec = targetOps / durationS;
        double avgTimePerOpUs = (durationMs * 1000.0) / targetOps;

        // Output results
        System.out.println("Java Benchmark Results:");
        System.out.printf("Total time for %d operations: %.2f ms%n", targetOps, durationMs);
        System.out.printf("Operations per second: %.2f%n", opsPerSec);
        System.out.printf("Average time per operation: %.2f us%n", avgTimePerOpUs);

    }

    public static HttpResponseParams createHttpResponseParams() {
        HttpResponseParams httpResponseParams = new HttpResponseParams();
        httpResponseParams.setOriginalMsg(hugeStringSupplier());
        httpResponseParams.setRequestParams(new HttpRequestParams(
                "https://portalapps.insperity.com/HSAEnrollment/contribution/new?CID=k7MKeZC9ac18A1lpkb7KlA",
                "POST",
                "HTTP/1.1",
                headerListMap(),
                hugeString(),
                123132));
        return httpResponseParams;
    }

    public static Map<String, List<String>> headerListMap() {
        Map<String, List<String>> headerListMap = new HashMap<>();
        headersMap().forEach((key, value) -> headerListMap.put(key, Collections.singletonList(value)));
        return headerListMap;
    }

    public static Map<String, String> headersMap() {
        Map<String, String> headers = new HashMap<>();
        headers.put("accept-encoding", "gzip, deflate, br, zstd");
        headers.put("accept-language", "en-US,en;q=0.9");
        headers.put("accept", "application/json, text/plain, */*");
        headers.put("adrum", "isAjax:true");
        headers.put("companyid", "k7MKeZC9ac18A1lpkb7KlA");
        headers.put("connection", "keep-alive");
        headers.put("content-length", "38285");
        headers.put("content-type", "application/json");
        // headers.put("cookie",
                // "_ga_GWHNRBN7C4=GS2.1.s1751293712$o1$g1$t1751297472$j60$l0$h841241629; _gid=GA1.2.573515384.1753017067; _ga=GA1.1.1643935729.1751998030; _ga_S0XMRY9KFM=GS2.1.s1753017067$o3$g1$t1753017084$j43$l0$h0; ESC5ReSync=True; ADRUM=s=1753101908565&r=https%3A%2F%2Fportal.insperity.com%2Fpcms%2Fbenefits%3F0; NSC_XE_ENA_QpsubmBqqt_efgbvmu_WJQ=ffffffff09f5144b45525d5f4f58455e445a4a42378b; FedAuth=77u/PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0idXRmLTgiPz48U2VjdXJpdHlDb250ZXh0VG9rZW4gcDE6SWQ9Il8yNmZlZGI1ZS1hOWE4LTRhMjMtODM2OS1kOTQ5MmRkMTBkODUtMDU4Nzk5QjBDODVEMzQ1QUYxREY3QUM3RkVCNEQ1NTciIHhtbG5zOnAxPSJodHRwOi8vZG9jcy5vYXNpcy1vcGVuLm9yZy93c3MvMjAwNC8wMS9vYXNpcy0yMDA0MDEtd3NzLXdzc2VjdXJpdHktdXRpbGl0eS0xLjAueHNkIiB4bWxucz0iaHR0cDovL2RvY3Mub2FzaXMtb3Blbi5vcmcvd3Mtc3gvd3Mtc2VjdXJlY29udmVyc2F0aW9uLzIwMDUxMiI+PElkZW50aWZpZXI+dXJuOnV1aWQ6OWQxMzg1NGYtZTJiNy00Y2ZmLTlhNGEtNjU5MDBlNTVlNjJjPC9JZGVudGlmaWVyPjxDb29raWUgeG1sbnM9Imh0dHA6Ly9zY2hlbWFzLm1pY3Jvc29mdC5jb20vd3MvMjAwNi8wNS9zZWN1cml0eSI+L1cxd1U4bndEOXpmaWYxdTZ4WUVxMDRUeHlURkVSbExSVkNuRWpmZjNHbGdoOUptRUo5WDR3alRvTmpiVFFaQ3NDTnpCUURTRVhBTko3NHh3MXFUOTdGaVV1VWRPNktDeTNUTXk0QWF4Y1RqdlJxRG5wbDZBNUJqNXdlYnZWUGNHcWdBV2hvdVhpa3BqRnhNZ2VCbjd6V0IvV1ZGbldNSndNQjVQMGhiMDFkUUtuRVZxRVErL0dFU1V5ekhyNU1tdm1TZGhXcVJDdHpKMDRYL3BRRFRiMFJOWlNWWjBZaHJNSHNUOG5EaG1xV3ozZ2lxUytoMEsrbU40S09WSXhocFdXbHpPYXl0bnJuWWI5THRveS9Za1ZxWVNEUm5RelNUMjRvZDlrbUY2Sm1RdjJTckI0MjMxWkxqeU5aNHU2SWxNQ1NrcE5UaW84V2lyT2piV01KNkpJUWRlM0lzN2I3UERidTc1S0NaS09QM3BqQmN1akV1b3JvdmZlKy93Y1ZZKzR5dlBRNlJDb0JYdFhadHhIcG50YzJmYlpPalk4RzJqNzFiTjEyQzJLTXlPL21hVGF0cTd5WTc1elhQRVBnREVicU5CSzRjSFhmaCtkOEF5MW1LRHBYZ0V6ZjJXRUg3MDV6anlDckFnT2xNdzFrSHZXQzVOUVh3ZDVqdVB0Wkt3RURRUVZwKzJPanNObm5XaHJVNWxJelk2Z0pPbFByQWNQcU1tVkxVQkxUOEhuWlZEc1hGZEVCd0FTdkYxM3FIZWJ4R1Z4a3pmOTJIdHRTRUJTeVN5Qlp0VldHU2FCK1l0dVEyZVZMZ05jcjE4RzdFUGFaTjl0eUhlUHZJdXFNNnAzODJmQ1ZMTEE4dUljc0dzZmtXUkRRQ0I1S3R1YUNKU3NpWTQ2UFV2eU8wRC9MdG0vTCt4cG5zYkwvQVRvOERLYStKbm1yK2dRRFJhUVVzMHNGVkZ4SzVKaDY0Q3VZbUhHZ2FxRHZPK211MHl1b081K0lPa1pmcjJZZWdoczBqOVpPVjd4TTdTWCtnR3FsL3FtOFZuc2U5cHdObzd5NDJOM3hjdzUzUzJIMjRjU0luZWVLeGlsNTVaMm14aDdKWnJGamJOUVF5ZUFBVFkzc2dKNytISFJvQS9CbGJQNlRNMWhrcEJ4eUVJRmF2KzJqcmFIR0V4WlhpVXdRS21NSG9OenVEVVdhYWovT0hYVW1PbDBteDV5NnlhWDY0L3JmY2NzSy9STGQxSTRHZy9TSnVMeWswNmNwYnpsN1loN3dvRzBXdStQYkVxMFg0TlEvbUh2dWZwQmk5MjQrRFZSV1ZiaGhHNnlhTFRBbTh2OHVRellWVHBCYkY1dmNsRkxRWHpSeW5tcWtBVWV2VFA4T2cxU0I5NTdjc2NOVE5pNUI4S1pN; FedAuth1=MUpPR3M0bmxXZUFCVGoxOXZtaXhTbGUvK3dTT2VFaTBpMG50M0dSRWczdjM1alBYZXRiQSttUWZSeEJ5UkNXMjljZ2F2SXpLWTJNVElNbmZaczlKc2E2R1c2NGVpWUw0ZzMrTnQwRngvUGJtSEFYZTRlRXBwYzErMlY3NG1URE03TTZ1MTBIVW13eU0rd0pDT3JNaVRKVWxLeTJNVzJPTGhHcUNnRFFjamN6b1VsN3FqNWtQbWlkLzJhNFFLQXdiWGZFS3hBaXA1djlLRytkNlNYUTkxaHlpWThZMTVWSDhwOTZQeTVzYWFWQVJlbmxDRnUwZ2VYRG5nLzJwV1ViOUN6cy82RXg0VkdxOGpHWmgvK3c4bUVmaktnZEZXVjh6ZmtLUHFDVTRPMWlCUkMvZDBWZDg4dHZLNm5lanpLLytDQVc2VkZKM1BHM3I1Y1BMT0VBZEdCSjlmV0lua2tMeXN6NzJ3V0pZREFKdFZobHhkb0lYTVBGMXJwWkFnbWpITEJRSk1ZZmhldG10aDwvQ29va2llPjwvU2VjdXJpdHlDb250ZXh0VG9rZW4+; SameSite=None; PCMSJwtBody=eyJhbGciOiJSUzI1NiIsImtpZCI6IjNCQ0Q3OTEzODc5Q0VBRDZDQkMwMzFDRDBBNkMwMDAxRDg1OEJDQjkiLCJ4NXQiOiJPODE1RTRlYzZ0Ykx3REhOQ213QUFkaFl2TGsiLCJ0eXAiOiJKV1QifQ.eyJNYXN0ZXJQZXJzb25JZCI6IjEyMjc5NDgyIiwiQWltc1BlcnNvbklkIjoiNDc3Mzc5MiIsIkFwcFNlY1VzZXJJZCI6IjI1NTE0NTYiLCJSb2xlcyI6IjIwMSwyMjMsMzQwLDM0MSwzNDQsMzQ1LDQzNyw0MzgsNDM5LDQ0MCw0NDMsNDQ0LDQ1OCw0NjAsNDYxLDQ2Miw0NjMsNTE1LDUxNiw1OTAsNjI0LDY0Myw2NzksNjgwLDY4MSw3MDgsNzUyLDc4NSIsIk1hcmtldFNlZ21lbnRzIjoiRW1lcmdpbmcgR3Jvd3RoICg1MCAtIDk5IEVFcykiLCJMYW5kaW5nUGFnZSI6IiIsIlByZWZlcnJlZEZpcnN0TmFtZSI6IktFTExJIiwiRmlyc3ROYW1lIjoiS0VMTEkiLCJMYXN0TmFtZSI6IkJPWUVSUyIsIklzQ29ycCI6IkZhbHNlIiwiQ29tcGFuaWVzIjoiNjEwMzUwMCIsIm5iZiI6MTc1MzEwMTkyMSwiZXhwIjoxNzUzMTAzNzIxLCJpYXQiOjE3NTMxMDE5MjEsImlzcyI6ImFwaS5wb3J0YWwuaW5zcGVyaXR5LmNvbS9hdXRoL3Rva2VuIiwiYXVkIjoiYXBpLnBvcnRhbC5pbnNwZXJpdHkuY29tIn0");
        headers.put("host", "portalapps.insperity.com");
        headers.put("origin", "https://portalapps.insperity.com");
        headers.put("referer",
                "https://portalapps.insperity.com/HSAEnrollment/contribution/new?CID=k7MKeZC9ac18A1lpkb7KlA");
        headers.put("sec-ch-ua-mobile", "?0");
        headers.put("sec-ch-ua-platform", "\"macOS\"");
        headers.put("sec-ch-ua", "\"Google Chrome\";v=\"137\", \"Chromium\";v=\"137\", \"Not/A)Brand\";v=\"24\"");
        headers.put("sec-fetch-dest", "empty");
        headers.put("sec-fetch-mode", "cors");
        headers.put("sec-fetch-site", "same-origin");
        headers.put("user-agent",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36");
        return headers;
    }

    public static Supplier<String> hugeStringSupplier() {
        return () -> hugeString();
    }

    public static String hugeString() {
        String t = "{\n" + //
                "  \"action\": \"Page_Load\",\n" + //
                "  \"auditGroupId\": \"ba072cda-08f5-4275-8ef6-c2591a0a1ab6\",\n" + //
                "  \"auditSequenceNumber\": 2,\n" + //
                "  \"html\": \"<!DOCTYPE html>\\n" + //
                "    <html>\\n" + //
                "    <head>\\n" + //
                "      <meta http-equiv=\\\"X-UA-Compatible\\\" content=\\\"IE=edge,chrome=1\\\"><meta http-equiv=\\\"Content-Type\\\" content=\\\"text/html; charset=utf-8\\\">\\n"
                + //
                "    <link href=\\\"/HSAEnrollment/bundles/css/angular?v=DIDUFIU5mpD6Ld-gNWisls9LADYzLJPLn5kqUsF0SV41\\\" rel=\\\"stylesheet\\\">\\n"
                + //
                "\\n" + //
                "    <base href=\\\"/HSAEnrollment\\\">\\n" + //
                "\\n" + //
                "\\n" + //
                "<meta name=\\\"viewport\\\" content=\\\"width=device-width, initial-scale=1.0\\\">\\n" + //
                "\\n" + //
                "<!-- Customised metadata used by ESC -->  \\n" + //
                "<!-- Indicate whether client side caching is enabled -->\\n" + //
                "<meta name=\\\"EnabledClientCache\\\" content=\\\"True\\\">\\n" + //
                "\\n" + //
                "<!--****************************************************************-->\\n" + //
                "<!-- Begin Header -->\\n" + //
                "<!--****************************************************************-->\\n" + //
                "\\n" + //
                "<link rel=\\\"icon\\\" href=\\\"https://cdn.portal.insperity.com/static/images/favicon.ico\\\" type=\\\"image/x-icon\\\">\\n"
                + //
                "\\n" + //
                "\\n" + //
                "\\n" + //
                "<link rel=\\\"stylesheet\\\" href=\\\"https://cdn.portal.insperity.com/static/css/nsp-standard.css\\\">\\n"
                + //
                "<link rel=\\\"stylesheet\\\" href=\\\"https://fonts.googleapis.com/icon?family=Material+Icons\\\">\\n"
                + //
                "<link href=\\\"https://fonts.googleapis.com/css?family=Noto+Sans:400,700,400italic,700italic\\\" rel=\\\"stylesheet\\\" type=\\\"text/css\\\">\\n"
                + //
                "\\n" + //
                "\\n" + //
                "\\n" + //
                "\\n" + //
                "\\n" + //
                "\\n" + //
                "<noscript>\\n" + //
                "    <iframe src=\\\"https://www.googletagmanager.com/ns.html?id=GTM-PRDTT6V\\\" height=\\\"0\\\" width=\\\"0\\\"\\n"
                + //
                "        style=\\\"display: none; visibility: hidden\\\"></iframe>\\n" + //
                "</noscript>\\n" + //
                "\\n" + //
                "\\n" + //
                "\\n" + //
                "<link rel=\\\"stylesheet\\\" href=\\\"https://cdn.portal.insperity.com/ngapps/premier-elements/styles.css\\\">\\n"
                + //
                "\\n" + //
                "<!-- ServerName - 100 -->\\n" + //
                "\\n" + //
                "\\n" + //
                "    \\n" + //
                "<title>HSA Enrollment</title><style></style><style type=\\\"text/css\\\"></style><style>#audit[_ngcontent-hsa-enrollment-app-c46]{background:#006595;color:#fff;width:3rem;height:3rem;border:none;border-radius:1.5rem;position:fixed;top:5rem;right:5rem}</style><style>.mat-icon{-webkit-user-select:none;user-select:none;background-repeat:no-repeat;display:inline-block;fill:currentColor;height:24px;width:24px}.mat-icon.mat-icon-inline{font-size:inherit;height:inherit;line-height:inherit;width:inherit}[dir=rtl] .mat-icon-rtl-mirror{transform:scale(-1, 1)}.mat-form-field:not(.mat-form-field-appearance-legacy) .mat-form-field-prefix .mat-icon,.mat-form-field:not(.mat-form-field-appearance-legacy) .mat-form-field-suffix .mat-icon{display:block}.mat-form-field:not(.mat-form-field-appearance-legacy) .mat-form-field-prefix .mat-icon-button .mat-icon,.mat-form-field:not(.mat-form-field-appearance-legacy) .mat-form-field-suffix .mat-icon-button .mat-icon{margin:auto}\\n"
                + //
                "</style><style>.mat-autocomplete-panel{text-align:left;cursor:default;border:1px solid #ccc;border-top:0;background-color:#fff;box-shadow:-1px 1px 3px #0000001a;position:absolute;z-index:9999;max-height:254px;overflow:hidden;overflow-y:auto;box-sizing:border-box}  .mat-option-text b{font-weight:400;color:#0072ce}.mat-option[_ngcontent-uuy-c28]{line-height:23px!important;height:23px!important;padding:0 .6em;font-size:13px}.mat-option[_ngcontent-uuy-c28]:hover, .mat-active[_ngcontent-uuy-c28]{background:#fafafa}</style><style>.mat-autocomplete-panel{min-width:112px;max-width:280px;overflow:auto;-webkit-overflow-scrolling:touch;visibility:hidden;max-width:none;max-height:256px;position:relative;width:100%;border-bottom-left-radius:4px;border-bottom-right-radius:4px}.mat-autocomplete-panel.mat-autocomplete-visible{visibility:visible}.mat-autocomplete-panel.mat-autocomplete-hidden{visibility:hidden}.mat-autocomplete-panel-above .mat-autocomplete-panel{border-radius:0;border-top-left-radius:4px;border-top-right-radius:4px}.mat-autocomplete-panel .mat-divider-horizontal{margin-top:-1px}.cdk-high-contrast-active .mat-autocomplete-panel{outline:solid 1px}mat-autocomplete{display:none}\\n"
                + //
                "</style><style type=\\\"text/css\\\" scoped=\\\"scoped\\\" class=\\\" pendo-style-3_g8XTCrgoanjOissFz-AD-iZFA\\\" style=\\\"white-space: pre-wrap;\\\"></style><style></style><style id=\\\"pendo-resource-center-bubble-animation\\\">@keyframes pulse { 0% { opacity: 1; transform: scale(1); } 100% { opacity: 0; transform: scale(1.6) } } .pendo-resource-center-badge-notification-bubble::before { content: \\\"\\\"; position: absolute; top: 0; left: 0; width: 100%; height: 100%; background-color: rgb(89, 203, 232); border-radius: 100%; z-index: -1; animation: pulse 2s infinite; will-change: transform; }</style><style>nsp-hsa-button-bar[_ngcontent-hsa-enrollment-app-c39]   button[_ngcontent-hsa-enrollment-app-c39]{margin-left:20px}</style><style>.material-icons.info[_ngcontent-hsa-enrollment-app-c35]{top:-.5em}@media screen and (max-width: 767px){table[_ngcontent-hsa-enrollment-app-c35]   thead[_ngcontent-hsa-enrollment-app-c35]   tr[_ngcontent-hsa-enrollment-app-c35]   th[_ngcontent-hsa-enrollment-app-c35]:nth-child(1){width:20%}table[_ngcontent-hsa-enrollment-app-c35]   thead[_ngcontent-hsa-enrollment-app-c35]   tr[_ngcontent-hsa-enrollment-app-c35]   th[_ngcontent-hsa-enrollment-app-c35]:nth-child(2){width:20%}table[_ngcontent-hsa-enrollment-app-c35]   thead[_ngcontent-hsa-enrollment-app-c35]   tr[_ngcontent-hsa-enrollment-app-c35]   th[_ngcontent-hsa-enrollment-app-c35]:nth-child(3){width:60%}table.editing[_ngcontent-hsa-enrollment-app-c35]   thead[_ngcontent-hsa-enrollment-app-c35]   tr[_ngcontent-hsa-enrollment-app-c35]   th[_ngcontent-hsa-enrollment-app-c35]:nth-child(1){width:20%}table.editing[_ngcontent-hsa-enrollment-app-c35]   thead[_ngcontent-hsa-enrollment-app-c35]   tr[_ngcontent-hsa-enrollment-app-c35]   th[_ngcontent-hsa-enrollment-app-c35]:nth-child(2){width:15%}table.editing[_ngcontent-hsa-enrollment-app-c35]   thead[_ngcontent-hsa-enrollment-app-c35]   tr[_ngcontent-hsa-enrollment-app-c35]   th[_ngcontent-hsa-enrollment-app-c35]:nth-child(3){width:10%}table.editing[_ngcontent-hsa-enrollment-app-c35]   thead[_ngcontent-hsa-enrollment-app-c35]   tr[_ngcontent-hsa-enrollment-app-c35]   th[_ngcontent-hsa-enrollment-app-c35]:nth-child(4){width:55%}}@media screen and (min-width: 768px) and (max-width: 991px){table[_ngcontent-hsa-enrollment-app-c35]   thead[_ngcontent-hsa-enrollment-app-c35]   tr[_ngcontent-hsa-enrollment-app-c35]   th[_ngcontent-hsa-enrollment-app-c35]:nth-child(1){width:20%}table[_ngcontent-hsa-enrollment-app-c35]   thead[_ngcontent-hsa-enrollment-app-c35]   tr[_ngcontent-hsa-enrollment-app-c35]   th[_ngcontent-hsa-enrollment-app-c35]:nth-child(2){width:20%}table[_ngcontent-hsa-enrollment-app-c35]   thead[_ngcontent-hsa-enrollment-app-c35]   tr[_ngcontent-hsa-enrollment-app-c35]   th[_ngcontent-hsa-enrollment-app-c35]:nth-child(3){width:60%}table.editing[_ngcontent-hsa-enrollment-app-c35]   thead[_ngcontent-hsa-enrollment-app-c35]   tr[_ngcontent-hsa-enrollment-app-c35]   th[_ngcontent-hsa-enrollment-app-c35]:nth-child(1){width:20%}table.editing[_ngcontent-hsa-enrollment-app-c35]   thead[_ngcontent-hsa-enrollment-app-c35]   tr[_ngcontent-hsa-enrollment-app-c35]   th[_ngcontent-hsa-enrollment-app-c35]:nth-child(2){width:15%}table.editing[_ngcontent-hsa-enrollment-app-c35]   thead[_ngcontent-hsa-enrollment-app-c35]   tr[_ngcontent-hsa-enrollment-app-c35]   th[_ngcontent-hsa-enrollment-app-c35]:nth-child(3){width:10%}table.editing[_ngcontent-hsa-enrollment-app-c35]   thead[_ngcontent-hsa-enrollment-app-c35]   tr[_ngcontent-hsa-enrollment-app-c35]   th[_ngcontent-hsa-enrollment-app-c35]:nth-child(4){width:55%}}@media screen and (min-width: 992px) and (max-width: 1279px){table[_ngcontent-hsa-enrollment-app-c35]   thead[_ngcontent-hsa-enrollment-app-c35]   tr[_ngcontent-hsa-enrollment-app-c35]   th[_ngcontent-hsa-enrollment-app-c35]:nth-child(1){width:38%}table[_ngcontent-hsa-enrollment-app-c35]   thead[_ngcontent-hsa-enrollment-app-c35]   tr[_ngcontent-hsa-enrollment-app-c35]   th[_ngcontent-hsa-enrollment-app-c35]:nth-child(2){width:32%}table[_ngcontent-hsa-enrollment-app-c35]   thead[_ngcontent-hsa-enrollment-app-c35]   tr[_ngcontent-hsa-enrollment-app-c35]   th[_ngcontent-hsa-enrollment-app-c35]:nth-child(3){width:30%}table.editing[_ngcontent-hsa-enrollment-app-c35]   thead[_ngcontent-hsa-enrollment-app-c35]   tr[_ngcontent-hsa-enrollment-app-c35]   th[_ngcontent-hsa-enrollment-app-c35]:nth-child(1){width:38%}table.editing[_ngcontent-hsa-enrollment-app-c35]   thead[_ngcontent-hsa-enrollment-app-c35]   tr[_ngcontent-hsa-enrollment-app-c35]   th[_ngcontent-hsa-enrollment-app-c35]:nth-child(2){width:26%}table.editing[_ngcontent-hsa-enrollment-app-c35]   thead[_ngcontent-hsa-enrollment-app-c35]   tr[_ngcontent-hsa-enrollment-app-c35]   th[_ngcontent-hsa-enrollment-app-c35]:nth-child(3){width:16%}table.editing[_ngcontent-hsa-enrollment-app-c35]   thead[_ngcontent-hsa-enrollment-app-c35]   tr[_ngcontent-hsa-enrollment-app-c35]   th[_ngcontent-hsa-enrollment-app-c35]:nth-child(4){width:20%}}@media screen and (min-width: 1280px) and (max-width: 1919){table[_ngcontent-hsa-enrollment-app-c35]   thead[_ngcontent-hsa-enrollment-app-c35]   tr[_ngcontent-hsa-enrollment-app-c35]   th[_ngcontent-hsa-enrollment-app-c35]:nth-child(1){width:25%}table[_ngcontent-hsa-enrollment-app-c35]   thead[_ngcontent-hsa-enrollment-app-c35]   tr[_ngcontent-hsa-enrollment-app-c35]   th[_ngcontent-hsa-enrollment-app-c35]:nth-child(2){width:25%}table[_ngcontent-hsa-enrollment-app-c35]   thead[_ngcontent-hsa-enrollment-app-c35]   tr[_ngcontent-hsa-enrollment-app-c35]   th[_ngcontent-hsa-enrollment-app-c35]:nth-child(3){width:50%}table.editing[_ngcontent-hsa-enrollment-app-c35]   thead[_ngcontent-hsa-enrollment-app-c35]   tr[_ngcontent-hsa-enrollment-app-c35]   th[_ngcontent-hsa-enrollment-app-c35]:nth-child(1){width:25%}table.editing[_ngcontent-hsa-enrollment-app-c35]   thead[_ngcontent-hsa-enrollment-app-c35]   tr[_ngcontent-hsa-enrollment-app-c35]   th[_ngcontent-hsa-enrollment-app-c35]:nth-child(2){width:25%}table.editing[_ngcontent-hsa-enrollment-app-c35]   thead[_ngcontent-hsa-enrollment-app-c35]   tr[_ngcontent-hsa-enrollment-app-c35]   th[_ngcontent-hsa-enrollment-app-c35]:nth-child(3){width:10%}table.editing[_ngcontent-hsa-enrollment-app-c35]   thead[_ngcontent-hsa-enrollment-app-c35]   tr[_ngcontent-hsa-enrollment-app-c35]   th[_ngcontent-hsa-enrollment-app-c35]:nth-child(4){width:40%}}@media screen and (min-width: 1920px){table[_ngcontent-hsa-enrollment-app-c35]   thead[_ngcontent-hsa-enrollment-app-c35]   tr[_ngcontent-hsa-enrollment-app-c35]   th[_ngcontent-hsa-enrollment-app-c35]:nth-child(1){width:18%}table[_ngcontent-hsa-enrollment-app-c35]   thead[_ngcontent-hsa-enrollment-app-c35]   tr[_ngcontent-hsa-enrollment-app-c35]   th[_ngcontent-hsa-enrollment-app-c35]:nth-child(2){width:18%}table[_ngcontent-hsa-enrollment-app-c35]   thead[_ngcontent-hsa-enrollment-app-c35]   tr[_ngcontent-hsa-enrollment-app-c35]   th[_ngcontent-hsa-enrollment-app-c35]:nth-child(3){width:64%}table.editing[_ngcontent-hsa-enrollment-app-c35]   thead[_ngcontent-hsa-enrollment-app-c35]   tr[_ngcontent-hsa-enrollment-app-c35]   th[_ngcontent-hsa-enrollment-app-c35]:nth-child(1){width:18%}table.editing[_ngcontent-hsa-enrollment-app-c35]   thead[_ngcontent-hsa-enrollment-app-c35]   tr[_ngcontent-hsa-enrollment-app-c35]   th[_ngcontent-hsa-enrollment-app-c35]:nth-child(2){width:18%}table.editing[_ngcontent-hsa-enrollment-app-c35]   thead[_ngcontent-hsa-enrollment-app-c35]   tr[_ngcontent-hsa-enrollment-app-c35]   th[_ngcontent-hsa-enrollment-app-c35]:nth-child(3){width:10%}table.editing[_ngcontent-hsa-enrollment-app-c35]   thead[_ngcontent-hsa-enrollment-app-c35]   tr[_ngcontent-hsa-enrollment-app-c35]   th[_ngcontent-hsa-enrollment-app-c35]:nth-child(4){width:54%}}</style>\\n"
                + //
                "    </head>\\n" + //
                "    <body>\\n" + //
                "      \\n" + //
                "<div class=\\\"nsp-layout has-side-nav\\\" id=\\\"escMasterPageDiv\\\">\\n" + //
                "    \\n" + //
                "\\n" + //
                "<x-nsp-premier-header _nghost-uuy-c30=\\\"\\\" ng-version=\\\"13.3.3\\\"><header _ngcontent-uuy-c30=\\\"\\\" class=\\\"nsp-main-header\\\"><ngapps-nsp-ngapps-nsp-premier-header-hamburger _ngcontent-uuy-c30=\\\"\\\" _nghost-uuy-c9=\\\"\\\"><span _ngcontent-uuy-c9=\\\"\\\" id=\\\"header.menu.toggle\\\" class=\\\"toggle-side-nav\\\"><i _ngcontent-uuy-c9=\\\"\\\" class=\\\"material-icons md-24\\\">î—’</i></span></ngapps-nsp-ngapps-nsp-premier-header-hamburger><ul _ngcontent-uuy-c30=\\\"\\\" id=\\\"header.tools\\\" class=\\\"header-tools\\\"><li _ngcontent-uuy-c30=\\\"\\\" class=\\\"nsp-hidden-lg nsp-hidden-md\\\"><a _ngcontent-uuy-c30=\\\"\\\"><mat-icon _ngcontent-uuy-c30=\\\"\\\" role=\\\"img\\\" class=\\\"mat-icon notranslate material-icons mat-icon-no-color\\\" aria-hidden=\\\"true\\\" data-mat-icon-type=\\\"font\\\">home</mat-icon></a></li><li _ngcontent-uuy-c30=\\\"\\\" id=\\\"announcementIndicator\\\" class=\\\"mr-10\\\"><a _ngcontent-uuy-c30=\\\"\\\" ngapps-nsp-link-element=\\\"\\\" target=\\\"_self\\\" href=\\\"https://portal.insperity.com/pcms/announcements_and_news\\\"><i _ngcontent-uuy-c30=\\\"\\\" class=\\\"material-icons md-18\\\">î‚¾</i><span _ngcontent-uuy-c30=\\\"\\\" class=\\\"announcements-indicator\\\">3</span><!----></a></li><ngapps-nsp-ngapps-nsp-premier-header-user-menu _ngcontent-uuy-c30=\\\"\\\" _nghost-uuy-c15=\\\"\\\"><li _ngcontent-uuy-c15=\\\"\\\" id=\\\"user\\\" class=\\\"nsp-menu click\\\"><span _ngcontent-uuy-c15=\\\"\\\" class=\\\"nsp-hidden-xs\\\">KELLI BOYERS</span><!----><i _ngcontent-uuy-c15=\\\"\\\" class=\\\"material-icons nsp-block-xs hidden\\\">îŸ½</i><ul _ngcontent-uuy-c15=\\\"\\\" id=\\\"header.tools.menu\\\"><li _ngcontent-uuy-c15=\\\"\\\"><a _ngcontent-uuy-c15=\\\"\\\" ngapps-nsp-link-element=\\\"\\\" class=\\\"load-view\\\" target=\\\"_self\\\" href=\\\"https://portalapps.insperity.com/MyProfile\\\"><i _ngcontent-uuy-c15=\\\"\\\" class=\\\"material-icons md-21\\\">î¡‘</i>My Profile</a></li><li _ngcontent-uuy-c15=\\\"\\\"><a _ngcontent-uuy-c15=\\\"\\\" ngapps-nsp-link-element=\\\"\\\" class=\\\"load-view\\\" target=\\\"_self\\\" href=\\\"https://portalapps.insperity.com/MyProfile/Settings\\\"><i _ngcontent-uuy-c15=\\\"\\\" class=\\\"material-icons md-21\\\">î¢¸</i>Settings</a></li><li _ngcontent-uuy-c15=\\\"\\\"><a _ngcontent-uuy-c15=\\\"\\\" ngapps-nsp-link-element=\\\"\\\" class=\\\"load-view\\\" target=\\\"_self\\\" href=\\\"https://portalapps.insperity.com/MyProfile/ManagePassword\\\"><i _ngcontent-uuy-c15=\\\"\\\" class=\\\"material-icons md-21\\\">î¢™</i>Manage Password</a></li><li _ngcontent-uuy-c15=\\\"\\\"><a _ngcontent-uuy-c15=\\\"\\\" ngapps-nsp-link-element=\\\"\\\" class=\\\"load-view\\\" target=\\\"_self\\\" href=\\\"https://portal.insperity.com/PremierChat/\\\"><i _ngcontent=uuy-c15=\\\"\\\" class=\\\"material-icons md-21\\\">î‚·</i>Need to Chat?</a></li><li _ngcontent-uuy-c15=\\\"\\\"><a _ngcontent=uuy-c15=\\\"\\\" ngapps-nsp-link-element=\\\"\\\" class=\\\"load-view\\\" target=\\\"_self\\\" href=\\\"https://portal.insperity.com/logout\\\"><i _ngcontent=uuy-c15=\\\"\\\" class=\\\"material-icons md-21\\\">î¡¹</i>Logout</a></li><!----></ul><!----></li></ngapps-nsp-ngapps-nsp-premier-header-user-menu><!----></ul><!----><!----><ngapps-nsp-premier-header-logo _ngcontent-uuy-c30=\\\"\\\" _nghost-uuy-c16=\\\"\\\"><div _ngcontent-uuy-c16=\\\"\\\" class=\\\"nsp-logo\\\"><div _ngcontent-uuy-c16=\\\"\\\" class=\\\"nsp-hidden-xs\\\"><a _ngcontent-uuy-c16=\\\"\\\"><img _ngcontent-uuy-c16=\\\"\\\" src=\\\"https://portal.insperity.com/static/images/logo.png\\\"></a></div></div></ngapps-nsp-premier-header-logo><ngapps-nsp-ngapps-nsp-premier-header-search-box _ngcontent-uuy-c30=\\\"\\\" _nghost-uuy-c28=\\\"\\\"><div _ngcontent-uuy-c28=\\\"\\\" class=\\\"search\\\"><div _ngcontent-uuy-c28=\\\"\\\" class=\\\"nsp-input\\\"><form _ngcontent-uuy-c28=\\\"\\\" method=\\\"GET\\\" ngnoform=\\\"\\\" id=\\\"header.search.box\\\" action=\\\"https://portal.insperity.com/pcms/search\\\"><input _ngcontent-uuy-c28=\\\"\\\" type=\\\"text\\\" placeholder=\\\"Search...\\\" name=\\\"q\\\" required=\\\"\\\" autocomplete=\\\"off\\\" matinput=\\\"\\\" class=\\\"mat-autocomplete-trigger ng-untouched ng-pristine ng-invalid\\\" role=\\\"combobox\\\" aria-autocomplete=\\\"list\\\" aria-expanded=\\\"false\\\" aria-haspopup=\\\"listbox\\\" value=\\\"\\\"><!----><i _ngcontent-uuy-c28=\\\"\\\" class=\\\"material-icons md-18 toggle-search\\\">î¢¶</i><input _ngcontent=uuy-c28=\\\"\\\" type=\\\"submit\\\" value=\\\"\\\"><mat-autocomplete _ngcontent-uuy-c28=\\\"\\\" class=\\\"mat-autocomplete\\\"><!----></mat-autocomplete></form></div></div></ngapps-nsp-ngapps-nsp-premier-header-search-box><!----></header><!----><!----><!----></x-nsp-premier-header>\\n"
                + //
                "<x-nsp-premier-left-nav _nghost-uuy-c31=\\\"\\\" ng-version=\\\"13.3.3\\\"><nav _ngcontent-uuy-c31=\\\"\\\" class=\\\"nsp-main-side-nav\\\"><div _ngcontent-uuy-c31=\\\"\\\" class=\\\"nsp-nav-container\\\"><div _ngcontent-uuy-c31=\\\"\\\" class=\\\"coBrand\\\" style=\\\"width: 165px; display: block; text-align: center;\\\"><img _ngcontent=uuy-c31=\\\"\\\" border=\\\"0\\\" src=\\\"data:image/gif;base64,R0lGODlhpQA9APcAAOr07cTk/y2a/xlEYQAkRtrh5jGc/4PE/wAoSgo0VOnu8ePy//b4+WK0/0xrghM9W8HM1HGLnpPDomB/k4e8mNPr//j5+9Ln2Dhdd3yUpZuuuzxhebLByzVadGSoekGUXbvI0bnYw02q/+n1/2mFmLLb/0Ol/97l6YifrgAmSFl5jsbR2EdqgSiY/yJMaLnf/1Wt/118kG2JmwAxUe7x9PT3+KGzv225/05whv3+/gAuTvP59aLT/2aBlbbEzeLo7KW2wiNJZQAiRI3J/3iSoxVAXh1tsSaX/8ri0YadrBtdk0qZZEWXYNzs4YGZqa3Z/+Hm6pKmtCqZ/3SOoMzW3A87WgAvUOTq7TFXcvH6/7nH0FR1i1Keaz2i/4yisXq1jb7h/6i5xAs4V5Opt628x1Fyiezw8vv8/fb8/56xvc7p/2KBlNHa4AArTKjW/9ju/5Kise728XKwhilRbEFmfdXd4wAsTUZlffH09qm4wyhOagU0VL7byLXDzUiYYrHUvIqgr1p3jAAaPV57kFN0ieHu5USWXn3B/5HL/wAdQJequDRTbrbXwbK+yaa3w5rGqP7//47AnlZ2jFujciBFYmmDlsnU2/z9/ni//2ysgebs70Nof0BjewcxUsvV3DWg/wAsTi5Wcfj9//n6+/P2919+kn22kG2Gmc7Y3jqh/3GvhcTezLjGz2aEl2i3/x1HZIGWp0diegUuTwAgQwAqRwIvTwIsTUybZ/P1902bZ0ybZl59kU2bZkyaZkCTW0CUWz+TWvv+/Dil/66+yPDz9Tif/yua//T7/7PCzMvn/+z3/12x/83U23CFmH+YqAAwT9jf5TKZ9WeFmJ3R/zhVcC+b/0aXYe74/+Ho68fh0IrI/3eRon6Xp0io/93w/wQxUwY0VEuaZTlfd57KrPf8+UJedwc+Z1BuhXuTpHqUpSeX/3ezipO21Ku8x4Oaq8jT2sTQ1y+T7AwvUGaBkfr+/xZDYF95joiZqYicrC6b/2h/k5CkstPc4QAwUDSe/////yH/C1hNUCBEYXRhWE1QPD94cGFja2V0IGJlZ2luPSLvu78iIGlkPSJXNU0wTXBDZWhpSHpyZVN6TlRjemtjOWQiPz4gPHg6eG1wbWV0YSB4bWxuczp4PSJhZG9iZTpuczptZXRhLyIgeDp4bXB0az0iQWRvYmUgWE1QIENvcmUgOS4wLWMwMDAgNzkuMTcxYzI3ZmFiLCAyMDIyLzA4LzE2LTIyOjM1OjQxICAgICAgICAiPiA8cmRmOlJERiB4bWxuczpyZGY9Imh0dHA6Ly93d3cudzMub3JnLzE5OTkvMDIvMjItcmRmLXN5bnRheC1ucyMiPiA8cmRmOkRlc2NyaXB0aW9uIHJkZjphYm91dD0iIiB4bWxuczp4bXBNTT0iaHR0cDovL25zLmFkb2JlLmNvbS94YXAvMS4wL21tLyIgeG1sbnM6c3RSZWY9Imh0dHA6Ly9ucy5hZG9iZS5jb20veGFwLzEuMC9zVHlwZS9SZXNvdXJjZVJlZiMiIHhtbG5zOnhtcD0iaHR0cDovL25zLmFkb2JlLmNvbS94YXAvMS4wLyIgeG1zbnM6ZGM9Imh0dHA6Ly9wdXJsLm9yZy9kYy9lbGVtZW50cy8xLjEvIiB4bXBNTTpPcmlnaW5hbERvY3VtZW50SUQ9InV1aWQ6NUQyMDg5MjQ5M0JGREIxMTkxNEE4NTkwRDMxNTA4QzgiIHhtcE1NOkRvY3VtZW50SUQ9InhtcC5kaWQ6QjhERjBGNDJCRUUxMTFFRkJFRDZFMjZFRjg3QTAyQkEiIHhtcE1NOkluc3RhbmNlSUQ9InhtcC5paWQ6QjhERjBGNDFCRUUxMTFFRkJFRDZFMjZFRjg3QTAyQkEiIHhtcDpDcmVhdG9yVG9vbD0iQWRvYmUgSWxsdXN0cmF0b3IgMjguMyAoTWFjaW50b3NoKSI+IDx4bXBNTTpEZXJpdmVkRnJvbSBzdFJlZjppbnN0YW5jZUlEPSJ4bXAuaWlkOjNiMjQyNTY1LWI0YzAtNGI0MC05MGE4LTUyMmRiZDkxNjYzZiIgc3RSZWY6ZG9jdW1lbnRJRD0ieG1wLmRpZDoyMDcxMGEzYi1hYzNiLTRlY2UtOThlMy0xZTI4ZTFiMWI5YjMiLz4gPGRjOnRpdGxlPiA8cmRmOkFsdD4gPHJkZjpsaSB4bWw6bGFuZz0ieC1kZWZhdWx0Ij5QcmludDwvcmRmOmxpPiA8L3JkZjpBbHQ+ IDwvZGM6dGl0bGU+ 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\\\"></div><!----><!----><!----><ul _ngcontent-uuy-c31=\\\"\\\" class=\\\"nsp-standard-list toggle-nav-list\\\"><li _ngcontent-uuy-c31=\\\"\\\" class=\\\"active\\\" id=\\\"20241014100841831\\\"><a _ngcontent-uuy-c31=\\\"\\\" ngapps-nsp-link-element=\\\"\\\" class=\\\"tier-one\\\" target=\\\"_self\\\" href=\\\"\\\" style=\\\"cursor: default;\\\"><i _ngcontent-uuy-c31=\\\"\\\" class=\\\"material-icons\\\">îŸ½</i> My Account </a><ul _ngcontent-uuy-c31=\\\"\\\" class=\\\"nsp-standard-list sub-navfix\\\"><li _ngcontent-uuy-c31=\\\"\\\" id=\\\"20230705094757091\\\"><a _ngcontent-uuy-c31=\\\"\\\" ngapps-nsp-link-element=\\\"\\\" target=\\\"_self\\\" href=\\\"https://portal.insperity.com/pcms/paycheck_information\\\"> Paycheck </a></li><li _ngcontent-uuy-c31=\\\"\\\" id=\\\"1401726943923\\\"><a _ngcontent-uuy-c31=\\\"\\\" ngapps-nsp-link-element=\\\"\\\" target=\\\"_self\\\" href=\\\"https://portal.insperity.com/pcms/benefits\\\"> Benefits </a></li><li _ngcontent-uuy-c31=\\\"\\\" id=\\\"1428419280515\\\"><a _ngcontent-uuy-c31=\\\"\\\" ngapps-nsp-link-element=\\\"\\\" target=\\\"_self\\\" href=\\\"https://portal.insperity.com/pcms/401k_retirement_services\\\"> 401(k) </a></li><li _ngcontent-uuy-c31=\\\"\\\" id=\\\"1428419469395\\\"><a _ngcontent-uuy-c31=\\\"\\\" ngapps-nsp-link-element=\\\"\\\" target=\\\"_self\\\" href=\\\"https://portalapps.insperity.com/PTO/PtoRedirect.aspx?link=Employee\\\"> Time and Attendance </a></li><li _ngcontent-uuy-c31=\\\"\\\" id=\\\"20240520161126498\\\"><a _ngcontent-uuy-c31=\\\"\\\" ngapps-nsp-link-element=\\\"\\\" target=\\\"_self\\\" href=\\\"https://portal.insperity.com/pcms/training_information\\\"> Training </a></li><li _ngcontent-uuy-c31=\\\"\\\" id=\\\"1428434165406\\\"><a _ngcontent-uuy-c31=\\\"\\\" ngapps-nsp-link-element=\\\"\\\" target=\\\"_self\\\" href=\\\"https://portal.insperity.com/pcms/wellbeing_on_demand\\\"> Wellbeing </a></li><!----></ul></li></ul><hr _ngcontent-uuy-c31=\\\"\\\"><!----><!----><ul _ngcontent-uuy-c31=\\\"\\\" class=\\\"nsp-standard-list toggle-nav-list\\\"><li _ngcontent-uuy-c31=\\\"\\\" class=\\\"active\\\" id=\\\"1428421742283\\\"><a _ngcontent-uuy-c31=\\\"\\\" ngapps-nsp-link-element=\\\"\\\" class=\\\"tier-one\\\" target=\\\"_self\\\" href=\\\"\\\" style=\\\"cursor: default;\\\"><i _ngcontent-uuy-c31=\\\"\\\" class=\\\"material-icons\\\">î‹‰</i> Company </a><ul _ngcontent-uuy-c31=\\\"\\\" class=\\\"nsp-standard-list sub-navfix\\\"><li _ngcontent-uuy-c31=\\\"\\\" id=\\\"1428427086447\\\"><a _ngcontent-uuy-c31=\\\"\\\" ngapps-nsp-link-element=\\\"\\\" target=\\\"_self\\\" href=\\\"https://portalapps.insperity.com/EmployeeDirectory/EmpDirectory.aspx\\\"> Employee Directory </a></li><li _ngcontent-uuy-c31=\\\"\\\" id=\\\"1428437085009\\\"><a _ngcontent-uuy-c31=\\\"\\\" ngapps-nsp-link-element=\\\"\\\" target=\\\"_self\\\" href=\\\"https://portalapps.insperity.com/ngapps/fileshare/my-files\\\"> File Share </a></li><li _ngcontent-uuy-c31=\\\"\\\" id=\\\"1428419425833\\\"><a _ngcontent-uuy-c31=\\\"\\\" ngapps-nsp-link-element=\\\"\\\" target=\\\"_self\\\" href=\\\"https://portal.insperity.com/pcms/regulatory_and_compliance\\\"> Regulatory and Compliance  </a></li><li _ngcontent-uuy-c31=\\\"\\\" id=\\\"1428430815144\\\"><a _ngcontent-uuy-c31=\\\"\\\" ngapps-nsp-link-element=\\\"\\\" target=\\\"_self\\\" href=\\\"https://portal.insperity.com/pcms/forms_and_policies\\\"> Forms and Policies </a></li><li _ngcontent-uuy-c31=\\\"\\\" id=\\\"1428431970156\\\"><a _ngcontent-uuy-c31=\\\"\\\" ngapps-nsp-link-element=\\\"\\\" target=\\\"_blank\\\" href=\\\"https://portalapps.insperity.com/3rdPartyMarketPlaceSSO/MarketPlaceSamlPost.aspx \\\"> MarketPlace </a></li><!----></ul></li></ul><!----><!----><!----><!----><!----></div></nav></x-nsp-premier-left-nav>\\n"
                + //
                "\\n" + //
                "\\n" + //
                "\\n" + //
                "\\n" + //
                "    \\n" + //
                "    <main>\\n" + //
                "        <nsp-root _nghost-hsa-enrollment-app-c46=\\\"\\\" ng-version=\\\"13.3.3\\\"><router-outlet _ngcontent-hsa-enrollment-app-c46=\\\"\\\"></router-outlet><nsp-hsa-new _nghost-hsa-enrollment-app-c43=\\\"\\\"><nsp-hsa-overview _ngcontent-hsa-enrollment-app-c43=\\\"\\\" title=\\\"Open a Health Savings Account (HSA)\\\" _nghost-hsa-enrollment-app-c42=\\\"\\\"><div _ngcontent-hsa-enrollment-app-c42=\\\"\\\" class=\\\"nsp-module\\\"><div _ngcontent-hsa-enrollment-app-c42=\\\"\\\" class=\\\"row\\\"><div _ngcontent-hsa-enrollment-app-c42=\\\"\\\" class=\\\"nsp-col-12\\\"><h1 _ngcontent-hsa-enrollment-app-c42=\\\"\\\" class=\\\"nsp-module-title\\\">Open a Health Savings Account (HSA)</h1></div></div><div _ngcontent-hsa-enrollment-app-c43=\\\"\\\" class=\\\"row\\\"><div _ngcontent-hsa-enrollment-app-c43=\\\"\\\" class=\\\"nsp-col-12\\\"><p _ngcontent-hsa-enrollment-app-c43=\\\"\\\"> Based on your current enrollment in an Insperity HDHP coverage option, you are eligible to open and contribute to an <span _ngcontent-hsa-enrollment-app-c43=\\\"\\\" class=\\\"text-link\\\">Optum Bank HSA</span> by payroll deduction. If you choose to make a contribution by payroll, you can change it at any time. </p><!----><p _ngcontent-hsa-enrollment-app-c43=\\\"\\\"> You will need internet access and a valid email address to manage your Optum Bank HSA. Please review Optum Bank's <span _ngcontent-hsa-enrollment-app-c43=\\\"\\\" class=\\\"text-link\\\">hardware and software requirements</span>. </p><p _ngcontent-hsa-enrollment-app-c43=\\\"\\\">If you need help deciding how much to contribute, you can use <span _ngcontent-hsa-enrollment-app-c43=\\\"\\\" class=\\\"text-link\\\">Optum Bank's calculator</span>.</p></div></div></div></nsp-hsa-overview><nsp-hsa-contribution _ngcontent-hsa-enrollment-app-c43=\\\"\\\" _nghost-hsa-enrollment-app-c39=\\\"\\\"><div _ngcontent-hsa-enrollment-app-c39=\\\"\\\" class=\\\"row\\\"><div _ngcontent-hsa-enrollment-app-c39=\\\"\\\" class=\\\"nsp-col-12 nsp-expand-md\\\"><div _ngcontent-hsa-enrollment-app-c39=\\\"\\\" class=\\\"nsp-module\\\"><nsp-hsa-contribution-status _ngcontent-hsa-enrollment-app-c39=\\\"\\\" _nghost-hsa-enrollment-app-c38=\\\"\\\"><nsp-hsa-contribution-status-new _ngngcontent-hsa-enrollment-app-c38=\\\"\\\" _nghost-hsa-enrollment-app-c36=\\\"\\\"><!----><!----><div _ngcontent-hsa-enrollment-app-c36=\\\"\\\" class=\\\"row\\\"><div _ngcontent-hsa-enrollment-app-c36=\\\"\\\" class=\\\"nsp-col-12\\\"><div _ngcontent-hsa-enrollment-app-c36=\\\"\\\" class=\\\"nsp-input inline radio mr-50\\\"><input _ngcontent-hsa-enrollment-app-c36=\\\"\\\" type=\\\"radio\\\" id=\\\"employeeContribute\\\" class=\\\"ng-untouched ng-dirty ng-valid\\\"><label _ngcontent-hsa-enrollment-app-c36=\\\"\\\" for=\\\"employeeContribute\\\">I want to contribute and receive the employer contribution</label></div><div _ngcontent-hsa-enrollment-app-c36=\\\"\\\" class=\\\"nsp-input inline radio ml-50\\\"><input _ngcontent-hsa-enrollment-app-c36=\\\"\\\" type=\\\"radio\\\" id=\\\"companyContribute\\\" class=\\\"ng-untouched ng-dirty ng-valid\\\" checked=\\\"checked\\\"><label _ngcontent-hsa-enrollment-app-c36=\\\"\\\" for=\\\"companyContribute\\\" style=\\\"margin-right: 0px;\\\">I only want the employer contribution</label></div><a _ngcontent-hsa-enrollment-app-c36=\\\"\\\" mattooltipposition=\\\"right\\\" class=\\\"mat-tooltip-trigger\\\" aria-describedby=\\\"cdk-describedby-message-1\\\" cdk-describedby-host=\\\"0\\\"><i _ngcontent-hsa-enrollment-app-c36=\\\"\\\" class=\\\"material-icons md-18 info\\\">info</i></a><!----></div></div><!----></nsp-hsa-contribution-status-new><!----><!----><!----><!----><!----><!----></nsp-hsa-contribution-status><nsp-hsa-contribution-table _ngcontent-hsa-enrollment-app-c39=\\\"\\\" _nghost-hsa-enrollment-app-c35=\\\"\\\"><form _ngcontent-hsa-enrollment-app-c35=\\\"\\\" novalidate=\\\"\\\" class=\\\"ng-untouched ng-pristine ng-valid\\\"><table _ngcontent-hsa-enrollment-app-c35=\\\"\\\" class=\\\"nsp-table-material\\\"><thead _ngcontent-hsa-enrollment-app-c35=\\\"\\\"><tr _ngcontent-hsa-enrollment-app-c35=\\\"\\\"><th _ngcontent-hsa-enrollment-app-c35=\\\"\\\"></th><th _ngcontent-hsa-enrollment-app-c35=\\\"\\\"></th><!----><th _ngcontent-hsa-enrollment-app-c35=\\\"\\\"></th></tr></thead><tbody _ngcontent-hsa-enrollment-app-c35=\\\"\\\"><tr _ngcontent-hsa-enrollment-app-c35=\\\"\\\"><!----><td _ngcontent-hsa-enrollment-app-c35=\\\"\\\"><label _ngcontent-hsa-enrollment-app-c35=\\\"\\\">My&nbsp;contribution per regular paycheck</label></td><!----><!----><!----><td _ngcontent-hsa-enrollment-app-c35=\\\"\\\" class=\\\"text-right\\\"><label _ngcontent-hsa-enrollment-app-c35=\\\"\\\">$0.00</label></td><!----><!----><!----><td _ngcontent-hsa-enrollment-app-c35=\\\"\\\"><h2 _ngcontent-hsa-enrollment-app-c35=\\\"\\\" class=\\\"text-primary-2 no-margin nsp-input inline pull-left\\\"> ($0.00 at year end) <a _ngcontent-hsa-enrollment-app-c35=\\\"\\\" mattooltipposition=\\\"right\\\" class=\\\"mat-tooltip-trigger\\\" aria-describedby=\\\"cdk-describedby-message-2\\\" cdk-describedby-host=\\\"0\\\"><i _ngcontent-hsa-enrollment-app-c35=\\\"\\\" class=\\\"material-icons md-18 info\\\">info</i></a><!----></h2></td><!----><!----></tr><tr _ngcontent-hsa-enrollment-app-c35=\\\"\\\"><td _ngcontent-hsa-enrollment-app-c35=\\\"\\\"> Employer contribution </td><td _ngcontent-hsa-enrollment-app-c35=\\\"\\\" class=\\\"text-right\\\"><span _ngcontent-hsa-enrollment-app-c35=\\\"\\\" class=\\\"text-bold\\\">($83.33 per regular paycheck)</span><!----><!----><!----><!----></td><!----><td _ngcontent-hsa-enrollment-app-c35=\\\"\\\"></td></tr><!----><tr _ngcontent-hsa-enrollment-app-c35=\\\"\\\"><td _ngcontent-hsa-enrollment-app-c35=\\\"\\\"></td><td _ngcontent-hsa-enrollment-app-c35=\\\"\\\" class=\\\"text-right\\\"><!----></td><!----><td _ngcontent-hsa-enrollment-app-c35=\\\"\\\"><span _ngcontent-hsa-enrollment-app-c35=\\\"\\\" class=\\\"text-italic block-item\\\">Your contribution limit is $8,550.</span></td></tr></tbody></table></form></nsp-hsa-contribution-table><!----><!----><!----><!----></div></div></div><nsp-hsa-button-bar _ngcontent-hsa-enrollment-app-c39=\\\"\\\" _nghost-hsa-enrollment-app-c30=\\\"\\\"><div _ngcontent-hsa-enrollment-app-c30=\\\"\\\" class=\\\"row\\\"><div _ngcontent-hsa-enrollment-app-c30=\\\"\\\" class=\\\"nsp-col-12 nsp-expand-md\\\"><a _ngcontent-hsa-enrollment-app-c30=\\\"\\\" class=\\\"nsp-button secondary\\\" href=\\\"https://portal.insperity.com/pcms/benefits\\\">Exit</a><!----><button _ngcontent-hsa-enrollment-app-c39=\\\"\\\" class=\\\"nsp-button action\\\" disabled=\\\"\\\">Next</button></div></div></nsp-hsa-button-bar></nsp-hsa-contribution><!----><!----><!----><!----></nsp-hsa-new><!----></nsp-root>\\n"
                + //
                "    </main>\\n" + //
                "    \\n" + //
                "\\n" + //
                "    \\n" + //
                "\\n" + //
                "\\n" + //
                "    \\n" + //
                "    \\n" + //
                "\\n" + //
                "<x-nsp-premier-footer _nghost-uuy-c8=\\\"\\\" ng-version=\\\"13.3.3\\\"><footer _ngcontent-uuy-c8=\\\"\\\"><div _ngcontent-uuy-c8=\\\"\\\" class=\\\"row\\\"><div _ngcontent-uuy-c8=\\\"\\\" class=\\\"nsp-col-12 text-center\\\"><p _ngcontent-uuy-c8=\\\"\\\"><a href=\\\"https://www.insperity.com/copyright-information/\\\" target=\\\"_blank\\\">Copyright</a> Â© 2025 Insperity. All rights reserved.</p></div></div><div _ngcontent-uuy-c8=\\\"\\\" class=\\\"row\\\"><div _ngcontent-uuy-c8=\\\"\\\" class=\\\"nsp-col-12\\\"><div _ngcontent-uuy-c8=\\\"\\\" class=\\\"nsp-table\\\"><ul _ngcontent-uuy-c8=\\\"\\\" class=\\\"nsp-standard-list inline bordered\\\"><li _ngcontent-uuy-c8=\\\"\\\"><a _ngcontent-uuy-c8=\\\"\\\" ngapps-nsp-link-element=\\\"\\\" target=\\\"_self\\\" href=\\\"https://portal.insperity.com/pcms/about_insperity\\\"> About Insperity </a></li><!----><li _ngcontent-uuy-c8=\\\"\\\"><a _ngcontent-uuy-c8=\\\"\\\" ngapps-nsp-link-element=\\\"\\\" target=\\\"_self\\\" href=\\\"https://portal.insperity.com/pcms/terms_of_use\\\"> Terms of Use  </a></li><!----><li _ngcontent-uuy-c8=\\\"\\\"><a _ngcontent=uuy-c8=\\\"\\\" ngapps-nsp-link-element=\\\"\\\" target=\\\"_blank\\\" href=\\\"https://www.insperity.com/privacy-notice/\\\"> Privacy Notice  </a></li><!----><li _ngcontent-uuy-c8=\\\"\\\"><a _ngcontent-uuy-c8=\\\"\\\" ngapps-nsp-link-element=\\\"\\\" target=\\\"_self\\\" href=\\\"https://portal.insperity.com/pcms/need_help\\\"> Need Help </a></li><!----><li _ngcontent-uuy-c8=\\\"\\\"><a _ngcontent-uuy-c8=\\\"\\\" ngapps-nsp-link-element=\\\"\\\" target=\\\"_self\\\" href=\\\"https://service.insperity.com/service-technology/ReleaseNotes/nsp_release_notes.html\\\"> Release Notes </a></li><!----><li _ngcontent-uuy-c8=\\\"\\\"><a _ngcontent-uuy-c8=\\\"\\\" ngapps-nsp-link-element=\\\"\\\" target=\\\"_self\\\" href=\\\"https://portalapps.insperity.com/ContactUsNow\\\"> Contact Us </a></li><!----><li _ngcontent-uuy-c8=\\\"\\\" id=\\\"screenshare\\\"><a _ngcontent-uuy-c8=\\\"\\\" ngapps-nsp-link-element=\\\"\\\" target=\\\"_self\\\" href=\\\"javascript:;\\\"> Screen Share </a></li><!----><!----></ul></div></div></div></footer><!----><!----><!----></x-nsp-premier-footer>\\n"
                + //
                "</div>\\n" + //
                "   \\n" + //
                "\\n" + //
                "<button aria-label=\\\"Open Resource Center, 6 new notifications\\\" data-pendo-aria-label-alert-singular=\\\"Open Resource Center, {UNSEEN_COUNT} new notification\\\" data-pendo-aria-label-alert-plural=\\\"Open Resource Center, {UNSEEN_COUNT} new notifications\\\" aria-expanded=\\\"false\\\" type=\\\"button\\\" id=\\\"_pendo-badge_ov7fvLtEGwSho2rCJZ_NbHm7CGs\\\" data-layout=\\\"badgeResourceCenter\\\" class=\\\"_pendo-badge _pendo-resource-center-badge-container _pendo-badge_\\\" data-pendo-stashed-aria-label=\\\"Open Resource Center\\\" style=\\\"border-radius: 28px; inset: auto 10px 10px auto; margin: 0px; position: fixed; z-index: 19000; background: rgba(255, 255, 255, 0); padding: 0px; height: 56px; width: 56px; box-shadow: rgba(0, 0, 0, 0.15) 2px 0px 6px 0px; border: 0px; float: none; vertical-align: text-bottom; cursor: pointer; display: inline-block;\\\"><img id=\\\"pendo-image-badge-6ae9731a\\\" src=\\\"https://content.analytics.insperity.com/3_g8XTCrgoanjOissFz-AD-iZFA/guide-media-b8875caa-06f8-49a8-81dd-70856f6449e5\\\" alt=\\\"\\\" data-_pendo-image-1=\\\"\\\" class=\\\"_pendo-image _pendo-badge-image _pendo-resource-center-badge-image\\\" style=\\\"display: block; height: 56px; width: 56px; padding: 0px; margin: 0px; line-height: 1; border: none; box-shadow: rgba(255, 255, 255, 0) 0px 0px 0px 0px; float: none; vertical-align: baseline;\\\"><div class=\\\" pendo-resource-center-badge-notification-bubble\\\" style=\\\"position: absolute; border-radius: 20px; line-height: 0px; height: 26px; box-sizing: content-box; background-color: rgb(89, 203, 232); top: -2px; left: -12px; padding: 0px 10px; margin-left: 0px; margin-top: 0px;\\\"><div class=\\\" pendo-notification-bubble-unread-count\\\" style=\\\"font-weight: 600; font-family: inherit; height: 100%; position: relative; top: 50%; color: rgb(255, 255, 255); white-space: pre-wrap;\\\">6</div></div></button><div class=\\\"cdk-describedby-message-container cdk-visually-hidden\\\" style=\\\"visibility: hidden;\\\"><div id=\\\"cdk-describedby-message-1\\\" role=\\\"tooltip\\\">Insperity will contribute up to $2,000.00\\n"
                + //
                "             per full calendar year ($83.33 per regular paycheck).\\n" + //
                "             You will receive this contribution whether or not you make contributions.</div><div id=\\\"cdk-describedby-message-2\\\" role=\\\"tooltip\\\">This estimate is based on the contribution amounts entered below and the number of pay periods you have remaining in the\\n"
                + //
                "  calendar year. Actual amounts contributed to your HSA for the calendar year are subject to IRS annual limits, and may vary\\n"
                + //
                "  based on any subsequent changes made to your contributions, as well as to your eligibility to participate in the Insperity HSA Program.</div></div>\\n"
                + //
                "    </body>\\n" + //
                "    </html>\\n" + //
                "    \",\n" + //
                "  \"state\": {\n" + //
                "    \"contributionLimits\": {\n" + //
                "      \"2025\": {\n" + //
                "        \"companyId\": {\n" + //
                "          \"RawValue\": 6103500\n" + //
                "        },\n" + //
                "        \"coverageLevel\": \"Employee and Child\",\n" + //
                "        \"coverageOptionName\": \"UHC ChoicePlus 3300 HDHP\",\n" + //
                "        \"effectiveDate\": \"2025-07-21T00:00:00-05:00\",\n" + //
                "        \"employerContributionAmount\": 83.33333333333333,\n" + //
                "        \"employerDistributionFrequencyId\": 1,\n" + //
                "        \"employerFullYearContributionAmount\": 2000,\n" + //
                "        \"isClientSeeding\": true,\n" + //
                "        \"isHiredEmployee\": true,\n" + //
                "        \"maximumAnnualContributionLimit\": 8550,\n" + //
                "        \"minimumPaycheckContributionAmount\": 5,\n" + //
                "        \"planYear\": 2025,\n" + //
                "        \"remainingPayPeriods\": 11\n" + //
                "      }\n" + //
                "    },\n" + //
                "    \"contributionRequest\": null,\n" + //
                "    \"contributionResponse\": {\n" + //
                "      \"canEnroll\": true,\n" + //
                "      \"clientYtdAmount\": 0,\n" + //
                "      \"contributionAmount\": 0,\n" + //
                "      \"employeeYtdAmount\": 0,\n" + //
                "      \"endDate\": null,\n" + //
                "      \"enrollmentType\": \"NEW_ENROLLMENT\",\n" + //
                "      \"eventReasonId\": null\n" + //
                "    },\n" + //
                "    \"employeeContributionAmount\": 0,\n" + //
                "    \"enrollmentType\": \"NEW_ENROLLMENT\",\n" + //
                "    \"eventDate\": null,\n" + //
                "    \"eventReason\": null,\n" + //
                "    \"isEmployeeContributing\": false,\n" + //
                "    \"links\": {\n" + //
                "      \"benefitsUrl\": \"https://portal.insperity.com/pcms/benefits\",\n" + //
                "      \"contactCenterPhoneNumber\": \"866-715-3552\",\n" + //
                "      \"premierUrl\": \"https://portal.insperity.com/cs/nsp/index\",\n" + //
                "      \"siteInformationUrl\": \"https://portal.insperity.com/pcms/client_service_agreement_terms__conditions\"\n"
                + //
                "    },\n" + //
                "    \"page\": \"/contribution/new\"\n" + //
                "  }\n" + //
                "}";
        return t;
    }

}
