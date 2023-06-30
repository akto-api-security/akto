import HomePage from "../dashboard/pages/home/HomePage"
import SignUp from "../signup/pages/SignUp"
import {AppProvider, Button} from "@shopify/polaris"
import {
  createBrowserRouter,
  RouterProvider,
} from "react-router-dom";
import Store from "../dashboard/store";

const TestToast = () => {
  const setToastConfig = Store(state => state.setToastConfig)

  return (
    <Button onClick={() => setToastConfig({isActive: true, isError: true, message: "This is a test"})}>
      Show Toast
    </Button>
  )
}

const router = createBrowserRouter([
  {
    path: "/",
    element: <HomePage />,
    children: [
      {
        path: "/test",
        element: <TestToast/>
      }
    ]
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