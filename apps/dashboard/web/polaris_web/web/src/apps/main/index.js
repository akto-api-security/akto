
import React from "react";
import { createRoot } from "react-dom/client";
import App from "./App";
import { AppProvider } from "@shopify/polaris";
import en from "@shopify/polaris/locales/en.json";
import { StiggProvider } from '@stigg/react-sdk';
import "@shopify/polaris/build/esm/styles.css";
import ExpiredApp from "./ExpiredApp";
import FreeApp from "./FreeApp";

const container = document.getElementById("root");
const root = createRoot(container);

let expired = false;
const ALLOWED_PLANS = ['enterprise', 'professional', 'trial'];

if (
  window.STIGG_CUSTOMER_ID &&
  (window.EXPIRED && window.EXPIRED == 'true')) {

  expired = true;
}

let free = false;
const planType = window.PLAN_TYPE?.toLowerCase();
if (!window.PLAN_TYPE || !ALLOWED_PLANS.includes(planType)) {
  free = true;
}

if (expired) {

  window.mixpanel.track("DASHBOARD_EXPIRED")

  root.render(
    <AppProvider i18n={en}>
      <ExpiredApp />
    </AppProvider>
  )

} else if (free) {

  window.mixpanel.track("DASHBOARD_FREE")

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