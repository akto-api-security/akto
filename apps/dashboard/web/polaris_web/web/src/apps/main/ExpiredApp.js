import { Link, Text } from "@shopify/polaris";
import EmptyScreensLayout from "../dashboard/components/banners/EmptyScreensLayout";
import {
    createBrowserRouter,
    RouterProvider
} from "react-router-dom";

function ExpiredApp() {
    const message = <Text>Please upgrade to the latest version to continue using akto. For any queries contact <Link url="mailto:support@akto.io">support@akto.io</Link>.</Text>
    const router = createBrowserRouter([
        {
            path: "*",
            element: <div style={{
                padding: "80px"
            }}>
                <EmptyScreensLayout key={"emptyScreen"}
                    iconSrc={"/public/upgrade.svg"}
                    headingText={"Upgrade to latest version"}
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