
import React from "react";
import { createRoot } from "react-dom/client";
import App from "./App";
import { AppProvider } from "@shopify/polaris";
import en from "@shopify/polaris/locales/en.json";
import "@shopify/polaris/build/esm/styles.css";
const container = document.getElementById("root");
const root = createRoot(container);
import { StiggProvider } from '@stigg/react-sdk';


root.render(
    <StiggProvider apiKey="client-6a4ba680-5ad9-4697-9e5a-4d8f242aaf5f:0cf27534-96ba-4c60-8239-a264f71bfe26">
      <AppProvider i18n={en}>
        <App />
      </AppProvider>
    </StiggProvider>
);