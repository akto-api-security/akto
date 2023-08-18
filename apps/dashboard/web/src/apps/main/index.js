
import React from "react";
import { createRoot } from "react-dom/client";
import App from "./App";
import { AppProvider } from "@shopify/polaris";
import en from "@shopify/polaris/locales/en.json";
import "@shopify/polaris/build/esm/styles.css";

import { asyncWithLDProvider } from 'launchdarkly-react-client-sdk';

const container = document.getElementById("root");
const root = createRoot(container);

(async () => {
  const LDProvider = await asyncWithLDProvider({
    clientSideID: '64dca77dd65b82146d830296',
    context:{
      kind: "user",
      email: window.USERS[0]?.login,
      key: "akto",
      name: window.USERS[0]?.name
    },
    options:{},
  });

root.render(
  <AppProvider i18n={en}>
    <LDProvider>
      <App />
    </LDProvider>
  </AppProvider>
)})();