import { HorizontalStack, Icon, LegacyCard, Text, VerticalStack } from "@shopify/polaris"
import { useEffect, useState } from "react"
import transform from "../../pages/testing/transform"

function MoreInformationComponent(props) {

    const { moreInfoSections, data, runIssues } = props
    console.log(moreInfoSections)
    const [infoState, setInfoState] = useState(moreInfoSections)

    const fetchData = async() =>{
        let updatedInformation = await transform.fillMoreInformation(data, runIssues, moreInfoSections)
        setInfoState(updatedInformation)
    }

    useEffect(()=>{
        fetchData()
    },[props])

    console.log(infoState)

    return (
      <VerticalStack gap={"4"}>
        <Text variant='headingMd'>
          More information
        </Text>
        <LegacyCard>
          <LegacyCard.Section>
            {
              Object.keys(infoState).map((section) => {
                return (<LegacyCard.Subsection key={infoState[section]?.title}>
                  <VerticalStack gap="3">
                    <HorizontalStack gap="2" align="start" blockAlign='start'>
                      <div style={{ maxWidth: "0.875rem", maxHeight: "0.875rem" }}>
                        {infoState[section]?.icon && <Icon source={infoState[section]?.icon}></Icon>}
                      </div>
                      <Text variant='headingSm'>
                        {infoState[section]?.title || "Heading"}
                      </Text>
                    </HorizontalStack>
                    {infoState[section]?.content}
                  </VerticalStack>
                </LegacyCard.Subsection>)
              })
            }
          </LegacyCard.Section>
        </LegacyCard>
      </VerticalStack>
    )
}

export default MoreInformationComponent