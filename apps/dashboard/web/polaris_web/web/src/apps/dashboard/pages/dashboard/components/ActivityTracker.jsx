import { Avatar, Box, Button, Card, HorizontalStack, Scrollable, Text, VerticalStack } from '@shopify/polaris'
import React from 'react'
import { useNavigate } from "react-router-dom"
import func from '../../../../../util/func'
import './ActivityTracker.css';
import PersistStore from '../../../../main/PersistStore';

function ActivityTracker({latestActivity, onLoadMore, showLoadMore, collections }) {

    const navigate = useNavigate()
    const filtersMap = PersistStore(state => state.filtersMap)
    const setFiltersMap = PersistStore(state => state.setFiltersMap)

      function extractCollectionName(description) {
        const parts = description.split(' ');
        const nameIndex = parts.indexOf('named');
        if (nameIndex !== -1 && nameIndex < parts.length - 1) {
          return parts[nameIndex + 1];
        } else {
          return null;
        }
      }

      function getKeyByValue(obj, value) {
        return Object.keys(obj).find(key => obj[key] === value);
      }


      function handleActivityType(activityType, activityDescription, timestamp) {
        if (activityType === "Collection created") {
          // Perform operations for "created" type
          const collectionName = extractCollectionName(activityDescription)

          const collectionKey = getKeyByValue(collections, collectionName)

          navigate(`/dashboard/observe/inventory/${collectionKey}`);
       
        } else if (activityType === "Endpoints detected") {
        

          navigate(`/dashboard/observe/changes`,{ state:{tab:0, timestamp: timestamp}});

  
        }
        else if(activityType === "Parameters detected" ){

            navigate(`/dashboard/observe/changes`,{state:{tab:1, timestamp: timestamp}});


        }
        else if(activityType === "High Vulnerability detected"){
          
        let updatedFiltersMap = { ...filtersMap }; 

            const filterKey = '/dashboard/issues'

            const filterArray = [{
              key: "severity",
              label: "High",
              value: ["HIGH"],
            },
            {
              key: "startTimestamp",
              label: "Custom",
              value: [timestamp-10000],
            }]

            updatedFiltersMap[filterKey] = {filters: filterArray, sort : []};


        setFiltersMap(updatedFiltersMap)
          navigate('/dashboard/issues')


        }
      }
      

    return (
        <Card>
            <VerticalStack gap={5}>
                <Text variant="bodyLg" fontWeight="semibold">Latest activity</Text>
                <Scrollable style={{maxHeight: '400px' }}  shadow> 
                    <VerticalStack gap={3}>
                        <HorizontalStack gap={3}>
                            <VerticalStack>
                            {latestActivity.map((c,index)=>{
                                return (

                                    <div style={{display: 'flex', gap: '12px'}} key={index}>
                                   
                                        <Box>
                                            
                                            <Avatar shape="round" size="extraSmall" source="/public/steps_icon.svg"/>
                                            {index < (latestActivity.length - 1) ? <div style={{background: '#e6e6ff', width: '2px', margin: 'auto', height: '36px'}} /> : null}
                                        </Box>

                                        <Box>
                                        <Button  textAlign="start"  plain monochrome removeUnderline onClick={() => handleActivityType(c.type, c.description, c.timestamp)}>
                                        <Text variant="bodySm" color="subdued">{`${func.prettifyEpoch(c.timestamp)}`}</Text>
                                        <div className="customButton" > 
                                            <Text variant="bodyMd" color="semibold" >{c.description}</Text>
                                        </div>      
                                         

                                        </Button>

                                            
                                           
                                        </Box>                             
                                    </div>
                                )
                            })}
                            </VerticalStack>
                        </HorizontalStack>
                    </VerticalStack>
                </Scrollable>
                {showLoadMore() ? <Button plain removeUnderline onClick={onLoadMore}>Load more</Button> : null}
            </VerticalStack>
        </Card>
    )
}
                          
export default ActivityTracker