import React from 'react'
import Auth0Redirects from '../components/Auth0Redirects'
import ContactSales from '../components/ContactSales'
import { useSearchParams } from 'react-router-dom'

function PageBusinessEmail() {
    const [searchParams] = useSearchParams();
    const showContactSales = searchParams.get('contact') === 'true';
    
    if (showContactSales) {
        return <ContactSales />;
    }
    
    return (
        <Auth0Redirects
            errorText={"Please sign up with a business email only"}
            bodyText={"Please sign up with a business email only. Try again with a different email id or contact support@akto.io for help."}
        />
    )
}

export default PageBusinessEmail