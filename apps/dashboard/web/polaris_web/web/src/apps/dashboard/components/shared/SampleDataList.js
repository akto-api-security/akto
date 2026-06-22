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
import ValidationReasonBanner from './ValidationReasonBanner';
import func from '../../../../util/func';
import { getDashboardCategory, mapLabel, isAgenticSecurityCategory, isEndpointSecurityCategory } from '../../../main/labelHelper';

function SchemaValidationError({ sampleData}) {
    const [expanded, setExpanded] = useState(false);

    if (!sampleData || !sampleData?.metadata) {
        return null;
    }
    const schemaErrors = JSON.parse(sampleData?.metadata)?.schemaErrors || [];
    
    // Only show actual schema validation errors
    const schemaValidationErrors = schemaErrors.filter(error => error?.attribute !== 'threat_detected');
    
    if (schemaValidationErrors.length === 0) {
        return null;
    }

    const isGuardrails = isAgenticSecurityCategory() || isEndpointSecurityCategory();
    const visibleErrors = (isGuardrails && !expanded) ? schemaValidationErrors.slice(0, 1) : schemaValidationErrors;
    const hiddenCount = schemaValidationErrors.length - 1;

    return (
        <VerticalStack gap={"4"}>
            <Banner
                title={isGuardrails ? "Guardrails Violations" : "Schema Validation Errors"}
                status="critical"
            >
                <List type="bullet">
                    {visibleErrors.map((error, index) => {
                        return <List.Item key={index}>{error?.message}</List.Item>
                    })}
                    {isGuardrails && hiddenCount > 0 && (
                        <List.Item>
                            <button
                                onClick={() => setExpanded(e => !e)}
                                style={{background: 'none', border: 'none', padding: 0, cursor: 'pointer', color: 'inherit', textDecoration: 'underline'}}
                            >
                                {expanded ? 'Show less' : `${hiddenCount} more violation${hiddenCount > 1 ? 's' : ''}`}
                            </button>
                        </List.Item>
                    )}
                </List>
            </Banner>

        </VerticalStack>
    )
}

function VulnerabilityEvidence({ segments }) {
    if (!Array.isArray(segments) || segments.length === 0) {
        return null;
    }

    return (
        <Banner title="Evidence" status="warning">
            <VerticalStack gap="2">
                {segments.map((seg, index) => {
                    const isRequest = String(seg?.location || 'RESPONSE').toUpperCase() === 'REQUEST';
                    const reason = typeof seg?.reason === 'string' ? seg.reason : '';
                    const phrase = typeof seg?.phrase === 'string' ? seg.phrase : '';
                    return (
                        <Box key={index}>
                            <HorizontalStack gap="2" align="start" blockAlign="start" wrap={false}>
                                <Box paddingBlockStart="05">
                                    <Badge status={isRequest ? 'attention' : 'critical'}>
                                        {isRequest ? 'Request' : 'Response'}
                                    </Badge>
                                </Box>
                                <VerticalStack gap="0">
                                    {reason ? <Text variant="bodyMd">{reason}</Text> : null}
                                    {phrase ? (
                                        <span style={{ fontFamily: 'monospace', fontSize: '12px', color: '#6B46C1', wordBreak: 'break-all' }}>
                                            {phrase}
                                        </span>
                                    ) : null}
                                </VerticalStack>
                            </HorizontalStack>
                        </Box>
                    );
                })}
            </VerticalStack>
        </Banner>
    );
}

function SampleDataList(props) {

    const {showDiff, sampleData, heading, minHeight, vertical, isVulnerable, isNewDiff, metadata, redactHeaders = [], isWebSocket: isWebSocketProp} = props;

    const [page, setPage] = useState(0);

    useEffect(() => {
      setPage(0);
    }, [sampleData])
  
    const currentSample = sampleData[Math.min(page, sampleData.length - 1)];
    const parsedSample = func.parseWebSocketSampleMessage(currentSample?.message)
    const isWebSocket = isWebSocketProp === true || func.isWebSocketApiType(parsedSample?.type)
    const panelTypes = isWebSocket ? ['events'] : ['request', 'response'];

    return (
      <VerticalStack gap="3">
         <SchemaValidationError sampleData={currentSample} />
         <VulnerabilityEvidence segments={currentSample?.vulnerabilitySegments} />
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
            panelTypes.map((type) => {
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
                      redactHeaders={redactHeaders}
                      isWebSocket={isWebSocket}
                    />
                  </LegacyCard>
                </Box>
              )
            })
          }
        </HorizontalGrid>
        <ValidationReasonBanner
          validationReason={currentSample?.validationReason}
          isVulnerable={isVulnerable}
        />
      </VerticalStack>
    )
  }

  export default SampleDataList