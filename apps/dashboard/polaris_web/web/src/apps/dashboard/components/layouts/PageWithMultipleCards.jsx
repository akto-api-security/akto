import { Page, VerticalStack } from "@shopify/polaris";
import { useNavigate, useLocation } from "react-router-dom";

const PageWithMultipleCards = (props) => {

    const {backUrl, isFirstPage, title, primaryAction, secondaryActions, divider, components} = props

    const location = useLocation();
    const navigate = useNavigate()
    const isNewTab = location.key==='default' || !(window.history.state && window.history.state.idx > 0)

    const navigateBack = () => {
        navigate(-1)
    }

    function getBackAction() {
        if(backUrl){
            return { onAction: ()=>navigate(backUrl) }
        }
        return isNewTab || isFirstPage ? null : { onAction: navigateBack }
    }

    return (
        <Page fullWidth
            title={title}
            backAction={getBackAction()}
            primaryAction={primaryAction}
            secondaryActions={secondaryActions}
            divider={divider}
        >
            <VerticalStack gap="4">
                {components?.filter((component) => {
                    return component
                })}
            </VerticalStack>
        </Page>
    )
}

export default PageWithMultipleCards