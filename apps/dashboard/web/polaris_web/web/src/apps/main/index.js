
import React from "react";
import { createRoot } from "react-dom/client";
import App from "./App";
import { AppProvider } from "@shopify/polaris";
import en from "@shopify/polaris/locales/en.json";
import { StiggProvider } from '@stigg/react-sdk';
import "@shopify/polaris/build/esm/styles.css";
import ExpiredApp from "./ExpiredApp"

const container = document.getElementById("root");
const root = createRoot(container);

let expired = false;

if (
  window.STIGG_CUSTOMER_ID &&
  (window.EXPIRED && window.EXPIRED == 'true')) {

  expired = true;
}

if (expired) {

  window.mixpanel.track("DASHBOARD_EXPIRED")

  root.render(
    <AppProvider i18n={en}>
      <ExpiredApp />
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