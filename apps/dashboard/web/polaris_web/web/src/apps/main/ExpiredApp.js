import { Link, Text } from "@shopify/polaris";
import EmptyScreensLayout from "../dashboard/components/banners/EmptyScreensLayout";
import {
    createBrowserRouter,
    RouterProvider
} from "react-router-dom";

function ExpiredApp() {
    const message = <Text>Please upgrade to the latest version to continue using Akto, <Link url="https://docs.akto.io/support">learn more</Link>. For any queries contact <Link url="mailto:support@akto.io">support@akto.io</Link>.</Text>
    const router = createBrowserRouter([
        {
            path: "*",
            element: <div style={{
                padding: "80px"
            }}>
                <EmptyScreensLayout key={"emptyScreen"}
                    iconSrc={"/public/upgrade.svg"}
                    headingText={"Hey! we noticed you're on an unsupported version"}
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