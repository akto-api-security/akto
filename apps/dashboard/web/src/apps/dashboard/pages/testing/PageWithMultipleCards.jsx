import { Outlet } from "react-router-dom";
import { Page } from "@shopify/polaris";

const PageWithMultipleCards = (props) => {

    return (
        <Page fullWidth
            title={props.title || "blank"}
        >
            <Outlet />
        </Page>
    )
}

export default PageWithMultipleCards