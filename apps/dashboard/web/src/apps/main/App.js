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
import DataTypes from "../dashboard/pages/observe/data_types/DataTypes";
import IssuesPage from "../dashboard/pages/issues/issuesPage/IssuesPage";

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
      }
    ]
  },
  {
    path: "/dashboard/settings",
    element: <Settings />,
    children: [
      {
        path: "/dashboard/settings/users",
        element: <Users />
      },
      {
        path: "/dashboard/settings/integrations",
        element: <Integrations />,
      },
      {
        path: "/dashboard/settings/about",
        element: <About />,
      },
      {
        path: "/dashboard/settings/metrics",
        element: <Metrics />,
      },
      {
        path: "/dashboard/settings/integrations/burp",
        element: <BurpSuite />,
      },
      {
        path: "/dashboard/settings/integrations/postman",
        element: <Postman />,
      },
      {
        path: "/dashboard/settings/integrations/akto_apis",
        element: <ApiTokens />,
      },
      {
        path: "/dashboard/settings/integrations/akto_gpt",
        element: <AktoGPT />,
      },
      {
        path: "/dashboard/settings/health_logs",
        element: <HealthLogs />,
      }
  ]
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