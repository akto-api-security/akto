<%@ page language="java" contentType="text/html; charset=US-ASCII"
         pageEncoding="US-ASCII"%>
<%@ taglib uri="/struts-tags" prefix="s"%>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=US-ASCII">
    <title>Welcome Page</title>
</head>
<body>
<h3>Welcome <s:property value="username"></s:property></h3>
<div id="dashboard"></div>
<script src="../dist/dashboard.js"></script>
</body>
</html>