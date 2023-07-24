package com.akto.action;

import com.akto.DaoInit;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.SampleDataDao;
import com.akto.dao.context.Context;
import com.akto.dto.*;
import com.akto.dto.traffic.Key;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.type.URLMethods;
import com.akto.parsers.HttpCallParser;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.mongodb.BasicDBObject;
import com.mongodb.ConnectionString;
import com.mongodb.client.model.Filters;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;

public class ExportSampleDataAction extends UserAction {
    private final static ObjectMapper mapper = new ObjectMapper();
    private static final JsonFactory factory = mapper.getFactory();
    @Override
    public String execute() {
        return SUCCESS.toUpperCase();
    }

    private String collectionName;
    private String lastUrlFetched;
    private String lastMethodFetched;
    private int limit;
    private final List<BasicDBObject> importInBurpResult = new ArrayList<>();
    public String importInBurp() {
        if (limit <= 0 || limit > 500 ) limit = 500;
        ApiCollection apiCollection = ApiCollectionsDao.instance.findByName(collectionName);

        if (apiCollection == null) {
            addActionError("Invalid collection");
            return ERROR.toUpperCase();
        }

        int apiCollectionId = apiCollection.getId();

        List<SampleData> sampleDataList = SampleDataDao.instance.fetchSampleDataPaginated(apiCollectionId, lastUrlFetched, lastMethodFetched, limit, 1);

        lastMethodFetched = null;
        lastUrlFetched = null;

        for (SampleData s: sampleDataList) {
            List<String> samples = s.getSamples();
            if (samples.size() < 1) continue;

            String msg = samples.get(0);
            Map<String, String> burpRequestFromSampleData = generateBurpRequestFromSampleData(msg);
            // use url from the sample data instead of relying on the id
            // this is to handle parameterised URLs
            String url = burpRequestFromSampleData.get("url");
            String req = burpRequestFromSampleData.get("request");
            String httpType = burpRequestFromSampleData.get("type");
            String resp = generateBurpResponseFromSampleData(msg, httpType);

            BasicDBObject res = new BasicDBObject();
            res.put("url", url);
            res.put("req", req);
            res.put("res", resp);

            importInBurpResult.add(res);

            // But for lastUrlFetched we need the id because mongo query uses the one in _id
            lastUrlFetched = s.getId().url;
            lastMethodFetched =  s.getId().method.name();
        }

        return SUCCESS.toUpperCase();
    }

    private String burpRequest;
    public String generateBurpRequest() {
        if (sampleData == null) {
            addActionError("Invalid sample data");
            return ERROR.toUpperCase();
        }

        Map<String, String> result = generateBurpRequestFromSampleData(sampleData);
        burpRequest = result.get("request_path");
        return SUCCESS.toUpperCase();
    }


