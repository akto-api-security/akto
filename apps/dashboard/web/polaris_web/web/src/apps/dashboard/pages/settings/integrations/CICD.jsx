import React from 'react'
import TokensLayout from './TokensLayout'
import func from '@/util/func';

function CICD() {
    let cardContent = "Seamlessly enhance your web application security with CI/CD integration, empowering you to efficiently detect vulnerabilities, analyze and intercept web traffic, and fortify your digital defenses.  "
  return (
    <TokensLayout type={func.testingResultType().CICD} title="CI/CD" cardContent={cardContent} docsUrl="https://docs.akto.io/testing/run-tests-in-cicd"/>
  )
}

export default CICD