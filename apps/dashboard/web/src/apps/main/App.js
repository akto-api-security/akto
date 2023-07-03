import HomePage from "../dashboard/pages/home/HomePage"
import TestRunsPage from "../dashboard/pages/testing/TestRunsPage";
import { AppProvider } from "@shopify/polaris"
import SignUp from "../signup/pages/SignUp"
import {
  createBrowserRouter,
  RouterProvider,
  Navigate
} from "react-router-dom";

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