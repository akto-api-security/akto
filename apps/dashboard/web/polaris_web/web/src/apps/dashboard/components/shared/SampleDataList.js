import React, { useState, useEffect, useMemo } from 'react'
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
import transform from './customDiffEditor';
import { filterLocatableSegments } from './vulnerabilityEvidence';

// Evidence panel is internal-only until the feature is generally available.
const SHOW_VULNERABILITY_EVIDENCE = func.isAktoUser();
// Good-to-have, off by default: when the model reports low confidence in a piece
// of evidence, show a subtle hint nudging the user toward the existing
// Triage -> "False positive" action. The framework verdict stays authoritative.
const SHOW_LOW_CONFIDENCE_HINT = false;

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

    const hasLowConfidence = SHOW_LOW_CONFIDENCE_HINT
        && segments.some((seg) => String(seg?.confidence || '').toLowerCase() === 'low');

    return (
        <Banner title="Evidence" status="warning">
            <VerticalStack gap="2">
                {segments.map((seg, index) => {
                    const isRequest = String(seg?.location || 'RESPONSE').toUpperCase() === 'REQUEST';
                    const reason = typeof seg?.reason === 'string' ? seg.reason : '';
                    const phrase = typeof seg?.phrase === 'string' ? seg.phrase : '';
                    const field = typeof seg?.field === 'string' ? seg.field : '';
                    const isInformational = seg?.informational === true;
                    // Prefer showing the concrete value; if it is masked/absent, show the field so the row is never blank.
                    const evidenceText = phrase || field;
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
                                    {/* Informational evidence (e.g. missing headers) describes something absent,
                                        so render the header list verbatim without the locatable "field: phrase" form. */}
                                    {isInformational ? (
                                        phrase ? (
                                            <span style={{ fontFamily: 'monospace', fontSize: '12px', color: '#6B46C1', wordBreak: 'break-all' }}>
                                                {phrase}
                                            </span>
                                        ) : null
                                    ) : evidenceText ? (
                                        <span style={{ fontFamily: 'monospace', fontSize: '12px', color: '#6B46C1', wordBreak: 'break-all' }}>
                                            {field && phrase ? `${field}: ${phrase}` : evidenceText}
                                        </span>
                                    ) : null}
                                </VerticalStack>
                            </HorizontalStack>
                        </Box>
                    );
                })}
                {hasLowConfidence ? (
                    <Box paddingBlockStart="1">
                        <Text variant="bodySm" color="subdued">
                            Some evidence is low-confidence. If this looks like a false positive, use Triage - "False positive".
                        </Text>
                    </Box>
                ) : null}
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

    // Verification contract (keystone): only ever show evidence that can actually
    // be located in the SAME text the editor renders. We validate REQUEST segments
    // against the rendered request text and RESPONSE segments against the response
    // text (and drop denylisted credential/token segments), then feed the identical
    // validated list to BOTH the Evidence panel and the request/response editors -
    // so the panel never lists a segment the editor cannot highlight.
    const validatedCurrentSample = useMemo(() => {
        if (!currentSample) {
            return currentSample;
        }
        const segments = currentSample.vulnerabilitySegments;
        if (!Array.isArray(segments) || segments.length === 0 || isWebSocket) {
            return currentSample;
        }
        const { requestText, responseText } = transform.buildRenderedText(currentSample, { isNewDiff, redactHeaders, metadata });
        // Informational segments (e.g. missing-header evidence) describe something
        // ABSENT, so there is nothing to locate/highlight - they bypass the
        // locatability filter and are panel-only. Everything else must be locatable.
        const informationalSegments = segments.filter(s => s?.informational === true);
        const locatableInput = segments.filter(s => s?.informational !== true);
        const requestSegments = locatableInput.filter(s => String(s?.location || '').toUpperCase() === 'REQUEST');
        const responseSegments = locatableInput.filter(s => String(s?.location || '').toUpperCase() !== 'REQUEST');
        const validated = [
            ...informationalSegments,
            ...filterLocatableSegments(requestSegments, requestText),
            ...filterLocatableSegments(responseSegments, responseText),
        ];
        return { ...currentSample, vulnerabilitySegments: validated };
    }, [currentSample, isNewDiff, redactHeaders, metadata, isWebSocket]);

    return (
      <VerticalStack gap="3">
         <SchemaValidationError sampleData={currentSample} />
         {SHOW_VULNERABILITY_EVIDENCE ? (
           <VulnerabilityEvidence segments={validatedCurrentSample?.vulnerabilitySegments} />
         ) : null}
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
                      sampleData={validatedCurrentSample}
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