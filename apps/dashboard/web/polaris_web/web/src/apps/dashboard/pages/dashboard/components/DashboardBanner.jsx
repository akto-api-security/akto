import React from 'react'
import BannerLayout from '../../../components/banners/BannerLayout'
import { Box, Text, VerticalStack } from '@shopify/polaris'
import { AppsMinor, MarketingMinor, ResetMinor, CustomersMinor, EditMinor, NoteMinor, ReadTimeMinor, StarFilledMinor } from "@shopify/polaris-icons"
import GridRows from '../../../components/shared/GridRows'
import BannerRow from './BannerRow'
import { HOMEDASHBOARD_VIDEO_LENGTH, HOMEDASHBOARD_VIDEO_THUMBNAIL, HOMEDASHBOARD_VIDEO_URL } from '../../../../main/onboardingData'
import LocalStore from '../../../../main/LocalStorageStore'
function InfoComponent({title, items})  {
    return(
        <VerticalStack gap={4}>
            <Text variant="bodyLg" fontWeight="semibold">{title}</Text>
            <GridRows columns={3} items={items} CardComponent={BannerRow} />
        </VerticalStack>
    )
}

function DashboardBanner() {
    const subCategoryMap = LocalStore.getState().subCategoryMap;
    let defaultCount = Math.max(Object.keys(subCategoryMap).length,850);
    defaultCount = Math.floor(defaultCount / 50) * 50

    const productGuides = [
        {
            title: "Add traffic",
            icon: AppsMinor,
            showRedirect: true,
            description: "Akto Discovers API inventory by connecting to your API traffic source. Add a connector to get started.",
            redirectUrl: "/dashboard/quick-start",
        },
        {
            title: "Start testing",
            icon: MarketingMinor,
            showRedirect: true,
            description: defaultCount + "+ built-in tests covering OWASP Top 10, HackerOne top 10 and all the business logic vulnerabilities for your API Security testing needs.",
            redirectUrl: "/dashboard/testing",
        },
        {
            title: "Configure CI/CD",
            icon: ResetMinor,
            showRedirect: true,
            description: "You can trigger Akto's API Security tests in your CI/CD pipelines. Generate your Akto token and send API requests to Akto dashboard to start running tests.",
            redirectUrl: "https://docs.akto.io/testing/run-tests-in-cicd",
            newTab: true,
        },
        {
            title: "Invite team members",
            icon: CustomersMinor,
            showRedirect: true,
            description: "Invite your team to the dashboard and collaborate effortlessly.",
            redirectUrl: "/dashboard/settings/users",
        },
        {
            title: "Write custom tests",
            icon: EditMinor,
            showRedirect: true,
            description: "Akto Test Editor is the test playground for security teams and developers to write their custom tests to find vulnerabilities in APIs.",
            redirectUrl: "/dashboard/test-editor",
        }
    ]

    const websiteGuides = [
        {
            title: "Documentation",
            icon: NoteMinor,
            description: "From detailed API guides and integration instructions to feature overviews and troubleshooting tips.",
            redirectUrl: "https://docs.akto.io/readme",
            newTab: true
        },
        {
            title: "Academy",
            icon: ReadTimeMinor,
            description: "Master new skills, understand complex concepts, and stay ahead with our curated tutorials and courses.",
            redirectUrl: "https://www.akto.io/api-security/",
            newTab: true
        },
        {
            title: "Blogs",
            icon: StarFilledMinor,
            description: "Uncover insights, tips, and trends to elevate your API strategies and implementations.",
            redirectUrl: "https://www.akto.io/blog",
            newTab: true
        },
    ]

    

    const containerComp = (
        <VerticalStack gap={8}>
            <Box paddingBlockEnd={"5"} paddingBlockStart={"5"}>
                <InfoComponent items={productGuides} title={"Get started guide"} />
            </Box>
            <Box paddingBlockEnd={16}>
                <InfoComponent items={websiteGuides} title={"Resources"} />
            </Box>
        </VerticalStack>
    )

    return (
        <BannerLayout
            title="Get started with your Dashboard"
            text={"Welcome to Akto Dashboard! It will provide an overview of your API Security Posture, identify critical issues, assess risks, and monitor sensitive data."}
            linkButton={true}
            buttonText={"Learn more"}
            buttonUrl={"https://docs.akto.io/readme"}
            containerComp={containerComp}
            newTab={true}
            videoLength={HOMEDASHBOARD_VIDEO_LENGTH}
            videoThumbnail={HOMEDASHBOARD_VIDEO_THUMBNAIL}
            videoLink={HOMEDASHBOARD_VIDEO_URL}
        />
    )
}

export {DashboardBanner, InfoComponent}