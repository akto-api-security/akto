import HomePage from "../dashboard/pages/home/HomePage"
import TestRunsPage from "../dashboard/pages/testing/TestRunsPage/TestRunsPage";
import SingleTestRunPage from "../dashboard/pages/testing/SingleTestRunPage/SingleTestRunPage"
import TestRunResultPage from "../dashboard/pages/testing/TestRunResultPage/TestRunResultPage";
import AllSensitiveData from "../dashboard/pages/observe/AllSensitiveData/AllSensitiveData";
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
import AwsSource from "../dashboard/pages/quick_start/components/AwsSource";
import AuthTypes from "../dashboard/pages/settings/auth_types/AuthTypes";
import AuthTypeDetails from "../dashboard/pages/settings/auth_types/AuthTypeDetails";
import Tags from "../dashboard/pages/settings/tags/Tags";
import TagDetails from "../dashboard/pages/settings/tags/TagDetails";

const router = createBrowserRouter([
  {
    path: "/dashboard",
    element: <HomePage />,
    children: [
      {
        path: "testing",
        element: <PageTesting />,
        children:[
          {
            path: "",
            element: <TestRunsPage />
          },
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
      {
        path: "quick-start/aws-setup",
        element: <AwsSource />
      }
    ]
  },
  {
    path: "/dashboard/settings",
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
        path: "health-logs",
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
    path: "/dashboard/test-editor/:testId",
    element: <TestEditor />
  },
{
  path: "/login",
    element: <SignUp />,
  },
{
  path: "/",
    element: <Navigate to="/login" />,
  }
])

function App() {
  return (
    <AppProvider>
      <RouterProvider router={router} />
    </AppProvider>
  );
}

export default App;