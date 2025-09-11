import { Text, VerticalStack } from '@shopify/polaris'
import { useRef } from 'react'
import JsonComponent from './shared/JsonComponent'
import func from "@/util/func"

function McpProxy() {
    const ref = useRef(null)
    const copyCommandUtil = (data)=>{func.copyToClipboard(data, ref, null)}

    const mcpProxyExample = `MCP Proxy URL Pattern:
https://mcp-proxy.akto.io/proxy/{protocol}/{host}/{path}

Example Transformation:
Original: https://mcp.example.com/api/endpoint
Proxied:  https://mcp-proxy.akto.io/proxy/https/mcp.example.com/api/endpoint

Steps:
1. Remove "://" from your MCP server URL
2. Prefix with https://mcp-proxy.akto.io/proxy/
3. Your MCP requests will now route through Akto's proxy`

    const mcpProxyComponent = (
        <VerticalStack gap="2">
          <div ref = {ref}/>

          <span>Configure your MCP requests to route through Akto's MCP proxy using the pattern below:</span>

          <VerticalStack gap="1">
            <JsonComponent 
              title="MCP Proxy Configuration" 
              toolTipContent="Copy configuration" 
              onClickFunc={()=> copyCommandUtil(mcpProxyExample)} 
              dataString={mcpProxyExample} 
              language="text" 
              minHeight="400px" 
            />
          </VerticalStack>

        </VerticalStack>
      )

    return (
        <div className='card-items'>
            <Text variant='bodyMd'>
                Safeguard your MCP server requests through Akto's proxy with threat protection and guardrails.
            </Text>
            {mcpProxyComponent}

        </div>
    )
}

export default McpProxy