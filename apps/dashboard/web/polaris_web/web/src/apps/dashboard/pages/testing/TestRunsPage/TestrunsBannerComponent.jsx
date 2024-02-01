import React from 'react'
import BannerLayout from '../../../components/banners/BannerLayout'
import { TESTING_VIDEO_LENGTH, TESTING_VIDEO_URL, TESTING_VIDEO_THUMBNAIL } from '../../../../main/onboardingData'

function TestrunsBannerComponent() {
    return (
        <BannerLayout
            title={"Test your APIs"}
            text={"150+ built-in tests covering OWASP Top 10, HackerOne top 10 and all the business logic vulnerabilities for your API Security testing needs."}
            buttonText={"Go to inventory"}
            buttonUrl={"/dashboard/observe/inventory"}
            videoLength={TESTING_VIDEO_LENGTH}
            videoLink={TESTING_VIDEO_URL}
            videoThumbnail={TESTING_VIDEO_THUMBNAIL} 
        />
    )
}

export default TestrunsBannerComponent