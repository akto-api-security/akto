<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
    <!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
    <%-- Using Struts2 Tags in JSP --%>
        <%@ taglib uri="/struts-tags" prefix="s" %>
            <html>

            <head>
                <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
                <meta name="theme-color" content="#6200EA" />

                <title>Akto</title>
                <link rel="shortcut icon" href="/public/favicon.svg" type="image/svg" />
                <link rel="manifest" href="/public/manifest.json" />
            </head>

            <body>
                <noscript>To run this application, JavaScript is required to be enabled.</noscript>
                <script src="//ajax.googleapis.com/ajax/libs/jquery/3.6.3/jquery.min.js">
                </script>
                <script src="https://apis.google.com/js/client:platform.js?onload=start" async defer>
                </script>
                <script type="text/javascript">
                    (function (f, b) {
                        if (!b.__SV) {
                            var e, g, i, h; window.mixpanel = b; b._i = []; b.init = function (e, f, c) {
                                function g(a, d) { var b = d.split("."); 2 == b.length && (a = a[b[0]], d = b[1]); a[d] = function () { a.push([d].concat(Array.prototype.slice.call(arguments, 0))) } } var a = b; "undefined" !== typeof c ? a = b[c] = [] : c = "mixpanel"; a.people = a.people || []; a.toString = function (a) { var d = "mixpanel"; "mixpanel" !== c && (d += "." + c); a || (d += " (stub)"); return d }; a.people.toString = function () { return a.toString(1) + ".people (stub)" }; i = "disable time_event track track_pageview track_links track_forms track_with_groups add_group set_group remove_group register register_once alias unregister identify name_tag set_config reset opt_in_tracking opt_out_tracking has_opted_in_tracking has_opted_out_tracking clear_opt_in_out_tracking start_batch_senders people.set people.set_once people.unset people.increment people.append people.union people.track_charge people.clear_charges people.delete_user people.remove".split(" ");
                                for (h = 0; h < i.length; h++)g(a, i[h]); var j = "set set_once union unset remove delete".split(" "); a.get_group = function () { function b(c) { d[c] = function () { call2_args = arguments; call2 = [c].concat(Array.prototype.slice.call(call2_args, 0)); a.push([e, call2]) } } for (var d = {}, e = ["get_group"].concat(Array.prototype.slice.call(arguments, 0)), c = 0; c < j.length; c++)b(j[c]); return d }; b._i.push([e, f, c])
                            }; b.__SV = 1.2; e = f.createElement("script"); e.type = "text/javascript"; e.async = !0; e.src = "undefined" !== typeof MIXPANEL_CUSTOM_LIB_URL ?
                                MIXPANEL_CUSTOM_LIB_URL : "file:" === f.location.protocol && "//cdn.mxpnl.com/libs/mixpanel-2-latest.min.js".match(/^\/\//) ? "https://cdn.mxpnl.com/libs/mixpanel-2-latest.min.js" : "//cdn.mxpnl.com/libs/mixpanel-2-latest.min.js"; g = f.getElementsByTagName("script")[0]; g.parentNode.insertBefore(e, g)
                        }
                    })(document, window.mixpanel || []);

                </script>

                <div id="app"></div>
                <script>
                    /*
                if ('serviceWorker' in navigator) {
                navigator.serviceWorker.register('/sw.js').then(function(reg) {
                    console.log('Successfully registered service worker', reg);
                }).catch(function(err) {
                    console.warn('Error whilst registering service worker', err);
                });
                }
                    */

                    window.SIGNUP_INFO = JSON.parse('${requestScope.signupInfo}' || '{}');
                    window.AVATAR = '${requestScope.avatar}';
                    window.USER_NAME = '${requestScope.username}';
                    window.USERS = JSON.parse('${requestScope.users}' || '{}');
                    window.DASHBOARDS = JSON.parse(atob('${requestScope.dashboards}') || '[]');
                    window.ACCOUNTS = JSON.parse('${requestScope.accounts}' || '{}');
                    window.ACTIVE_ACCOUNT = +'${requestScope.activeAccount}';
                    window.DASHBOARD_MODE = '${requestScope.dashboardMode}';
                    window.DASHBOARD_CATEGORY = '${requestScope.dashboardCategory}';
                    window.ACCESS_TOKEN = '${accessToken}';
                    window.SIGNUP_INVITATION_CODE = '${signupInvitationCode}'
                    window.SIGNUP_EMAIL_ID = '${signupEmailId}'
                    // Enabling the debug mode flag is useful during implementation,
                    // but it's recommended you remove it for production

                    if (window.USER_NAME.length > 0) {
                        // Initialize mixpanel
                        mixpanel.init('c403d0b00353cc31d7e33d68dc778806', { debug: false, ignore_dnt: true });
                        mixpanel.identify(window.USER_NAME);
                        mixpanel.people.set({ "$email": window.USER_NAME });
                        mixpanel.register({
                            'email': window.USER_NAME,
                            'dashboard_mode': 'ON_PREM'
                        })
                        mixpanel.track('login');

                        //Initialize intercom
                        (function () { var w = window; var ic = w.Intercom; if (typeof ic === "function") { ic('reattach_activator'); ic('update', w.intercomSettings); } else { var d = document; var i = function () { i.c(arguments); }; i.q = []; i.c = function (args) { i.q.push(args); }; w.Intercom = i; var l = function () { var s = d.createElement('script'); s.type = 'text/javascript'; s.async = true; s.src = 'https://widget.intercom.io/widget/e9w9wkdk'; var x = d.getElementsByTagName('script')[0]; x.parentNode.insertBefore(s, x); }; if (document.readyState === 'complete') { l(); } else if (w.attachEvent) { w.attachEvent('onload', l); } else { w.addEventListener('load', l, false); } } })();
                        window.intercomSettings = {
                            api_base: "https://api-iam.intercom.io",
                            app_id: "e9w9wkdk",
                            created_at: new Date().getTime()
                        };
                    }
   // mixpanel.track('Login');


                </script>
                <script>
                   var beamer_config = {
                        product_id: 'cJtNevEq80216',
                        filter: 'filterTag',
                        selector: '#beamer-btn',
                        top: 0,
                        left: 0,
                        lazy: true
                    };
                </script>
                <script type="text/javascript" src="https://app.getbeamer.com/js/beamer-embed.js" defer="defer"></script>
                <div id="root"></div>
                <script src="/dist/main.js"></script>
            </body>

            </html>