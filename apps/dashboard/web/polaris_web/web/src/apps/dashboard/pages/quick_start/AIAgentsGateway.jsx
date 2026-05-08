
import { Text, VerticalStack } from '@shopify/polaris'
import { useRef } from 'react'
import func from "@/util/func"
import JsonComponent from './components/shared/JsonComponent'


function AIAgentsGateway(){
    const ref = useRef(null)
    const copyCommandUtil = (data)=>{func.copyToClipboard(data, ref, null)}

    const mcpProxyExample = `AI Agent Proxy URL Pattern:
https://ai-agent-proxy.akto.io/proxy/{protocol}/{host}/{path}

Example Transformation:
Original: https://my-agent.example.com/api/endpoint
Proxied:  https://ai-agent-proxy.akto.io/proxy/https/my-agent.example.com/api/endpoint

Steps:
1. Remove "://" from your AI Agent URL
2. Prefix with https://ai-agent-proxy.akto.io/proxy/
3. Your AI Agent requests will now route through Akto's proxy`

    const mcpProxyComponent = (
        <VerticalStack gap="2">
          <div ref = {ref}/>

          <span>Configure your AI Agent requests to route through Akto's AI Agent proxy using the pattern below:</span>

          <VerticalStack gap="1">
            <JsonComponent 
              title="AI Agent Proxy Configuration" 
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

export default AIAgentsGateway;