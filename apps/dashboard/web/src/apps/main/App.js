import HomePage from "../dashboard/pages/home/HomePage"
import SignUp from "../signup/pages/SignUp"
import {AppProvider, Button} from "@shopify/polaris"
import {
  createBrowserRouter,
  RouterProvider,
} from "react-router-dom";

const router = createBrowserRouter([
  {
    path: "/",
    element: <HomePage />
  },
  {
    path:"/login",
    element: <SignUp/>,
  },
])

function App() {
  return (
    <AppProvider>
      <RouterProvider router={router} />
    </AppProvider>
  );
}

export default App;