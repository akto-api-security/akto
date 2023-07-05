import HomePage from "../dashboard/pages/home/HomePage"
import TestRunsPage from "../dashboard/pages/testing/TestRunsPage";
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
import Postman from "../dashboard/pages/settings/integrations/Postman";
import ApiTokens from "../dashboard/pages/settings/integrations/ApiTokens";

const router = createBrowserRouter([
  {
    path: "/dashboard",
    element: <HomePage />,
    children: [
      {
        path: "/dashboard/testing",
        element: <TestRunsPage/>
      },
      {
        path: "/dashboard/settings",
        element: <Settings/>,
        children: [
          {
            path: "/dashboard/settings/integrations",
            element: <Integrations/>,
          },
          {
            path: "/dashboard/settings/integrations/burp",
            element: <BurpSuite/>,
          },
          {
            path: "/dashboard/settings/integrations/postman",
            element: <Postman/>,
          },
          {
            path: "/dashboard/settings/integrations/akto_apis",
            element: <ApiTokens/>,
          }
        ]
      }
    ]
  },
  {
    path:"/login",
    element: <SignUp/>,
  },
  {
    path:"/",
    element:<Navigate to="/login" />,
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