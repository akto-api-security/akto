import React from 'react'
import BannerLayout from '../../../components/banners/BannerLayout'

function TestrunsBannerComponent() {
    return (
        <BannerLayout
            title={"Test your APIs"}
            text={"150+ built-in tests covering OWASP Top 10, HackerOne top 10 and all the business logic vulnerabilities for your API Security testing needs."}
            buttonText={"Go to inventory"}
            buttonUrl={"/dashboard/observe/inventory"} 
        />
    )
}

export default TestrunsBannerComponent