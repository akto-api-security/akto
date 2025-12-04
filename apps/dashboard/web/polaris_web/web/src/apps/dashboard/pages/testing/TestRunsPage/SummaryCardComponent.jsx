import React, { memo } from 'react';
import { Text, LegacyCard, HorizontalStack, Button, Collapsible, HorizontalGrid, Box, Divider, VerticalStack } from '@shopify/polaris';
import { ChevronDownMinor, ChevronUpMinor } from '@shopify/polaris-icons';
import ChartypeComponent from './ChartypeComponent';
import ApiCollectionCoverageGraph from './ApiCollectionCoverageGraph';
import ApisTestedOverTimeGraph from './ApisTestedOverTimeGraph';
import TestRunOverTimeGraph from './TestRunOverTimeGraph';
import CategoryWiseScoreGraph from './CategoryWiseScoreGraph';
import { isApiSecurityCategory, isDastCategory } from '../../../../main/labelHelper';
import func from '@/util/func';

// Memoize only the child components that make API calls to prevent unnecessary re-renders
const MemoizedApiCollectionCoverageGraph = memo(ApiCollectionCoverageGraph);
const MemoizedTestRunOverTimeGraph = memo(TestRunOverTimeGraph);
const MemoizedApisTestedOverTimeGraph = memo(ApisTestedOverTimeGraph);
const MemoizedCategoryWiseScoreGraph = memo(CategoryWiseScoreGraph);

const SummaryCardComponent = ({ 
  severityMap, 
  subCategoryInfo, 
  collapsible, 
  setCollapsible, 
  startTimestamp, 
  endTimestamp 
}) => {
  const totalVulnerabilities = (severityMap?.CRITICAL?.text || 0) + 
                              (severityMap?.HIGH?.text || 0) + 
                              (severityMap?.MEDIUM?.text || 0) + 
                              (severityMap?.LOW?.text || 0);
  
  const iconSource = collapsible ? ChevronUpMinor : ChevronDownMinor;
  
  return (
    <LegacyCard>
      <LegacyCard.Section title={<Text fontWeight="regular" variant="bodySm" color="subdued">Vulnerabilities</Text>}>
        <HorizontalStack align="space-between">
          <Text fontWeight="semibold" variant="bodyMd">Found {totalVulnerabilities} vulnerabilities in total</Text>
          <Button plain monochrome icon={iconSource} onClick={() => setCollapsible(!collapsible)} />
        </HorizontalStack>
        {totalVulnerabilities > 0 ? 
        <Collapsible open={collapsible} transition={{duration: '500ms', timingFunction: 'ease-in-out'}}>
          <LegacyCard.Subsection>
            <Box paddingBlockStart={3}><Divider/></Box>
            <VerticalStack gap={"5"}>
              <HorizontalGrid columns={2} gap={6}>
                <ChartypeComponent chartSize={190} navUrl={"/dashboard/issues"} data={subCategoryInfo} title={"Categories"} isNormal={true} boxHeight={'250px'}/>
                <ChartypeComponent
                    data={severityMap}
                    navUrl={"/dashboard/issues"} title={"Severity"} isNormal={true} boxHeight={'250px'} dataTableWidth="250px" boxPadding={8}
                    pieInnerSize="50%"
                    chartOnLeft={false}
                    chartSize={190}
                />
              </HorizontalGrid>
              {func.isDemoAccount() && !(isApiSecurityCategory() || isDastCategory()) ? (
                <MemoizedCategoryWiseScoreGraph 
                  key={"category-score-graph"} 
                  startTimestamp={startTimestamp} 
                  endTimestamp={endTimestamp}
                  dataSource="redteaming"
                />
              ) : null}
                {
                  func.isDemoAccount() && !(isApiSecurityCategory() || isDastCategory()) ? <></> :
                    <VerticalStack gap={4}>
                      <HorizontalGrid columns={2} gap={4}>
                        <MemoizedApiCollectionCoverageGraph />
                        <MemoizedTestRunOverTimeGraph />
                      </HorizontalGrid>
                      <MemoizedApisTestedOverTimeGraph />
                    </VerticalStack>
                }
            </VerticalStack>
          </LegacyCard.Subsection>
        </Collapsible>
        : null }
      </LegacyCard.Section>
    </LegacyCard>
  );
};

export default SummaryCardComponent;
