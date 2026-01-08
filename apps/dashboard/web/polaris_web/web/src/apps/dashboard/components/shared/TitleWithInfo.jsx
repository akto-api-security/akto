import { Avatar, Box, HorizontalStack, Link, Text, Tooltip } from '@shopify/polaris'
import TooltipWithLink from './TooltipWithLink'

function TitleWithInfo(props) {

    const { titleComp, textProps, titleText, tooltipContent, docsUrl } = props

    const content = docsUrl ?
        <Box as="span">
            {tooltipContent} {" "}
            <Link url={docsUrl} target="_blank">Learn more</Link>
        </Box>
         : tooltipContent

    return (
        <HorizontalStack gap={"1"}>
            {titleComp ? titleComp : <Text variant="headingLg" {...textProps}>{titleText}</Text>}
            {docsUrl ?
                <TooltipWithLink content={content} preferredPosition="top">
                    <div className='reduce-size'>
                        <Avatar shape="round" size="extraSmall" source='/public/info_filled_icon.svg'/>
                    </div>
                </TooltipWithLink>
                : tooltipContent ?
                <Tooltip content={tooltipContent} dismissOnMouseOut>
                    <div className='reduce-size'>
                        <Avatar shape="round" size="extraSmall" source='/public/info_filled_icon.svg'/>
                    </div>
                </Tooltip> : null
            }
        </HorizontalStack>
    )
}

export default TitleWithInfo