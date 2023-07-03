import HomePage from "../dashboard/pages/home/HomePage"
import TestRunsPage from "../dashboard/pages/testing/TestRunsPage";
import { AppProvider } from "@shopify/polaris"
import SignUp from "../signup/pages/SignUp"
import {
  createBrowserRouter,
  RouterProvider,
  Navigate
} from "react-router-dom";
import BurpSuite from "../dashboard/components/settings/BurpSuite";
import Integrations from "../dashboard/components/settings/Integrations";

const router = createBrowserRouter([
  {
    path: "/dashboard",
    element: <HomePage />,
    children: [
      {
        path: "/dashboard/testing",
        element: <TestRunsPage/>
      }
    ]
  },
  {
    path:"/login",
    element: <SignUp/>,
  },
  {
    path: "/settings/integrations",
    element: <Integrations />
  },
  {
    path:"/settings/integrations/burp",
    element: <BurpSuite />,
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