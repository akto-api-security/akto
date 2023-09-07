import HomePage from "../dashboard/pages/home/HomePage"
import TestRunsPage from "../dashboard/pages/testing/TestRunsPage/TestRunsPage";
import SingleTestRunPage from "../dashboard/pages/testing/SingleTestRunPage/SingleTestRunPage"
import TestRunResultPage from "../dashboard/pages/testing/TestRunResultPage/TestRunResultPage";
import AllSensitiveData from "../dashboard/pages/observe/AllSensitiveData/AllSensitiveData";
import ApiCollections from "../dashboard/pages/observe/api_collections/ApiCollections";
import ApiEndpoints from  "../dashboard/pages/observe/api_collections/ApiEndpoints";
import SensitiveDataExposure from "../dashboard/pages/observe/SensitiveDataExposure/SensitiveDataExposure";
import SingleRequest from "../dashboard/pages/observe/SingleRequest/SingleRequest";
import PageObserve from "../dashboard/pages/observe/PageObserve"
import PageTesting from "../dashboard/pages/testing/PageTesting";
import { AppProvider } from "@shopify/polaris"
import SignUp from "../signup/pages/SignUp"
import {
  createBrowserRouter,
  RouterProvider,
  Navigate,
} from "react-router-dom";
import BurpSuite from "../dashboard/pages/settings/integrations/BurpSuite";
import Integrations from "../dashboard/pages/settings/integrations/Integrations";
import Settings from "../dashboard/pages/settings/Settings";
import Users from "../dashboard/pages/settings/users/Users";
import Postman from "../dashboard/pages/settings/integrations/Postman";
import ApiTokens from "../dashboard/pages/settings/integrations/ApiTokens";
import AktoGPT from "../dashboard/pages/settings/integrations/AktoGPT";
import HealthLogs from "../dashboard/pages/settings/health_logs/HealthLogs";
import About from "../dashboard/pages/settings/about/About";
import Metrics from "../dashboard/pages/settings/metrics/Metrics";
import TestEditor from "../dashboard/pages/test_editor/TestEditor";
import DataTypes from "../dashboard/pages/observe/data_types/DataTypes";
import IssuesPage from "../dashboard/pages/issues/IssuesPage/IssuesPage";
import QuickStart from "../dashboard/pages/quick_start/QuickStart";
import Webhooks from "../dashboard/pages/settings/integrations/webhooks/Webhooks";
import Webhook from "../dashboard/pages/settings/integrations/webhooks/Webhook";
import TestRolesPage from "../dashboard/pages/testing/TestRolesPage/TestRolesPage";
import TestRoleSettings from "../dashboard/pages/testing/TestRoleSettings/TestRoleSettings";
import UserConfig from "../dashboard/pages/testing/user_config/UserConfig";
import AuthTypes from "../dashboard/pages/settings/auth_types/AuthTypes";
import AuthTypeDetails from "../dashboard/pages/settings/auth_types/AuthTypeDetails";
import Tags from "../dashboard/pages/settings/tags/Tags";
import TagDetails from "../dashboard/pages/settings/tags/TagDetails";
import Onboarding from "../dashboard/pages/onboarding/Onboarding";
import Dashboard from "../dashboard/pages/Dashboard";
import Slack from "../dashboard/pages/settings/integrations/Slack";
import ApiChanges from "../dashboard/pages/observe/api_collections/ApiChanges";

import Store from "../dashboard/store";
import { generateSearchData } from "@/util/searchItems"
import { useEffect } from "react";
import CICD from "../dashboard/pages/settings/integrations/CICD";

const router = createBrowserRouter([
  {
    path: "/dashboard",
    element: <Dashboard/>,
    children: [
      {
        path: "",
        element: <HomePage />,
        children: [
          {
            path: "testing",
            element: <PageTesting />,
            children:[
              ...(["", "active", "cicd", "inactive"].map((i) => {
                return {
                  path: i,
                  element: <TestRunsPage />
                }
              })),
              {
                path: ":hexId",
                element: <SingleTestRunPage />
              },
              {
                path: ":hexId/result/:hexId2",
                element: <TestRunResultPage />
              },
              {
                path:"roles",
                element: <TestRolesPage/>
              },
              {
                path:"roles/details",
                element:<TestRoleSettings/>
              },
              {
                path:"user-config",
                element:<UserConfig/>
              }
            ]
          },
          {
            path: "observe",
            element: <PageObserve/>,
            children: [
              {
                path: "sensitive",
                element: <AllSensitiveData/>
              },
              {
                path: "inventory",
                element: <ApiCollections/>
              },
              {
                path: "changes",
                element: <ApiChanges/>
              },
              {
                path: "inventory/:apiCollectionId",
                element: <ApiEndpoints/>
              },
              {
                path: "data-types",
                element: <DataTypes/>
              },
              {
                path: "sensitive/:subType",
                element: <SensitiveDataExposure/>
              },
              {
                path: "sensitive/:subType/:apiCollectionId/:urlAndMethod",
                element: <SingleRequest/>
              }
            ]
          },
          {
            path:"issues",
            element:<IssuesPage/>
          },
          {
            path: "quick-start",
            element: <QuickStart/>,
          },
        ]
      },
      {
        path: "settings",
        element: <Settings />,
        children: [
          {
            path: "users",
            element: <Users />
          },
          {
            path: "integrations",
            element: <Integrations />,
          },
          {
            path: "about",
            element: <About />,
          },
          {
            path: "metrics",
            element: <Metrics />,
          },
          {
            path: "integrations/burp",
            element: <BurpSuite />,
          },
          {
            path: "integrations/ci-cd",
            element: <CICD />,
          },
          {
            path: "integrations/postman",
            element: <Postman />,
          },
          {
            path: "integrations/akto_apis",
            element: <ApiTokens />,
          },
          {
            path: "integrations/akto_gpt",
            element: <AktoGPT />,
          },
          {
            path: "integrations/slack",
            element: <Slack />,
          },
          {
            path: "integrations/webhooks",
            element: <Webhooks />,
          },
          {
            path: "integrations/webhooks/:webhookId",
            element: <Webhook />,
          },
          {
            path: "integrations/webhooks/create_custom_webhook",
            element: <Webhook />,
          },
          {
            path: "logs",
            element: <HealthLogs />,
          },
          {
            path: "auth-types",
            element:<AuthTypes/>
          },
          {
            path: "auth-types/details",
            element: <AuthTypeDetails/>
          },
          {
            path: "tags",
            element: <Tags/>
          },
          {
            path: "tags/details",
            element: <TagDetails/>
          }
        ]
      },
      {
        path: "test-editor/:testId",
        element: <TestEditor />
      },
      {
        path: "onboarding",
        element: <Onboarding />
      },
    ]
  },
{
  path: "/login",
    element: <SignUp />,
},
{
  path: "/",
    element: <Navigate to="/login" />,
  },
])

function App() {
  const setAllRoutes = Store(state => state.setAllRoutes)
  const searchData= generateSearchData(router.routes)
  setAllRoutes(searchData)

  useEffect(() => {
    const script = document.createElement('script')
    const scriptText = document.createTextNode(`
    self.MonacoEnvironment = {
      getWorkerUrl: function (moduleId, label) {
          if (label === 'json') {
              return '/polaris_web/web/dist/json.worker.js';
          }
          return '/polaris_web/web/dist/editor.worker.js';
      }
      };
    `);
    script.appendChild(scriptText);
    document.body.appendChild(script)
  }, [])

  return (
    <AppProvider>
      <RouterProvider router={router} />
    </AppProvider>
  );
}

export default App;