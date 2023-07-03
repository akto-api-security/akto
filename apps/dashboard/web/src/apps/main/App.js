import HomePage from "../dashboard/pages/home/HomePage"
import TestRunsPage from "../dashboard/pages/testing/TestRunsPage";
import { AppProvider } from "@shopify/polaris"
import SignUp from "../signup/pages/SignUp"
import {
  createBrowserRouter,
  RouterProvider,
} from "react-router-dom";
import Settings from "../dashboard/pages/settings/Settings";
import Users from "../dashboard/pages/settings/Users";
import Integrations from "../dashboard/pages/settings/Integrations";

const router = createBrowserRouter([
  {
    path: "/",
    element: <HomePage />,
    children: [
      {
        path: "/tests/result",
        element: <TestRunsPage/>
      },
      {
        path: "/settings",
        element: <Settings/>,
        children: [
          {
            path: "/settings/users",
            element: <Users/>
          },
        ]
      }
    ]
  },
  {
    path:"/login",
    element: <SignUp/>,
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