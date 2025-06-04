import {  HorizontalStack,  Page, VerticalStack } from "@shopify/polaris";
import { useNavigate, useLocation } from "react-router-dom";
import { learnMoreObject } from "../../../main/onboardingData"
import LearnPopoverComponent from "./LearnPopoverComponent";
import func from  "@/util/func"
import { useEffect, useRef } from "react";

const PageWithMultipleCards = (props) => {

    const {backUrl, isFirstPage, title, primaryAction, secondaryActions, divider, components, fullWidth} = props

    const location = useLocation();
    const navigate = useNavigate()
    const isNewTab = location.key==='default' || !(window.history.state && window.history.state.idx > 0)

    const prevPathRef = useRef();

    // Track pathnames in sessionStorage
    const MAX_STACK_SIZE = 50; // Maximum number of entries in the stack

    useEffect(() => {
        let stack = JSON.parse(sessionStorage.getItem('pathnameStack') || '[]');
        const currentPath = location.pathname;
        if (stack.length === 0 || stack[stack.length - 1] !== currentPath) {
            stack.push(currentPath);
            // Trim the stack to the maximum size
            if (stack.length > MAX_STACK_SIZE) {
                stack.shift(); // Remove the oldest entry
            }
            sessionStorage.setItem('pathnameStack', JSON.stringify(stack));
        }
        prevPathRef.current = currentPath;
    }, [location.pathname]);

    // Custom navigateBack: skip over same-pathname entries
    const navigateBack = () => {
        let stack = JSON.parse(sessionStorage.getItem('pathnameStack') || '[]');
        const currentPath = location.pathname;
        // Remove current path
        while (stack.length > 0 && stack[stack.length - 1] === currentPath) {
            stack.pop();
        }
        // Find last different path
        const lastDifferent = stack.length > 0 ? stack[stack.length - 1] : null;
        sessionStorage.setItem('pathnameStack', JSON.stringify(stack));
        if (lastDifferent && lastDifferent !== currentPath) {
            navigate(lastDifferent);
        } else {
            navigate(-1);
        }
    }

    function getBackAction() {
        if(backUrl){
            return { onAction: ()=>navigate(backUrl) }
        }
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