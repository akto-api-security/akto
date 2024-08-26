import {  HorizontalStack,  Page, VerticalStack } from "@shopify/polaris";
import { useNavigate, useLocation } from "react-router-dom";
import { learnMoreObject } from "../../../main/onboardingData"
import LearnPopoverComponent from "./LearnPopoverComponent";
import func from  "@/util/func"

const PageWithMultipleCards = (props) => {

    const {backUrl, isFirstPage, title, primaryAction, secondaryActions, divider, components, fullWidth} = props

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
        console.log("isNewTab",isNewTab)
        return isNewTab || isFirstPage ? null : { onAction: navigateBack }
    }


    const learnMoreObj = learnMoreObject.hasOwnProperty(func.transformString(location.pathname)) ? learnMoreObject[func.transformString(location.pathname)] : null

    const learnMoreComp = (
        learnMoreObj ?
        <LearnPopoverComponent learnMoreObj={learnMoreObj} /> : null
    )

    const useSecondaryActions = (
        <HorizontalStack gap={2}>
            {learnMoreObj ? learnMoreComp : null }
            {secondaryActions}
        </HorizontalStack>
    )

    return (
        <Page fullWidth={fullWidth === undefined ? true: fullWidth}
            title={title}
            backAction={getBackAction()}
            primaryAction={primaryAction}
            secondaryActions={useSecondaryActions}
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