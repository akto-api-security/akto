import React from 'react'
import Auth0Redirects from '../components/Auth0Redirects'

function PageBusinessEmail() {
    return (
        <Auth0Redirects
            errorText={"Please sign up with a business email only"}
            bodyText={"Try again with a different email id"}
        />
    )
}

export default PageBusinessEmail