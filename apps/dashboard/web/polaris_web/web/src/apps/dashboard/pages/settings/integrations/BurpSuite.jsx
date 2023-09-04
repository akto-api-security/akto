import React from 'react'
import TokensLayout from './TokensLayout'
import func from '@/util/func';

function BurpSuite() {
    let cardContent = "Seamlessly enhance your web application security with Burp Suite integration, empowering you to efficiently detect vulnerabilities, analyze and intercept web traffic, and fortify your digital defenses. "
  return (
    <TokensLayout type={func.testingResultType().BURP} title="Burp Suite" cardContent={cardContent} docsUrl="https://docs.akto.io/traffic-connections/burp-suite"/>
  )
}

export default BurpSuite