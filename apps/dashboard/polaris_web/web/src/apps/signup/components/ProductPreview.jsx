import { useState } from "react"

import {Box, Text, Icon, HorizontalStack, VerticalStack} from "@shopify/polaris"
import {ChevronLeftMinor, ChevronRightMinor} from "@shopify/polaris-icons"

import ProductPreviewImages from "../util/productPreviewImages.js"


const ProductPreview = () => {
    const [selectedIndex, setSelectedIndex ] = useState(0)
  
    const handleLeftProductPreviewNavigation = () => {
      if (selectedIndex > 0) {
          setSelectedIndex(selectedIndex - 1)
      }
    }
  
    const handleRightProductPreviewNavigation = () => {
      if (selectedIndex < ProductPreviewImages.length - 1) {
          setSelectedIndex(selectedIndex + 1)
      }
    }
  
    return (
      <Box background="bg-interactive-hover">
        <div style={{width: "40vw", margin: "25vh auto"}}>
          <VerticalStack gap="3" align="center">
            <img src={ProductPreviewImages[selectedIndex]}/>
            
            <HorizontalStack gap="3" align="center">
              <div onClick={handleLeftProductPreviewNavigation}>
                <Icon
                  source={ChevronLeftMinor}
                  color={selectedIndex === 0 ? "base" : "subdued"}
                /> 
              </div>  
              <Text as="h1" color="text-inverse">{selectedIndex + 1} / {ProductPreviewImages.length}</Text>
              <div onClick={handleRightProductPreviewNavigation}>
                <Icon
                  source={ChevronRightMinor}
                  color={selectedIndex === ProductPreviewImages.length - 1 ? "base" : "subdued"}
                />      
              </div>
            </HorizontalStack>
          </VerticalStack>
        </div>
      </Box>
    )
}

export default ProductPreview