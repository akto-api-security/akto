import React from 'react'
import Auth0Redirects from '../components/Auth0Redirects'

function PageSsoOnlyLogin() {
    return (
        <Auth0Redirects
            errorText={"SSO login required"}
            bodyText={"Your organization requires sign-in via SSO. Please use your organization's SSO to sign in."}
        />
    )
}

export default PageSsoOnlyLogin
