import React from 'react'
import Auth0Redirects from '../components/Auth0Redirects'

function PageCheckInbox() {
    return (
        <Auth0Redirects
            errorText={"Check your email"}
            bodyText={"We sent you a temporary verification link to your email. Please click on the link to activate your account. Can't find it? Check your spam folder or contact us at support@akto.io with your signup address."}
        />
    )
}

export default PageCheckInbox