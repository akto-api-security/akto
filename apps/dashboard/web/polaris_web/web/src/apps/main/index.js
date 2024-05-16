
import React from "react";
import { createRoot } from "react-dom/client";
import App from "./App";
import { AppProvider } from "@shopify/polaris";
import en from "@shopify/polaris/locales/en.json";
import { StiggProvider } from '@stigg/react-sdk';
import "@shopify/polaris/build/esm/styles.css";
import ExpiredApp from "./ExpiredApp"
import * as Sentry from "@sentry/react";

Sentry.init({
  dsn: "https://ddf19fb1a875f83cfa3baa1a8efe35d0@o4506573945438208.ingest.sentry.io/4506574714306560",
  integrations: [
    new Sentry.BrowserTracing({
      // Set 'tracePropagationTargets' to control for which URLs distributed tracing should be enabled
      tracePropagationTargets: ["*"],
    }),
    new Sentry.Replay({
      maskAllText: false,
      blockAllMedia: false,
    }),
  ],
  // Performance Monitoring
  tracesSampleRate: 1.0, //  Capture 100% of the transactions
  // Session Replay
  replaysSessionSampleRate: 0.1, // This sets the sample rate at 10%. You may want to change it to 100% while in development and then sample at a lower rate in production.
  replaysOnErrorSampleRate: 1.0, // If you're not already sampling the entire session, change the sample rate to 100% when sampling sessions where errors occur.
});

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