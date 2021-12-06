<%@ page language="java" contentType="text/html; charset=US-ASCII"
         pageEncoding="US-ASCII"%>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<%-- Using Struts2 Tags in JSP --%>
<%@ taglib uri="/struts-tags" prefix="s"%>
<html>
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=US-ASCII">
    <meta name="theme-color" content="#6200EA" />

    <title>Akto</title>
    <link rel="shortcut icon" href="/public/favicon.svg" type="image/svg"/>
    <link rel="manifest" href="/public/manifest.json"/>
</head>
<body>
<noscript>To run this application, JavaScript is required to be enabled.</noscript>
<script src="//ajax.googleapis.com/ajax/libs/jquery/1.8.2/jquery.min.js">
</script>
<script src="https://apis.google.com/js/client:platform.js?onload=start" async defer>
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
    window.ACCESS_TOKEN = '${accessToken}';
</script>
<script src="/dist/main.js"></script>
</body>
</html>