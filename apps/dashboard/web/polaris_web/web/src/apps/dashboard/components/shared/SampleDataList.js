import React, { useState, useEffect } from 'react'
import {
  Banner,
  List,
  Text,
  VerticalStack,
  HorizontalStack, Box, LegacyCard, HorizontalGrid,
  Pagination, Key, Badge} from '@shopify/polaris';
import SampleDataComponent from './SampleDataComponent';
import SampleData from './SampleData';
import func from '../../../../util/func';
import { getDashboardCategory, mapLabel } from '../../../main/labelHelper';

function SchemaValidationError({ sampleData}) {
    if (!sampleData || !sampleData?.metadata) {
        return null;
    }
    const schemaErrors = JSON.parse(sampleData?.metadata)?.schemaErrors || [];
    
    // Only show actual schema validation errors
    const schemaValidationErrors = schemaErrors.filter(error => error?.attribute !== 'threat_detected');
    
    if (schemaValidationErrors.length === 0) {
        return null;
    }


    return (
        <VerticalStack gap={"4"}>
            <Banner
                title="Schema Validation Errors"
                status="critical"
            >
                <List type="bullet">
                    {schemaValidationErrors?.map((error, index) => {
                        return <List.Item key={index}>{error?.message}</List.Item>
                    })}
                </List>
            </Banner>

        </VerticalStack>
    )
}

function SampleDataList(props) {

    const {showDiff, sampleData, heading, minHeight, vertical, isVulnerable, isNewDiff, metadata} = props;

    const [page, setPage] = useState(0);

    useEffect(() => {
      setPage(0);
    }, [sampleData])
  
    return (
      <VerticalStack gap="3">
         <SchemaValidationError sampleData={sampleData[Math.min(page, sampleData.length - 1)]} />
        <HorizontalStack align='space-between'>
          <HorizontalStack gap="2">
            <Text variant='headingMd'>
              {heading}
            </Text>
            {isVulnerable ? <Box paddingBlockStart={"05"}><Badge status="critical">Vulnerable</Badge></Box> : null}
          </HorizontalStack>
        <Pagination
                label={
                  sampleData?.length==0 ? 'No ' + mapLabel("Test runs", getDashboardCategory()) + " found" :
                  `${page+1} of ${sampleData?.length}`
                }
                hasPrevious = {page > 0}
                previousKeys={[Key.LeftArrow]}
                onPrevious={() => {setPage((old) => (old-1))}}
                hasNext = {sampleData?.length > (page+1)}
                nextKeys={[Key.RightArrow]}
                onNext={() => {setPage((old) => (old+1))}}
              />
        </HorizontalStack>
        <HorizontalGrid columns={vertical ? "1" : "2"} gap="2">
          {
            sampleData[Math.min(page, sampleData.length - 1)]?.errorList != undefined ?
              <Box key={"errorList"}>
                <LegacyCard>
                  <Box id='error-editor-container'>
                    <LegacyCard.Section flush>
                      <Box paddingInlineEnd={"2"} paddingInlineStart={"2"} paddingBlockEnd={"3"} paddingBlockStart={"3"}>
                        <HorizontalStack padding="2" align='space-between'>
                          {func.toSentenceCase("Errors")}
                        </HorizontalStack>
                      </Box>
                    </LegacyCard.Section>
                    <LegacyCard.Section flush>
                      <SampleData data={{ original: sampleData[Math.min(page, sampleData.length - 1)]?.errorList }}
                        language="yaml" minHeight={minHeight} wordWrap={false} />
                    </LegacyCard.Section>
                  </Box>
                </LegacyCard>
              </Box>
              :
            ["request","response"].map((type) => {
              return (
                <Box key={type}>
                  <LegacyCard>
                    <SampleDataComponent
                      type={type}
                      sampleData={sampleData[Math.min(page, sampleData.length-1)]}
                      minHeight={minHeight}
                      showDiff={showDiff}
                      isNewDiff={isNewDiff}
                      metadata={metadata}
                      readOnly={true}
                    />
                  </LegacyCard>
                </Box>
              )
            })
          }
        </HorizontalGrid>
      </VerticalStack>
    )
  }

  export default SampleDataList