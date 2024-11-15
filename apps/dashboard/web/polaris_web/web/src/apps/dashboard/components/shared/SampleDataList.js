import React, { useState, useEffect } from 'react'
import {
  Text,
  BlockStack,
  InlineStack, Box, LegacyCard, InlineGrid,
  Pagination, Key, Badge} from '@shopify/polaris';
import SampleDataComponent from './SampleDataComponent';

function SampleDataList(props) {

    const {showDiff, sampleData, heading, minHeight, vertical, isVulnerable, isNewDiff} = props;

    const [page, setPage] = useState(0);

    useEffect(() => {
      setPage(0);
    }, [sampleData])
  
    return (
      <BlockStack gap="3">
        <InlineStack align='space-between'>
          <InlineStack gap="2">
            <Text variant='headingMd'>
              {heading}
            </Text>
            {isVulnerable ? <Box paddingBlockStart={"05"}><Badge tone="critical">Vulnerable</Badge></Box> : null}
          </InlineStack>
          <Pagination
                  label={
                    sampleData?.length==0 ? 'No test runs found' :
                    `${page+1} of ${sampleData?.length}`
                  }
                  hasPrevious = {page > 0}
                  previousKeys={[Key.LeftArrow]}
                  onPrevious={() => {setPage((old) => (old-1))}}
                  hasNext = {sampleData?.length > (page+1)}
                  nextKeys={[Key.RightArrow]}
                  onNext={() => {setPage((old) => (old+1))}}
                />
        </InlineStack>
        <InlineGrid columns={vertical ? "1" : "2"} gap="2">
          {
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
                    />
                  </LegacyCard>
                </Box>
              )
            })
          }
        </InlineGrid>
      </BlockStack>
    );
  }

  export default SampleDataList