    private Map<String, String> generateBurpRequestFromSampleData(String sampleData) {
        OriginalHttpRequest originalHttpRequest = new OriginalHttpRequest();
        try {
            originalHttpRequest.buildFromSampleMessage(sampleData);
        } catch (Exception e) {
            originalHttpRequest.buildFromApiSampleMessage(sampleData);
        }

        String url = originalHttpRequest.getFullUrlWithParams();
        String path = originalHttpRequest.getPath();

        if (!url.startsWith("http")) {
            String host = originalHttpRequest.findHostFromHeader();
            String protocol = originalHttpRequest.findProtocolFromHeader();
            try {
                url = OriginalHttpRequest.makeUrlAbsolute(url,host, protocol);
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
        }

        StringBuilder builderWithUrl = buildRequest(originalHttpRequest, url);
        StringBuilder builderWithPath = buildRequest(originalHttpRequest, path);

        Map<String, String> result = new HashMap<>();
        result.put("request", builderWithUrl.toString());
        result.put("url", url);
        result.put("type", originalHttpRequest.getType());
        result.put("request_path", builderWithPath.toString());

        return result;
    }

    private StringBuilder buildRequest(OriginalHttpRequest originalHttpRequest, String url) {
        StringBuilder builder = new StringBuilder("");

        // METHOD and PATH
        builder.append(originalHttpRequest.getMethod()).append(" ")
                .append(url).append(" ")
                .append(originalHttpRequest.getType())
                .append("\n");

        // HEADERS
        addHeadersBurp(originalHttpRequest.getHeaders(), builder);

        builder.append("\n");

        // BODY
        builder.append(originalHttpRequest.getBody());
        return builder;
    }

    private String generateBurpResponseFromSampleData(String sampleData, String httpType) {
        OriginalHttpResponse originalHttpResponse = new OriginalHttpResponse();
        originalHttpResponse.buildFromSampleMessage(sampleData);

        OriginalHttpRequest originalHttpRequest = new OriginalHttpRequest();
        originalHttpRequest.buildFromSampleMessage(sampleData);

        StringBuilder builder = new StringBuilder("");

        // HTTP type
        builder.append(httpType).append(" ").append(originalHttpResponse.getStatusCode()).append(" ").append("\n");

        // Headers
        addHeadersBurp(originalHttpResponse.getHeaders(), builder);

        builder.append("\n");

        // Body
        builder.append(originalHttpResponse.getBody());

        return builder.toString();
    }

    private  void addHeadersBurp(Map<String, List<String>> headers, StringBuilder builder) {
        for (String headerName: headers.keySet()) {
            List<String> values = headers.get(headerName);
            if (values == null || values.isEmpty() || headerName.length()<1) continue;
            String prettyHeaderName = headerName.substring(0, 1).toUpperCase() + headerName.substring(1);
            String value = String.join(",", values);
            builder.append(prettyHeaderName).append(": ").append(value);
            builder.append("\n");
        }
    }

    public static void main(String[] args) {
        Gson gson = new Gson();
        BasicDBObject ob = BasicDBObject.parse("{\"request\":{\"url\":\"https://www.atlassian.com:443/ondemand/signup/confirmation\",\"queryParams\":\"ondemandurl=veviwag1&cloudId=d208cff3-0355-4ac6-b94d-ba049e44cc14&requestId=98725370&products=jira-software.ondemand\",\"body\":\"{}\",\"headers\":\"{\\\"sec-fetch-mode\\\":\\\"navigate\\\",\\\"referer\\\":\\\"https://www.atlassian.com/try/cloud/signup?bundle=jira-software&edition=free\\\",\\\"sec-fetch-site\\\":\\\"same-origin\\\",\\\"cookie\\\":\\\"null\\\",\\\"accept-language\\\":\\\"en-GB,en-US;q=0.9,en;q=0.8\\\",\\\"accept\\\":\\\"text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9\\\",\\\"sec-ch-ua\\\":\\\"\\\\\\\" Not A;Brand\\\\\\\";v=\\\\\\\"99\\\\\\\", \\\\\\\"Chromium\\\\\\\";v=\\\\\\\"104\\\\\\\"\\\",\\\"sec-ch-ua-mobile\\\":\\\"?0\\\",\\\"sec-ch-ua-platform\\\":\\\"\\\\\\\"macOS\\\\\\\"\\\",\\\"host\\\":\\\"www.atlassian.com\\\",\\\"upgrade-insecure-requests\\\":\\\"1\\\",\\\"accept-encoding\\\":\\\"gzip, deflate\\\",\\\"user-agent\\\":\\\"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/104.0.5112.102 Safari/537.36\\\",\\\"sec-fetch-dest\\\":\\\"document\\\"}\"},\"response\":{\"statusCode\":200,\"body\":\"\\n\\n<!doctype html> <html id=\\\"magnolia\\\" lang=\\\"en\\\"> <head> <meta charset=\\\"utf-8\\\"> <meta http-equiv=\\\"X-UA-Compatible\\\" content=\\\"IE=edge, chrome=1\\\"> <meta name=\\\"viewport\\\" content=\\\"width=device-width, initial-scale=1\\\"> <meta property=\\\"fb:pages\\\" content=\\\"115407078489594\\\" /> <meta name=\\\"author\\\" content=\\\"Atlassian\\\"> <link rel=\\\"canonical\\\" href=\\\"https://www.atlassian.com/ondemand/signup/confirmation\\\"/> <title>Confirmation | Atlassian</title> <link rel=\\\"preload\\\" href=\\\"https://wac-cdn.atlassian.com/static/master/1862/assets/build/fonts/charlie-sans/charlie-text/Charlie_Text-Regular.woff2\\\" as=\\\"font\\\" type=\\\"font/woff2\\\" crossorigin> <link rel=\\\"preload\\\" href=\\\"https://wac-cdn.atlassian.com/static/master/1862/assets/build/fonts/charlie-sans/charlie-text/Charlie_Text-Bold.woff2\\\" as=\\\"font\\\" type=\\\"font/woff2\\\" crossorigin> <link rel=\\\"preload\\\" href=\\\"https://wac-cdn.atlassian.com/static/master/1862/assets/build/fonts/charlie-sans/charlie-text/Charlie_Text-Semibold.woff2\\\" as=\\\"font\\\" type=\\\"font/woff2\\\" crossorigin> <link rel=\\\"preload\\\" href=\\\"https://wac-cdn.atlassian.com/static/master/1862/assets/build/fonts/charlie-sans/charlie-display/Charlie_Display-Semibold.woff2\\\" as=\\\"font\\\" type=\\\"font/woff2\\\" crossorigin> <link rel=\\\"preload\\\" href=\\\"https://wac-cdn.atlassian.com/static/master/1862/assets/build/fonts/charlie-sans/charlie-display/Charlie_Display-Black.woff2\\\" as=\\\"font\\\" type=\\\"font/woff2\\\" crossorigin> <link rel=\\\"stylesheet\\\" href=\\\"https://wac-cdn.atlassian.com/static/master/1862/assets/build/css/wpl-main.css?cdnVersion=496\\\" id=\\\"main-css\\\" /> <link href=\\\"https://wac-cdn.atlassian.com\\\" rel=\\\"preconnect\\\"> <script type=\\\"text/javascript\\\" src=\\\"https://metal.prod.atl-paas.net/1.24.0/metal-head.min.js\\\"></script> <script type=\\\"text/javascript\\\" src=\\\"https://wac-cdn.atlassian.com/static/master/1862/assets/build/js/head.js?cdnVersion=496\\\"></script> <link rel=\\\"alternate\\\" href=\\\"https://www.atlassian.com/ondemand/signup/confirmation\\\" hreflang=\\\"x-default\\\" /> <link rel=\\\"alternate\\\" href=\\\"https://www.atlassian.com/ondemand/signup/confirmation\\\" hreflang=\\\"en\\\" /> <link rel=\\\"alternate\\\" href=\\\"https://www.atlassian.com/ja/ondemand/signup/confirmation\\\" hreflang=\\\"ja\\\" /> <link rel=\\\"alternate\\\" href=\\\"https://www.atlassian.com/fr/ondemand/signup/confirmation\\\" hreflang=\\\"fr\\\" /> <link rel=\\\"alternate\\\" href=\\\"https://www.atlassian.com/de/ondemand/signup/confirmation\\\" hreflang=\\\"de\\\" /> <link rel=\\\"alternate\\\" href=\\\"https://www.atlassian.com/es/ondemand/signup/confirmation\\\" hreflang=\\\"es\\\" /> <link rel=\\\"alternate\\\" href=\\\"https://www.atlassian.com/br/ondemand/signup/confirmation\\\" hreflang=\\\"pt-BR\\\" /> <link rel=\\\"alternate\\\" href=\\\"https://www.atlassian.com/zh/ondemand/signup/confirmation\\\" hreflang=\\\"zh-Hans\\\" /> <link rel=\\\"alternate\\\" href=\\\"https://www.atlassian.com/ko/ondemand/signup/confirmation\\\" hreflang=\\\"ko\\\" /> <link rel=\\\"alternate\\\" href=\\\"https://www.atlassian.com/ru/ondemand/signup/confirmation\\\" hreflang=\\\"ru\\\" /> <link rel=\\\"alternate\\\" href=\\\"https://www.atlassian.com/pl/ondemand/signup/confirmation\\\" hreflang=\\\"pl\\\" /> <link rel=\\\"alternate\\\" href=\\\"https://www.atlassian.com/it/ondemand/signup/confirmation\\\" hreflang=\\\"it\\\" /> <link rel=\\\"alternate\\\" href=\\\"https://www.atlassian.com/nl/ondemand/signup/confirmation\\\" hreflang=\\\"nl\\\" /> <link rel=\\\"alternate\\\" href=\\\"https://www.atlassian.com/hu/ondemand/signup/confirmation\\\" hreflang=\\\"hu\\\" /> <link rel=\\\"apple-touch-icon-precomposed\\\" sizes=\\\"57x57\\\" href=\\\"https://wac-cdn.atlassian.com/assets/img/favicons/atlassian/apple-touch-icon-57x57.png\\\" /> <link rel=\\\"apple-touch-icon-precomposed\\\" sizes=\\\"114x114\\\" href=\\\"https://wac-cdn.atlassian.com/assets/img/favicons/atlassian/apple-touch-icon-114x114.png\\\" /> <link rel=\\\"apple-touch-icon-precomposed\\\" sizes=\\\"72x72\\\" href=\\\"https://wac-cdn.atlassian.com/assets/img/favicons/atlassian/apple-touch-icon-72x72.png\\\" /> <link rel=\\\"apple-touch-icon-precomposed\\\" sizes=\\\"144x144\\\" href=\\\"https://wac-cdn.atlassian.com/assets/img/favicons/atlassian/apple-touch-icon-144x144.png\\\" /> <link rel=\\\"apple-touch-icon-precomposed\\\" sizes=\\\"120x120\\\" href=\\\"https://wac-cdn.atlassian.com/assets/img/favicons/atlassian/apple-touch-icon-120x120.png\\\" /> <link rel=\\\"apple-touch-icon-precomposed\\\" sizes=\\\"152x152\\\" href=\\\"https://wac-cdn.atlassian.com/assets/img/favicons/atlassian/apple-touch-icon-152x152.png\\\" /> <link rel=\\\"icon\\\" type=\\\"image/png\\\" href=\\\"https://wac-cdn.atlassian.com/assets/img/favicons/atlassian/favicon.png\\\" sizes=\\\"32x32\\\" /> <link rel=\\\"mask-icon\\\" href=\\\"https://wac-cdn.atlassian.com/assets/img/favicons/atlassian/safari-pinned-tab.svg\\\" color=\\\"#59afe1\\\"> <meta name=\\\"msapplication-TileColor\\\" content=\\\"#ffffff\\\"> <meta name=\\\"msapplication-TileImage\\\" content=\\\"https://wac-cdn.atlassian.com/assets/img/favicons/atlassian/mstile-144x144.png\\\" /> <meta name=\\\"theme-color\\\" content=\\\"#205081\\\"> <meta property=\\\"og:title\\\" content=\\\"Confirmation | Atlassian\\\" /> <meta property=\\\"og:type\\\" content=\\\"website\\\" /> <meta property=\\\"og:url\\\" content=\\\"https://www.atlassian.com/ondemand/signup/confirmation\\\" /> <meta property=\\\"og:image\\\" content=\\\"https://wac-cdn.atlassian.com/dam/jcr:33c6ebab-72c3-45b1-8acc-a3e413e10477/social-share.png\\\" /> <meta property=\\\"og:image:width\\\" content=\\\"1200\\\"> <meta property=\\\"og:image:height\\\" content=\\\"630\\\"> <meta property=\\\"og:site_name\\\" content=\\\"Atlassian\\\" /> <meta name=\\\"twitter:card\\\" content=\\\"summary\\\" /> <meta name=\\\"twitter:image\\\" content=\\\"https://wac-cdn.atlassian.com/dam/jcr:89e146b4-642e-41fc-8e65-7848337d7bdd/Atlassian-icon-blue-onecolor@2x.png\\\" /> <meta name=\\\"twitter:site\\\" content=\\\"@Atlassian\\\" /> <script src=\\\"https://wac-cdn.atlassian.com/static/master/1862/assets/build/js/jquery.js?cdnVersion=496\\\"></script> <script type=\\\"text/javascript\\\"> window.cmsPageVariant=\\\"magnolia_migration\\\"; </script> <script type=\\\"text/javascript\\\"> window.featureFlags = \\\"jiraTemplateGallery=showJiraTemplateGallery&amp;aisAvailableProducts=enableAisAvailableProducts&amp;jpdBundle=enableJpdBundle&amp;devopsGoogleSignup=enableDevopsGoogleSignup&amp;siteSelectionListBlockedGeoIP=enableSiteSelectionListBlockedGeoIP&amp;control=im_itsm_microsite_control\\\".split('&amp;') .reduce((flags, flagStr) => { const [flagKey, flagValue] = flagStr.split('='); return { ...flags, [flagKey]: flagValue }; }, {}) </script> <script type=\\\"text/javascript\\\"> window.cmsPageVariant = window.cmsPageVariant ? window.cmsPageVariant : window.featureFlags.cmsVariant; </script> <link rel=\\\"preconnect\\\" href=\\\"cdn.optimizely.com\\\" /> <script type=\\\"text/javascript\\\" src=\\\"https://cdn.optimizely.com/js/1096093.js\\\"></script> <script type=\\\"text/javascript\\\"> var LOCALIZED_PRICING_CONTENTFUL_SPACE = \\\"3s3v3nq72la0\\\"; var LOCALIZED_PRICING_CONTENTFUL_ENVIRONMENT = \\\"master\\\"; var LOCALIZED_PRICING_CONTENTFUL_ACCESS_TOKEN = \\\"b9c3ea8d9c664146e9c7dd136a1d40ff8415715c3ef8ee222536f36fb2056613\\\"; var LOCALIZED_CCP_PRICING_CONTENTFUL_ENVIRONMENT = \\\"ccp_dev\\\"; var LOCALIZED_CCP_PRICING_CONTENTFUL_ACCESS_TOKEN = \\\"3SWtzulQjrRd4OdxZm8zhe_ohy5J2-Jafj0Zkr5grS4\\\"; var LOCALIZED_PRICING_USE_GEO_CURRENCY = true; </script> </head> <body id=\\\"confirmation\\\" class=\\\"wac ondemand signup confirmation\\\" data-headerless-path=\\\"confirmation\\\"> <div class=\\\"language-selector-banner \\\"> <script type=\\\"text/x-component\\\"> { \\\"type\\\":\\\"imkt.components.LanguageSwitcherNav\\\", \\\"params\\\": { } } </script> <span class=\\\"language-selector-banner__close-banner\\\">Close</span> <div class=\\\"language-selector-banner__inner-container\\\"> <div class=\\\"language-selector-banner__language-suggestion\\\"> <a href=\\\"#\\\">View this page in <span class=\\\"preferred-locale-detected\\\">your language</span>?</a> </div> <div class=\\\"language-selector-banner__language-selector\\\"> <a href=\\\"#\\\" class=\\\"language-selector-banner__language-selector__trigger\\\">All languages</a> <div class=\\\"language-selector-banner__language-selector__options\\\"> <div class=\\\"language-selector-banner__language-selector__options__header\\\"> <span>Choose your language</span> </div> <ul class=\\\"language-selector-banner__language-selector__options__list\\\"> <li class=\\\"language-selector-banner__language-selector__options__list__item\\\"> <a aria-label=\\\"中文\\\" data-event=\\\"clicked\\\" data-uuid=\\\"212a1c0b-40-0\\\" data-event-component=\\\"linkButton\\\" data-event-container=\\\"languageSelectorBanner\\\" data-schema-version=\\\"1\\\" data-lang=\\\"zh\\\" class=\\\"notranslate SL_opaque\\\" data-locale-url=\\\"https://www.atlassian.com/zh/ondemand/signup/confirmation\\\" href=\\\"https://www.atlassian.com/zh/ondemand/signup/confirmation\\\">中文</a> </li> <li class=\\\"language-selector-banner__language-selector__options__list__item\\\"> <a aria-label=\\\"Deutsch\\\" data-event=\\\"clicked\\\" data-uuid=\\\"212a1c0b-40-1\\\" data-event-component=\\\"linkButton\\\" data-event-container=\\\"languageSelectorBanner\\\" data-schema-version=\\\"1\\\" data-lang=\\\"de\\\" class=\\\"notranslate SL_opaque\\\" data-locale-url=\\\"https://www.atlassian.com/de/ondemand/signup/confirmation\\\" href=\\\"https://www.atlassian.com/de/ondemand/signup/confirmation\\\">Deutsch</a> </li> <li class=\\\"language-selector-banner__language-selector__options__list__item\\\"> <a aria-label=\\\"English\\\" data-event=\\\"clicked\\\" data-uuid=\\\"212a1c0b-40-2\\\" data-event-component=\\\"linkButton\\\" data-event-container=\\\"languageSelectorBanner\\\" data-schema-version=\\\"1\\\" data-lang=\\\"en\\\" class=\\\"notranslate SL_opaque\\\" data-locale-url=\\\"https://www.atlassian.com/ondemand/signup/confirmation\\\" href=\\\"https://www.atlassian.com/ondemand/signup/confirmation\\\">English</a> </li> <li class=\\\"language-selector-banner__language-selector__options__list__item\\\"> <a aria-label=\\\"Español\\\" data-event=\\\"clicked\\\" data-uuid=\\\"212a1c0b-40-3\\\" data-event-component=\\\"linkButton\\\" data-event-container=\\\"languageSelectorBanner\\\" data-schema-version=\\\"1\\\" data-lang=\\\"es\\\" class=\\\"notranslate SL_opaque\\\" data-locale-url=\\\"https://www.atlassian.com/es/ondemand/signup/confirmation\\\" href=\\\"https://www.atlassian.com/es/ondemand/signup/confirmation\\\">Español</a> </li> <li class=\\\"language-selector-banner__language-selector__options__list__item\\\"> <a aria-label=\\\"Français\\\" data-event=\\\"clicked\\\" data-uuid=\\\"212a1c0b-40-4\\\" data-event-component=\\\"linkButton\\\" data-event-container=\\\"languageSelectorBanner\\\" data-schema-version=\\\"1\\\" data-lang=\\\"fr\\\" class=\\\"notranslate SL_opaque\\\" data-locale-url=\\\"https://www.atlassian.com/fr/ondemand/signup/confirmation\\\" href=\\\"https://www.atlassian.com/fr/ondemand/signup/confirmation\\\">Français</a> </li> <li class=\\\"language-selector-banner__language-selector__options__list__item\\\"> <a aria-label=\\\"Italiano\\\" data-event=\\\"clicked\\\" data-uuid=\\\"212a1c0b-40-5\\\" data-event-component=\\\"linkButton\\\" data-event-container=\\\"languageSelectorBanner\\\" data-schema-version=\\\"1\\\" data-lang=\\\"it\\\" class=\\\"notranslate SL_opaque\\\" data-locale-url=\\\"https://www.atlassian.com/it/ondemand/signup/confirmation\\\" href=\\\"https://www.atlassian.com/it/ondemand/signup/confirmation\\\">Italiano</a> </li> <li class=\\\"language-selector-banner__language-selector__options__list__item\\\"> <a aria-label=\\\"한국어\\\" data-event=\\\"clicked\\\" data-uuid=\\\"212a1c0b-40-6\\\" data-event-component=\\\"linkButton\\\" data-event-container=\\\"languageSelectorBanner\\\" data-schema-version=\\\"1\\\" data-lang=\\\"ko\\\" class=\\\"notranslate SL_opaque\\\" data-locale-url=\\\"https://www.atlassian.com/ko/ondemand/signup/confirmation\\\" href=\\\"https://www.atlassian.com/ko/ondemand/signup/confirmation\\\">한국어</a> </li> <li class=\\\"language-selector-banner__language-selector__options__list__item\\\"> <a aria-label=\\\"Magyar\\\" data-event=\\\"clicked\\\" data-uuid=\\\"212a1c0b-40-7\\\" data-event-component=\\\"linkButton\\\" data-event-container=\\\"languageSelectorBanner\\\" data-schema-version=\\\"1\\\" data-lang=\\\"hu\\\" class=\\\"notranslate SL_opaque\\\" data-locale-url=\\\"https://www.atlassian.com/hu/ondemand/signup/confirmation\\\" href=\\\"https://www.atlassian.com/hu/ondemand/signup/confirmation\\\">Magyar</a> </li> <li class=\\\"language-selector-banner__language-selector__options__list__item\\\"> <a aria-label=\\\"Nederlands\\\" data-event=\\\"clicked\\\" data-uuid=\\\"212a1c0b-40-8\\\" data-event-component=\\\"linkButton\\\" data-event-container=\\\"languageSelectorBanner\\\" data-schema-version=\\\"1\\\" data-lang=\\\"nl\\\" class=\\\"notranslate SL_opaque\\\" data-locale-url=\\\"https://www.atlassian.com/nl/ondemand/signup/confirmation\\\" href=\\\"https://www.atlassian.com/nl/ondemand/signup/confirmation\\\">Nederlands</a> </li> <li class=\\\"language-selector-banner__language-selector__options__list__item\\\"> <a aria-label=\\\"日本語\\\" data-event=\\\"clicked\\\" data-uuid=\\\"212a1c0b-40-9\\\" data-event-component=\\\"linkButton\\\" data-event-container=\\\"languageSelectorBanner\\\" data-schema-version=\\\"1\\\" data-lang=\\\"ja\\\" class=\\\"notranslate SL_opaque\\\" data-locale-url=\\\"https://www.atlassian.com/ja/ondemand/signup/confirmation\\\" href=\\\"https://www.atlassian.com/ja/ondemand/signup/confirmation\\\">日本語</a> </li> <li class=\\\"language-selector-banner__language-selector__options__list__item\\\"> <a aria-label=\\\"Português\\\" data-event=\\\"clicked\\\" data-uuid=\\\"212a1c0b-40-10\\\" data-event-component=\\\"linkButton\\\" data-event-container=\\\"languageSelectorBanner\\\" data-schema-version=\\\"1\\\" data-lang=\\\"br\\\" class=\\\"notranslate SL_opaque\\\" data-locale-url=\\\"https://www.atlassian.com/br/ondemand/signup/confirmation\\\" href=\\\"https://www.atlassian.com/br/ondemand/signup/confirmation\\\">Português</a> </li> <li class=\\\"language-selector-banner__language-selector__options__list__item\\\"> <a aria-label=\\\"Pусский\\\" data-event=\\\"clicked\\\" data-uuid=\\\"212a1c0b-40-11\\\" data-event-component=\\\"linkButton\\\" data-event-container=\\\"languageSelectorBanner\\\" data-schema-version=\\\"1\\\" data-lang=\\\"ru\\\" class=\\\"notranslate SL_opaque\\\" data-locale-url=\\\"https://www.atlassian.com/ru/ondemand/signup/confirmation\\\" href=\\\"https://www.atlassian.com/ru/ondemand/signup/confirmation\\\">Pусский</a> </li> <li class=\\\"language-selector-banner__language-selector__options__list__item\\\"> <a aria-label=\\\"Polski\\\" data-event=\\\"clicked\\\" data-uuid=\\\"212a1c0b-40-12\\\" data-event-component=\\\"linkButton\\\" data-event-container=\\\"languageSelectorBanner\\\" data-schema-version=\\\"1\\\" data-lang=\\\"pl\\\" class=\\\"notranslate SL_opaque\\\" data-locale-url=\\\"https://www.atlassian.com/pl/ondemand/signup/confirmation\\\" href=\\\"https://www.atlassian.com/pl/ondemand/signup/confirmation\\\">Polski</a> </li> </ul> </div> </div> </div> </div> <main> <div class=\\\"container-fluid\\\"> <div class=\\\"row\\\"> <div class=\\\"column no-flex\\\" > <div id=\\\"imkt-jsx--c59af9b4\\\" class=\\\"imkt-jsx--wac-confirmation-page\\\"></div> <script type=\\\"text/jsx-component\\\"> { \\\"type\\\": \\\"WacConfirmationPageContainer\\\", \\\"domRootId\\\": \\\"imkt-jsx--c59af9b4\\\", \\\"props\\\": { \\\"images\\\": { \\\"robots\\\":\\\"https://wac-cdn.atlassian.com/dam/jcr:de7ce373-4aca-4b89-ab3a-c28be0ee63ac/construction.gif?cdnVersion=496\\\", \\\"timeout\\\":\\\"https://wac-cdn.atlassian.com/dam/jcr:504d3972-1e50-4ff8-8ba8-66520e0ade03/inbox.svg?cdnVersion=496\\\", \\\"tips\\\":\\\"https://wac-cdn.atlassian.com/dam/jcr:db7f2385-5667-42ca-b5c7-0fea0d31de06/app-switcher-jira.svg?cdnVersion=496\\\" }, \\\"featureFlags\\\": { \\\"provisionSecondPartyApps\\\":false }, \\\"labels\\\": { \\\"WacConfirmationPage.heading\\\":\\\"One moment, your site is starting up\\\" , \\\"WacConfirmationPage.robots\\\":\\\"<p>{ isReferredFromVerifyEmail, select, true {Great, your email address is now verified! Our robots are working on your Atlassian Cloud site.} other {Thanks for signing up! Our robots are working on your Atlassian Cloud site.} }<br />\\\\n\\\\\\\\nThis won&rsquo;t take more than a minute or two. You&rsquo;ll be taken there once it&#39;s ready.&lt;\\\\\\\\/p&gt;<\\\\/p>\\\" , \\\"WacConfirmationPage.timeout\\\":\\\"<p>This is taking longer than expected.<br />\\\\nDon&#39;t worry, we&#39;ll send you an email when your site is ready.<\\\\/p>\\\" , \\\"WacConfirmationPage.tips\\\":\\\"<p>{ isClaiming, select, true {<\\\\/p>\\\\n\\\\n<h2>When you get in, explore all the products you&#39;re evaluating.<\\\\/h2>\\\\n\\\\n<p>} other {<\\\\/p>\\\\n\\\\n<h2>When you get in, explore all the products you&#39;ve purchased.<\\\\/h2>\\\\n\\\\n<p>} }{ type, select, jira-bundle {Click the menu (<span ng-if=\\\\\\\"iconMenu\\\\\\\" style=\\\\\\\"display:inline-flex;vertical-align: middle;padding-bottom: 3px;\\\\\\\"><svg height=\\\\\\\"14\\\\\\\" role=\\\\\\\"presentation\\\\\\\" style=\\\\\\\"margin-bottom: 0\\\\\\\" viewbox=\\\\\\\"0 0 24 24\\\\\\\" width=\\\\\\\"14\\\\\\\"><path d=\\\\\\\"M4 5.01C4 4.451 4.443 4 5.01 4h1.98C7.549 4 8 4.443 8 5.01v1.98C8 7.549 7.557 8 6.99 8H5.01C4.451 8 4 7.557 4 6.99V5.01zm0 6c0-.558.443-1.01 1.01-1.01h1.98c.558 0 1.01.443 1.01 1.01v1.98C8 13.549 7.557 14 6.99 14H5.01C4.451 14 4 13.557 4 12.99v-1.98zm6-6c0-.558.443-1.01 1.01-1.01h1.98c.558 0 1.01.443 1.01 1.01v1.98C14 7.549 13.557 8 12.99 8h-1.98C10.451 8 10 7.557 10 6.99V5.01zm0 6c0-.558.443-1.01 1.01-1.01h1.98c.558 0 1.01.443 1.01 1.01v1.98c0 .558-.443 1.01-1.01 1.01h-1.98c-.558 0-1.01-.443-1.01-1.01v-1.98zm6-6c0-.558.443-1.01 1.01-1.01h1.98c.558 0 1.01.443 1.01 1.01v1.98C20 7.549 19.557 8 18.99 8h-1.98C16.451 8 16 7.557 16 6.99V5.01zm0 6c0-.558.443-1.01 1.01-1.01h1.98c.558 0 1.01.443 1.01 1.01v1.98c0 .558-.443 1.01-1.01 1.01h-1.98c-.558 0-1.01-.443-1.01-1.01v-1.98zm-12 6c0-.558.443-1.01 1.01-1.01h1.98c.558 0 1.01.443 1.01 1.01v1.98C8 19.549 7.557 20 6.99 20H5.01C4.451 20 4 19.557 4 18.99v-1.98zm6 0c0-.558.443-1.01 1.01-1.01h1.98c.558 0 1.01.443 1.01 1.01v1.98c0 .558-.443 1.01-1.01 1.01h-1.98c-.558 0-1.01-.443-1.01-1.01v-1.98zm6 0c0-.558.443-1.01 1.01-1.01h1.98c.558 0 1.01.443 1.01 1.01v1.98c0 .558-.443 1.01-1.01 1.01h-1.98c-.558 0-1.01-.443-1.01-1.01v-1.98z\\\\\\\" data-darkreader-inline-fill=\\\\\\\"\\\\\\\" fill=\\\\\\\"currentColor\\\\\\\" fill-rule=\\\\\\\"evenodd\\\\\\\" style=\\\\\\\"--darkreader-inline-fill:currentColor;\\\\\\\"><\\\\/path><\\\\/svg><\\\\/span>) in the top left to switch between products. Create a project to explore your Jira products&rsquo; features.} jira-only-bundle {Create a project to explore your Jira products&rsquo; features.} other {Click the menu (<span ng-if=\\\\\\\"iconMenu\\\\\\\" style=\\\\\\\"display:inline-flex;vertical-align: middle;padding-bottom: 3px;\\\\\\\"><svg height=\\\\\\\"14\\\\\\\" role=\\\\\\\"presentation\\\\\\\" style=\\\\\\\"margin-bottom: 0\\\\\\\" viewbox=\\\\\\\"0 0 24 24\\\\\\\" width=\\\\\\\"14\\\\\\\"><path d=\\\\\\\"M4 5.01C4 4.451 4.443 4 5.01 4h1.98C7.549 4 8 4.443 8 5.01v1.98C8 7.549 7.557 8 6.99 8H5.01C4.451 8 4 7.557 4 6.99V5.01zm0 6c0-.558.443-1.01 1.01-1.01h1.98c.558 0 1.01.443 1.01 1.01v1.98C8 13.549 7.557 14 6.99 14H5.01C4.451 14 4 13.557 4 12.99v-1.98zm6-6c0-.558.443-1.01 1.01-1.01h1.98c.558 0 1.01.443 1.01 1.01v1.98C14 7.549 13.557 8 12.99 8h-1.98C10.451 8 10 7.557 10 6.99V5.01zm0 6c0-.558.443-1.01 1.01-1.01h1.98c.558 0 1.01.443 1.01 1.01v1.98c0 .558-.443 1.01-1.01 1.01h-1.98c-.558 0-1.01-.443-1.01-1.01v-1.98zm6-6c0-.558.443-1.01 1.01-1.01h1.98c.558 0 1.01.443 1.01 1.01v1.98C20 7.549 19.557 8 18.99 8h-1.98C16.451 8 16 7.557 16 6.99V5.01zm0 6c0-.558.443-1.01 1.01-1.01h1.98c.558 0 1.01.443 1.01 1.01v1.98c0 .558-.443 1.01-1.01 1.01h-1.98c-.558 0-1.01-.443-1.01-1.01v-1.98zm-12 6c0-.558.443-1.01 1.01-1.01h1.98c.558 0 1.01.443 1.01 1.01v1.98C8 19.549 7.557 20 6.99 20H5.01C4.451 20 4 19.557 4 18.99v-1.98zm6 0c0-.558.443-1.01 1.01-1.01h1.98c.558 0 1.01.443 1.01 1.01v1.98c0 .558-.443 1.01-1.01 1.01h-1.98c-.558 0-1.01-.443-1.01-1.01v-1.98zm6 0c0-.558.443-1.01 1.01-1.01h1.98c.558 0 1.01.443 1.01 1.01v1.98c0 .558-.443 1.01-1.01 1.01h-1.98c-.558 0-1.01-.443-1.01-1.01v-1.98z\\\\\\\" data-darkreader-inline-fill=\\\\\\\"\\\\\\\" fill=\\\\\\\"currentColor\\\\\\\" fill-rule=\\\\\\\"evenodd\\\\\\\" style=\\\\\\\"--darkreader-inline-fill:currentColor;\\\\\\\"><\\\\/path><\\\\/svg><\\\\/span>) in the top left to switch between products.}}<\\\\/p>\\\" , \\\"WacConfirmationPage.productDescription\\\":\\\"<p>{ productKey, select, jira.ondemand {Keep track of projects and issues} jira-core.ondemand {Simplify every business team&#39;s projects with approvals, reviews, drag-and-drop form builders, and customizable workflows.} greenhopper.jira.ondemand {Work using agile methods like Scrum and Kanban} jira-software.ondemand {The #1 software development tool used by agile teams. Plan, track, and release world-class software} com.atlassian.servicedesk.ondemand {Manage queues and SLAs for IT and business teams} jira-servicedesk.ondemand {Collaborate at high-velocity, respond to business changes and deliver great customer and employee service experiences fast.} jira-incident-manager.ondemand {One place to respond, resolve, and learn from every incident} com.radiantminds.roadmaps-jira.ondemand {Know when you can deliver, react to change and keep everyone on the same page} bonfire.jira.ondemand {Rapid bug reporting for exploratory and session-based testing} confluence.ondemand {Organize your work, create documents, and discuss everything in one place} com.atlassian.confluence.plugins.confluence-questions.ondemand {Capture, learn from, and retain the collective knowledge of your organization as it grows} team.calendars.confluence.ondemand {Your team&#39;s single source of truth for managing team leave, tracking Jira projects, and planning events} other {<br />\\\\n} }<\\\\/p>\\\" } } } </script> </div> </div> </div> </main> <script type=\\\"text/x-component\\\"> { \\\"type\\\":\\\"imkt.components.PageLeaveTracking\\\", \\\"params\\\": { } } </script> <script type=\\\"text/javascript\\\" src=\\\"https://wac-cdn.atlassian.com/static/master/1862/assets/build/js/main.js?cdnVersion=496\\\"></script> <script type=\\\"text/javascript\\\"> (function(){ var imkt = window.imkt = window.imkt || {}; imkt.isEditMode = false; imkt.isProduction = true; imkt.constants = imkt.constants || {}; imkt.constants.rootPath = \\\"/\\\"; imkt.constants.assetPath = \\\"https://wac-cdn.atlassian.com/static/master/1862\\\" + \\\"/assets/\\\"; imkt.constants.libPath = imkt.constants.assetPath + \\\"bower_components/\\\"; imkt.constants.cdnVersionQuery = \\\"?cdnVersion=496\\\"; imkt.constants.isDevUser = false; imkt.constants.isFreeEnabled = true; imkt.constants.getUserAccounts = false; imkt.constants.headerLoginMenu = true; imkt.constants.public = true; imkt.constants.mobileBreakpoint = 640; })(); </script> <script type=\\\"text/x-component\\\"> { \\\"type\\\":\\\"imkt.pages.WACPage\\\", \\\"params\\\": { } } </script> <script> function initializeATLAnalytics() { var pageViewProperties = { contentSite: 'WAC', contentType: 'Website', contentProduct: 'Other' }; /* get url path, convert to dash format and add it to page view properties. */ var originProduct = window.location.pathname; pageViewProperties.originProduct = \\\"wac\\\" + (originProduct === '/' ? \\\"\\\" : originProduct.replace(new RegExp('/', 'g'),\\\"-\\\")); if (typeof ace !== 'undefined') { ace.analytics.Initializer.initWithPageAnalytics('kiv6wyh2nw', pageViewProperties, null, null, window.atlGlobalLoadStart); } } </script> <script type = \\\"text/javascript\\\"> /** * function to load external js through javascript. * * @param url - javascript url * @param location - location of the dom (e.g. document.head) */ var loadExternalJS = function(url, location, callback){ var scriptTag = document.createElement('script'); scriptTag.src = url; scriptTag.type = 'text/javascript'; /* Then bind the event to the callback function. There are several events for cross browser compatibility. */ scriptTag.onreadystatechange = callback; scriptTag.onload = callback; window.atlGlobalLoadStart = new Date(); /* Fire the loading */ location.appendChild(scriptTag); }; </script> <script type=\\\"text/javascript\\\" class=\\\"optanon-category-2\\\"> loadExternalJS(\\\"https://atl-global.atlassian.com/js/atl-global.min.js\\\", document.body, initializeATLAnalytics); </script> </body> <!-- LastRendered: Aug 26, 2022 7:02:04 PM --> </html>\\n\",\"headers\":\"{\\\"date\\\":\\\"Mon, 29 Aug 2022 05:42:38 GMT\\\",\\\"server\\\":\\\"globaledge-envoy\\\",\\\"x-envoy-upstream-service-time\\\":\\\"27\\\",\\\"x-frame-options\\\":\\\"deny\\\",\\\"expect-ct\\\":\\\"report-uri=\\\\\\\"https://web-security-reports.services.atlassian.com/expect-ct-report/bxp-nginx\\\\\\\", max-age=86400\\\",\\\"pragma\\\":\\\"no-cache\\\",\\\"strict-transport-security\\\":\\\"max-age=63072000; preload\\\",\\\"atl-traceid\\\":\\\"444381822e49ec71\\\",\\\"set-cookie\\\":\\\"atlCohort={\\\\\\\"bucketAll\\\\\\\":{\\\\\\\"bucketedAtUTC\\\\\\\":\\\\\\\"2022-08-29T05:42:38.858Z\\\\\\\",\\\\\\\"version\\\\\\\":\\\\\\\"2\\\\\\\",\\\\\\\"index\\\\\\\":97,\\\\\\\"bucketId\\\\\\\":0}}; Max-Age=315360000; Domain=.atlassian.com; Path=/; Expires=Thu, 26 Aug 2032 05:42:38 GMT;atl_global_ld_flag_settings=%7B%22jiraTemplateGallery%22%3A%22showJiraTemplateGallery%22%2C%22aisAvailableProducts%22%3A%22enableAisAvailableProducts%22%2C%22jpdBundle%22%3A%22enableJpdBundle%22%2C%22devopsGoogleSignup%22%3A%22enableDevopsGoogleSignup%22%2C%22siteSelectionListBlockedGeoIP%22%3A%22enableSiteSelectionListBlockedGeoIP%22%2C%22control%22%3A%22im_itsm_microsite_control%22%7D; Max-Age=300; Domain=.atlassian.com; Path=/; Expires=Mon, 29 Aug 2022 05:47:38 GMT;ajs_anonymous_id=%22f32d9a62-242c-47b6-e745-4ab1bc54944b%22; Max-Age=315360000; Domain=.atlassian.com; Path=/; Expires=Thu, 26 Aug 2032 05:42:38 GMT;bxp_gateway_request_id=0454e20b-d50d-8de7-eb12-214d38cbeb79; Max-Age=300; Domain=.atlassian.com; Path=/; Expires=Mon, 29 Aug 2022 05:47:38 GMT;wac_user_detected=0;Domain=www.atlassian.com;Path=/;Max-Age=-1\\\",\\\"last-modified\\\":\\\"Fri, 26 Aug 2022 19:02:03 GMT\\\",\\\"content-security-policy\\\":\\\"frame-ancestors 'none';\\\",\\\"x-magnolia-registration\\\":\\\"Registered\\\",\\\"x-content-type-options\\\":\\\"nosniff\\\",\\\"x-xss-protection\\\":\\\"1; mode=block\\\",\\\"nel\\\":\\\"{\\\\\\\"failure_fraction\\\\\\\": 0.001, \\\\\\\"include_subdomains\\\\\\\": true, \\\\\\\"max_age\\\\\\\": 600, \\\\\\\"report_to\\\\\\\": \\\\\\\"endpoint-1\\\\\\\"}\\\",\\\"x-powered-by\\\":\\\"Express\\\",\\\"content-type\\\":\\\"text/html;charset=UTF-8\\\",\\\"etag\\\":\\\"W/\\\\\\\"16a6-BECiAoxzlj9U2jGo+Suj2P+Cnus\\\\\\\"\\\",\\\"report-to\\\":\\\"{\\\\\\\"endpoints\\\\\\\": [{\\\\\\\"url\\\\\\\": \\\\\\\"https://dz8aopenkvv6s.cloudfront.net\\\\\\\"}], \\\\\\\"group\\\\\\\": \\\\\\\"endpoint-1\\\\\\\", \\\\\\\"include_subdomains\\\\\\\": true, \\\\\\\"max_age\\\\\\\": 600}\\\",\\\"cache-control\\\":\\\"no-cache, no-store, must-revalidate, max-age=0\\\"}\"}}");
        BasicDBObject reqObj = (BasicDBObject) ob.get("request");
        Map<String, List<String>> headers = new HashMap<>();
        headers = gson.fromJson(reqObj.getString("headers"), headers.getClass());
        System.out.println(headers);
        OriginalHttpRequest originalHttpRequest = new OriginalHttpRequest(
                reqObj.getString("url"), reqObj.getString("queryParams"), reqObj.getString("method"), reqObj.getString("body"), headers, reqObj.getString("type")
        );
    }

    private String curlString;
    private String sampleData;
    public String generateCurl() {

        try {
            curlString = getCurl(sampleData);
            return SUCCESS.toUpperCase();
        } catch (IOException e) {
            addActionError("Couldn't parse the data");
            return ERROR.toUpperCase();
        }
    }

    public static String getCurl(String sampleData) throws IOException {
        HttpResponseParams httpResponseParams;
        try {
            httpResponseParams = HttpCallParser.parseKafkaMessage(sampleData);
        } catch (Exception e) {
            try {
                OriginalHttpRequest originalHttpRequest = new OriginalHttpRequest();
                originalHttpRequest.buildFromApiSampleMessage(sampleData);

                HttpRequestParams httpRequestParams = new HttpRequestParams(
                        originalHttpRequest.getMethod(), originalHttpRequest.getUrl(), originalHttpRequest.getType(),
                        originalHttpRequest.getHeaders(), originalHttpRequest.getBody(), 0
                );

                httpResponseParams = new HttpResponseParams();
                httpResponseParams.requestParams = httpRequestParams;
            } catch (Exception e1) {
                throw e1;
            }

        }

        HttpRequestParams httpRequestParams = httpResponseParams.getRequestParams();

        StringBuilder builder = new StringBuilder("curl -v ");

        // Method
        builder.append("-X ").append(httpRequestParams.getMethod()).append(" \\\n  ");

        String hostName = null;
        // Headers
        for (Map.Entry<String, List<String>> entry : httpRequestParams.getHeaders().entrySet()) {
            if (entry.getKey().equalsIgnoreCase("host") && entry.getValue().size() > 0) {
                hostName = entry.getValue().get(0);
            }
            builder.append("-H '").append(entry.getKey()).append(":");
            for (String value : entry.getValue()) {
                builder.append(" ").append(value.replaceAll("\"", "\\\\\""));
            }
            builder.append("' \\\n  ");
        }

        String urlString;
        String path = httpRequestParams.getURL();
        if (hostName != null && !(path.toLowerCase().startsWith("http") || path.toLowerCase().startsWith("www."))) {
            urlString = path.startsWith("/") ? hostName + path : hostName + "/" + path;
        } else {
            urlString = path;
        }

        StringBuilder url = new StringBuilder(urlString);

        // Body
        try {
            String payload = httpRequestParams.getPayload();
            if (payload == null) payload = "";
            boolean curlyBracesCond = payload.startsWith("{") && payload.endsWith("}");
            boolean squareBracesCond = payload.startsWith("[") && payload.endsWith("]");
            if (curlyBracesCond || squareBracesCond) {
                if (!Objects.equals(httpRequestParams.getMethod(), "GET")) {
                    builder.append("-d '").append(payload).append("' \\\n  ");
                } else {
                    JsonParser jp = factory.createParser(payload);
                    JsonNode node = mapper.readTree(jp);
                    if (node != null) {
                        Iterator<String> fieldNames = node.fieldNames();
                        boolean flag =true;
                        while(fieldNames.hasNext()) {
                            String fieldName = fieldNames.next();
                            JsonNode fieldValue = node.get(fieldName);
                            if (fieldValue.isValueNode()) {
                                if (flag) {
                                    url.append("?").append(fieldName).append("=").append(fieldValue.asText());
                                    flag = false;
                                } else {
                                    url.append("&").append(fieldName).append("=").append(fieldValue.asText());
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw e;
        }


        // URL
        builder.append("\"").append(url).append("\"");

        return builder.toString();
    }

    public String getCurlString() {
        return curlString;
    }

    public void setSampleData(String sampleData) {
        this.sampleData = sampleData;
    }

    public String getBurpRequest() {
        return burpRequest;
    }

    public void setCollectionName(String collectionName) {
        this.collectionName = collectionName;
    }

    public List<BasicDBObject> getImportInBurpResult() {
        return importInBurpResult;
    }

    public void setLastUrlFetched(String lastUrlFetched) {
        this.lastUrlFetched = lastUrlFetched;
    }

    public void setLastMethodFetched(String lastMethodFetched) {
        this.lastMethodFetched = lastMethodFetched;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public String getLastUrlFetched() {
        return lastUrlFetched;
    }

    public String getLastMethodFetched() {
        return lastMethodFetched;
    }
}

