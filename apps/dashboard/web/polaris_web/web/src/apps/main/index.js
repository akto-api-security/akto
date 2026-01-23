
import React from "react";
import { createRoot } from "react-dom/client";
import App from "./App";
import { AppProvider } from "@shopify/polaris";
import en from "@shopify/polaris/locales/en.json";
import { StiggProvider } from '@stigg/react-sdk';
import "@shopify/polaris/build/esm/styles.css";
import ExpiredApp from "./ExpiredApp";
import FreeApp from "./FreeApp";
import func from "@/util/func";

const container = document.getElementById("root");
const root = createRoot(container);

let expired = false;
const ALLOWED_PLANS = ['enterprise', 'professional', 'trial'];

if (
  window.STIGG_CUSTOMER_ID &&
  (window.EXPIRED && window.EXPIRED == 'true')) {

  expired = true;
}

// Bypass FreeApp for signup/login related pages
const signupPages = ['/check-inbox', '/business-email', '/signup', '/sso-login', '/addUserToAccount', '/login'];
const currentPath = window.location.pathname;
const isSignupPage = signupPages.some(page => currentPath.startsWith(page));
const isWhitelisted = func.isWhiteListedOrganization();
let free = !(isWhitelisted || isSignupPage);
if(window.PLAN_TYPE && ALLOWED_PLANS.includes(window.PLAN_TYPE.toLowerCase())) {
  free = true;
}else if(!window.PLAN_TYPE) {
  free = false;
}

if (expired) {

  if (window.mixpanel && window.mixpanel.track) {
    window.mixpanel.track("DASHBOARD_EXPIRED")
  }

  root.render(
    <AppProvider i18n={en}>
      <ExpiredApp />
    </AppProvider>
  )

} else if (free) {

  if (window.mixpanel && window.mixpanel.track) {
    window.mixpanel.track("DASHBOARD_FREE")
  }

  root.render(
    <AppProvider i18n={en}>
      <FreeApp />
    </AppProvider>
  )

} else {
  root.render(
    <StiggProvider apiKey={window.STIGG_CLIENT_KEY}>
      <AppProvider i18n={en}>
        <App />
      </AppProvider>
    </StiggProvider>
  );
}