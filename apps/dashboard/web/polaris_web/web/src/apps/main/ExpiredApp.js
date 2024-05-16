import { Link, Text } from "@shopify/polaris";
import EmptyScreensLayout from "../dashboard/components/banners/EmptyScreensLayout";
import {
    createBrowserRouter,
    RouterProvider
} from "react-router-dom";

function ExpiredApp() {
    const message = <Text>To continue using Akto, please reach out to <Link url="mailto:support@akto.io">support@akto.io</Link>.</Text>
    const router = createBrowserRouter([
        {
            path: "*",
            element: <div style={{
                padding: "80px"
            }}>
                <EmptyScreensLayout key={"emptyScreen"}
                    iconSrc={"/public/upgrade.svg"}
                    headingText={"Hey! Your free trial has expired"}
                    description={message}
                />
            </div>
        }
    ])

    return (
        <RouterProvider router={router} />
    )
}

export default ExpiredApp;