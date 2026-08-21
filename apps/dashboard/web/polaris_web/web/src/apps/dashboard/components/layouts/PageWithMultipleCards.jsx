import {  HorizontalStack,  Page, VerticalStack } from "@shopify/polaris";
import { useNavigate, useLocation, useNavigationType } from "react-router-dom";
import { learnMoreObject } from "../../../main/onboardingData"
import { getDashboardCategory } from "../../../main/labelHelper"
import LearnPopoverComponent from "./LearnPopoverComponent";
import func from  "@/util/func"
import { useEffect, useRef } from "react";

const PageWithMultipleCards = (props) => {

    const {backUrl, isFirstPage, title, titleMetadata, subtitle, primaryAction, secondaryActions, divider, components, fullWidth} = props

    const location = useLocation();
    const navigate = useNavigate()
    const navigationType = useNavigationType()
    const stack = JSON.parse(sessionStorage.getItem('pathnameStack') || '[]');
    const isNewTab = location.key === 'default' || stack.length <= 1

    const prevPathRef = useRef();

    // Track pathnames in sessionStorage
    const MAX_STACK_SIZE = 50; // Maximum number of entries in the stack

    // Includes location.search, not just pathname — a page whose identity depends on its query
    // params (e.g. an asset-detail page keyed by ?groupKey=...&rowType=...) would otherwise have
    // its own stack entry silently stripped down to the bare route, so navigating back to it from
    // one page further in (e.g. Inventory's own back button) lands on a query-string-less URL that
    // page can't actually render (reproduced: "groupKey and rowType are required"). For every page
    // that doesn't use query params, `search` is always "", so this is a no-op there.
    //
    // navigationType === 'REPLACE' updates the top entry in place instead of pushing a new one —
    // several table components (GithubServerTable) sync their own filter/sort state into the URL
    // via a replace navigation shortly after mount, which still changes location.search here even
    // though it isn't a real navigation the back arrow should have to step through. Without this,
    // that self-correction pushed a near-duplicate entry for "the same page" every time, requiring
    // the back arrow to be pressed twice to actually leave it (confirmed: pressing back once from
    // one hop further in landed on this page's own pre-self-correction URL, not the true prior page).
    useEffect(() => {
        let stack = JSON.parse(sessionStorage.getItem('pathnameStack') || '[]');
        const currentPath = location.pathname + location.search;
        if (navigationType === 'REPLACE' && stack.length > 0) {
            stack[stack.length - 1] = currentPath;
            sessionStorage.setItem('pathnameStack', JSON.stringify(stack));
        } else if (stack.length === 0 || stack[stack.length - 1] !== currentPath) {
            stack.push(currentPath);
            // Trim the stack to the maximum size
            if (stack.length > MAX_STACK_SIZE) {
                stack.shift(); // Remove the oldest entry
            }
            sessionStorage.setItem('pathnameStack', JSON.stringify(stack));
        }
        prevPathRef.current = currentPath;
    }, [location.pathname, location.search, navigationType]);

    // Custom navigateBack: skip over same-pathname entries
    const navigateBack = () => {
        let stack = JSON.parse(sessionStorage.getItem('pathnameStack') || '[]');
        const currentPath = location.pathname + location.search;
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


    const pathKey = func.transformString(location.pathname)
    const category = getDashboardCategory()
    const categoryKey = category?.toLowerCase().replace(/ /g, '_')

    let learnMoreObj = null
    let pageData = learnMoreObject?.[pathKey]

    // Fallback for sensitive data types - if specific type not found, use generic datatype config
    if (!pageData && pathKey.startsWith('dashboard_observe_sensitive_') && pathKey !== 'dashboard_observe_sensitive') {
        pageData = learnMoreObject?.['dashboard_observe_sensitive_datatype']
    }

    // Fallback for DAST: use API Security docs if DAST docs don't exist
    if (pageData && categoryKey === 'dast' && !pageData[categoryKey] && pageData['api_security']) {
        pageData = { ...pageData, dast: pageData['api_security'] };
    }

    if (pageData) {
        // Check if category-specific data exists
        if (pageData[categoryKey] && typeof pageData[categoryKey] === 'object') {
            // Use ONLY category-specific data (no merge with root-level)
            const categoryData = pageData[categoryKey]
            learnMoreObj = {
                title: categoryData.title,
                description: categoryData.description,
                docsLink: Array.isArray(categoryData.docsLink) ? categoryData.docsLink : [],
                videoLink: Array.isArray(categoryData.videoLink) ? categoryData.videoLink : []
            }
        } else {
            // Fallback to root-level data (for categories without specific config)
            // Only if root-level arrays actually exist
            const hasRootDocs = Array.isArray(pageData.docsLink);
            const hasRootVideos = Array.isArray(pageData.videoLink);

            if (hasRootDocs || hasRootVideos) {
                learnMoreObj = {
                    title: pageData.title,
                    description: pageData.description,
                    docsLink: hasRootDocs ? pageData.docsLink : [],
                    videoLink: hasRootVideos ? pageData.videoLink : []
                }
            }
        }
    }

    // Check if learnMoreObj has actual content (non-empty docs or videos)
    const hasContent = learnMoreObj && (
        (learnMoreObj.docsLink && learnMoreObj.docsLink.length > 0) ||
        (learnMoreObj.videoLink && learnMoreObj.videoLink.length > 0)
    )

    const learnMoreComp = (
        hasContent ?
            <LearnPopoverComponent learnMoreObj={learnMoreObj} /> : null
    )

    const useSecondaryActions = (
        <HorizontalStack gap={2}>
            {secondaryActions}
            {learnMoreComp}
        </HorizontalStack>
    )

    return (
        <Page fullWidth={fullWidth === undefined ? true: fullWidth}
            title={title}
            titleMetadata={titleMetadata}
            subtitle={subtitle}
            backAction={getBackAction()}
            primaryAction={primaryAction}
            secondaryActions={useSecondaryActions}
            divider={divider}
        >
            <VerticalStack gap="4">
                {components?.map((component, index) => {
                    // If component already has a key, return as is, otherwise add index-based key
                    return component;
                })}
            </VerticalStack>
        </Page>
    )
}

export default PageWithMultipleCards