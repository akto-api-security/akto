import { Button, Text, Tooltip, VerticalStack } from '@shopify/polaris'
import React from 'react'
import SampleData from '../../../../components/shared/SampleData'
import { ClipboardMinor } from "@shopify/polaris-icons"

function JsonComponent({dataString, onClickFunc, title, toolTipContent, language, minHeight, readOnly = false, getEditorData}) {

  let data = {message:dataString}

  return (
    <VerticalStack gap="1">  
        <div className='copyRequest'>
            <Text>{title}</Text>
            <Tooltip dismissOnMouseOut preferredPosition='above' content={toolTipContent}>
                <Button icon={ClipboardMinor} plain  onClick={() => onClickFunc()}/>
            </Tooltip>
        </div>
        <SampleData data={data} language={language} minHeight={minHeight} readOnly={readOnly} getEditorData={getEditorData} />
    </VerticalStack>
  )
}

export default JsonComponent