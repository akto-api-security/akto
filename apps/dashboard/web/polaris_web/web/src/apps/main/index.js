
// AG Grid license/module registration must be the first thing evaluated in the
// app's module graph, ahead of any component that renders a grid, otherwise
// grids can mount before the license key is set and briefly show the
// "invalid license" watermark.
import { ModuleRegistry, AllCommunityModule } from "ag-grid-community";
import { LicenseManager, AllEnterpriseModule } from "ag-grid-enterprise";


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

// Open-source self-hosted (local_deploy) deployments have no billing service, so
// PLAN_TYPE resolves empty and the plan gate below would wrongly block the user.
// These installs are not SaaS and should never be paywalled.
const isLocalDeploy = (window.DASHBOARD_MODE || '').toUpperCase() === 'LOCAL_DEPLOY'
  || window.IS_SAAS === 'false' || window.IS_SAAS === false;

let free = false
if(isWhitelisted || isSignupPage || isLocalDeploy) {
  free = false; // Whitelisted users, signup pages & self-hosted local deploys should not block user
} else {
  // For non-whitelisted, non-signup users, check plan type
  if(window.PLAN_TYPE && ALLOWED_PLANS.includes(window.PLAN_TYPE.toLowerCase())) {
    free = false; // Valid plan type = no restrictions
  } else {
    free = true; // No valid plan = Block user
  }
}

ModuleRegistry.registerModules([AllCommunityModule, AllEnterpriseModule]);
LicenseManager.setLicenseKey(window.AG_GRID_LICENSE_KEY);


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