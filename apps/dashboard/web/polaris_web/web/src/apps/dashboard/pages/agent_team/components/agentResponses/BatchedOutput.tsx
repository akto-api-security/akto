import { Box, HorizontalStack, Icon, Text, Tooltip, VerticalStack } from '@shopify/polaris'
import React, { useMemo } from 'react'
import { motion, AnimatePresence } from "framer-motion";
import { CaretDownMinor, ClockMinor, CircleAlertMajor } from "@shopify/polaris-icons";
import { intermediateStore } from '../../intermediate.store';
import {circle_tick_minor } from  "../../../../../dashboard/components/icons";

function BatchedOutput({data, buttonText, isCollectionBased, keysArr}: {data: any, buttonText: string, isCollectionBased: boolean, keysArr: string[]}) {
    const [expanded, setExpanded] = React.useState(true);
    const codeAnalysisCollections = intermediateStore(state => state.sourceCodeCollections);
    const collectionsMap = codeAnalysisCollections.reduce((acc, item) => {
        acc[item?.apiCollectionId] = item.name;
        return acc;
      }, {});
    
    const getIconObj = (val:any, type: string) => {
        if(val === null || val === undefined) {
            return {
                icon: ClockMinor,
                tooltipContent: `Currently scanning for: ${type}`
            }
        }else if(val){
            return{
                icon: CircleAlertMajor,
                tooltipContent: `API is vulnerable for :${type}`
            }
        }else{
            return {
                tooltipContent: `API is not vulnerable for ${type}`,
                icon: circle_tick_minor
              }
        }
    }

    return useMemo(() => {
        return (
            <VerticalStack gap={"4"}>
                <HorizontalStack gap={"2"} wrap={false}>
                    <HorizontalStack gap="1" wrap={false}>
                        <Icon source={ClockMinor} color="subdued"/>
                        <Text as={"dt"}>Pending</Text>
                    </HorizontalStack>
                    <HorizontalStack gap="1" wrap={false}>
                        <Icon source={CircleAlertMajor} color="critical"/>
                        <Text as={"dt"}>Vulnerable</Text>
                    </HorizontalStack>
                    <HorizontalStack gap="1" wrap={false}>
                        <Icon source={circle_tick_minor} color="base"/>
                        <Text as={"dt"}>Not Vulnerable</Text>
                    </HorizontalStack>
                </HorizontalStack>
                {Object.keys(data).map((collectionID, index) => {
                    return (
                        <VerticalStack key={index} gap={"2"} >
                            <button className="w-full flex items-center cursor-pointer" onClick={() => setExpanded(!expanded)}>
                                <motion.div animate={{ rotate: expanded ? 0 : 270 }} transition={{ duration: 0.2 }}>
                                    <CaretDownMinor height={20} width={20} />
                                </motion.div>
                                <Text as={"dd"}>{`${buttonText}`}</Text>
                                <Text as={"dd"}>{" "}</Text>
                                <Text as={"dd"} variant="headingMd">{collectionsMap[collectionID]}</Text>
                            </button>
                            <AnimatePresence>
                                <motion.div
                                    animate={expanded ? "open" : "closed"}
                                    variants={{ open: { height: "auto", opacity: 1 }, closed: { height: 0, opacity: 0 } }}
                                    transition={{ duration: 0.2 }}
                                    className="overflow-hidden"
                                >
                                    <VerticalStack gap={"4"}>
                                        <AnimatePresence initial={false}>
                                            {data[collectionID].map((item: any) => {
                                                return (
                                                    <motion.div
                                                        key={item?.url + item?.method}
                                                        initial={{ opacity: 0 }}
                                                        animate={{ opacity: 1 }}
                                                        exit={{ opacity: 0 }}
                                                        transition={{ duration: 0.6 }}
                                                        className='flex gap-2 w-full wrap:nowrap'
                                                    >
                                                        <motion.p initial={{ opacity: 0, y: -10 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.2 }} className="text-xs text-[var(--text-subdued)] ml-3! p-0.5 hover:bg-[var(--background-selected)]">
                                                            {item?.method} - {item?.url}
                                                        </motion.p>
                                                        <HorizontalStack gap={"2"}>
                                                            {keysArr.map((key) => (
                                                            <div key={key} className="flex text-xs wrap:nowrap">
                                                                {key}
                                                                <motion.span
                                                                    initial={{ scale: 0, opacity: 0 }}
                                                                    animate={{ scale: 1, opacity: 1 }}
                                                                    transition={{ duration: 0.3, delay: index * 0.1 }}
                                                                    className="ml-2"
                                                                >
                                                                    <Tooltip content={getIconObj(item.output[key], key).tooltipContent} hoverDelay={300} dismissOnMouseOut>
                                                                        <Box><Icon source={getIconObj(item.output[key], key).icon} color={
                                                                            item?.output[key] === undefined ? "subdued" : item?.output[key] === true ? "critical" : "base"
                                                                        }/></Box>
                                                                    </Tooltip>
                                                                </motion.span>
                                                            </div>
                                                            ))}
                                                        </HorizontalStack>
                                                    </motion.div>
                                                )   
                                            })}      
                                        </AnimatePresence>
                                    </VerticalStack>
                                </motion.div>
                            </AnimatePresence>
                        </VerticalStack>
                    )
                })}
            </VerticalStack>
        );
    }, [data, expanded]);
    
}

export default BatchedOutput